---
title: refactor: Replace sub-resource timestamp scans with a dirty-work queue
type: refactor
status: active
date: 2026-03-17
---

# Refactor: Replace sub-resource timestamp scans with a dirty-work queue

## Overview

The current child-resource background flow is safe, but it scales by scanning for changed rows instead of draining an explicit set of changed resources. This plan replaces the timestamp-scan scheduler for the steady-state Kafka path with a Postgres-backed dirty-work queue so child-resource processing remains eventual but becomes proportional to actual change volume.

## Problem Statement

Today the non-full-reindex path works like this:

1. Kafka instance/item/holdings events are normalized to instance-oriented work and sent into the indexing path.
2. `PopulateInstanceBatchInterceptor` persists parent facts into Postgres and explicitly defers child-resource work to a later background job.
3. `ScheduledInstanceSubResourcesService` loops tenants and entity types, locks a timestamp watermark, scans `instance`, `item`, and child tables by `last_updated_date`, and advances the watermark after processing.

This design creates the following problems:

- Work grows with table size, not with actual change count.
- Retries re-scan windows that may be mostly cold.
- The scheduler must touch multiple large tables per tenant even when only a small number of parents changed.
- Parent expansion and child indexing are both scan-driven, which increases steady-state Postgres pressure on large tenants.

## Goals

- Remove repeated `last_updated_date` scans from the steady-state Kafka child-resource path.
- Preserve eventual consistency semantics for derived child indices.
- Keep the solution inside `mod-search` and backed by Postgres only.
- Preserve current delete, ownership-move, resource-sharing, and tenant-specific behavior.
- Make retries idempotent and proportional to the failed work item instead of a scan window.

## Non-Goals

- Redesign the full reindex flow in this change.
- Change the OpenSearch document shape for child indices.
- Introduce new external infrastructure beyond Postgres.
- Remove the legacy scan-based scheduler in the first rollout step.

## Current State

### Relevant flow

- Kafka instance-related events are handled in `KafkaMessageListener.handleInstanceEvents(...)`.
- `PopulateInstanceBatchInterceptor` writes `instance`, `item`, and `holdings` facts into Postgres and logs that sub-resource processing will be handled by the background job.
- `ScheduledInstanceSubResourcesService`:
  - scans changed `instance` and `item` rows
  - calls `InstanceChildrenResourceService.persistChildren(...)` to maintain child entity and relation tables
  - scans changed child entity tables
  - indexes child-resource documents into OpenSearch

### Key limitation

The current design uses global per-tenant watermarks and table scans as the unit of work. That is the right failure model for correctness, but the wrong cost model for large steady-state tenants.

## Proposed Solution

Introduce an explicit Postgres-backed work queue for the steady-state child-resource path and replace scan-driven scheduling with queue-driven scheduling.

### Queue model

Add a new table, `sub_resource_work`, with one live row per pending resource/stage key.

Suggested columns:

- `id uuid primary key`
- `tenant_id varchar(100) not null`
- `stage varchar(32) not null`
- `resource_type varchar(32) not null`
- `resource_id varchar(64) not null`
- `status varchar(16) not null`
- `attempt_count integer not null default 0`
- `available_at timestamp not null default current_timestamp`
- `locked_at timestamp null`
- `last_error text null`
- `created_at timestamp not null default current_timestamp`
- `updated_at timestamp not null default current_timestamp`

Suggested uniqueness:

- unique index on `(tenant_id, stage, resource_type, resource_id)`

Queue semantics:

- A work item exists only while it is pending or retrying.
- On success, delete the row.
- On duplicate enqueue, use `ON CONFLICT DO NOTHING` or a lightweight `updated_at` refresh.

### Two-stage work model

Use two explicit work stages to match the current two-step child pipeline.

#### Stage 1: `parent_expand`

Keyed by:

- `(tenant_id, instance, instance_id)`
- `(tenant_id, item, item_id)`

Purpose:

- Rebuild child entity and relation tables from changed parent records.

#### Stage 2: `child_index`

Keyed by:

- `(tenant_id, subject, subject_id)`
- `(tenant_id, contributor, contributor_id)`
- `(tenant_id, classification, classification_id)`
- `(tenant_id, call_number, call_number_id)`

Purpose:

- Index or delete the final child-resource document in OpenSearch.

This preserves the current architecture but swaps scans for explicit work keys.

## Technical Approach

### Enqueue points

After successful parent persistence in `PopulateInstanceBatchInterceptor`, enqueue `parent_expand` rows for the touched parent ids.

Rules:

- Enqueue `instance` work after instance create/update/delete and bound-with-affecting instance changes.
- Enqueue `item` work after item create/update/delete.
- For ownership moves, enqueue both tenant-scoped work items.
- Holdings events remain out of scope unless they are shown to mutate child-resource projections directly.

### Parent worker

Add an in-process scheduled worker that polls `parent_expand` items with `FOR UPDATE SKIP LOCKED`.

For each locked batch:

1. Group by tenant and resource type.
2. Fetch current parent rows by exact ids from `instance` or `item` tables.
3. Build `ResourceEvent` instances representing update or delete.
4. Call `InstanceChildrenResourceService.persistChildren(...)`.
5. Collect the affected child ids by child type.
6. Enqueue `child_index` work for those ids.
7. Delete the processed `parent_expand` rows on success.

Failure behavior:

- Increment `attempt_count`
- set `last_error`
- release the row by clearing the lock and moving `available_at` forward with backoff

### Child worker

Add an in-process scheduled worker that polls `child_index` rows with the same lock strategy.

For each locked batch:

1. Group by tenant and child resource type.
2. Fetch child projection rows by exact ids from the corresponding child repository.
3. Convert each row to an index event if it still has surviving `instances`.
4. Emit a delete event if the child row no longer exists or no longer has instances.
5. Bulk index into OpenSearch through `ResourceService.indexResources(...)`.
6. Delete the processed `child_index` rows on success.

### Repository changes

The queue design requires exact-id access and affected-id reporting rather than timestamp scans.

Expected repository additions:

- exact-id fetch methods for `instance` and `item`
- exact-id fetch methods for child repositories
- save/delete methods on child repositories that return the touched child ids

The current child extractor path returns `void`. Change that contract so parent processing can surface the precise child ids that must be re-indexed.

### Transaction boundaries

The queue must avoid lost work.

Required coupling:

- parent fact persistence and `parent_expand` enqueue should happen in the same transaction
- child table mutation and `child_index` enqueue should happen in the same transaction
- queue ack should happen only after the downstream step succeeds

This removes the risk of advancing a watermark while losing the actual unit of work.

## Implementation Phases

### Phase 1: Foundation

- Create `sub_resource_work` schema and repository.
- Add a feature flag:
  - `folio.search-config.indexing.sub-resource-queue-enabled`
- Add queue metrics:
  - pending count by stage
  - retry count
  - failure count
  - max queue age
- Add exact-id fetch methods on parent repositories.

Success criteria:

- Queue rows can be inserted, locked, retried, and deleted safely.
- The feature can be enabled or disabled per deployment.

### Phase 2: Parent expansion worker

- Enqueue `parent_expand` work from `PopulateInstanceBatchInterceptor`.
- Implement the queue worker for `instance` and `item`.
- Change `InstanceChildrenResourceService` and extractor/repository APIs to return affected child ids.
- Enqueue `child_index` work from the parent worker.

Success criteria:

- Parent work is no longer dependent on timestamp scans.
- Duplicate Kafka updates collapse to a single live queue row per key.
- Delete and ownership-move behavior remains correct.

### Phase 3: Child indexing worker

- Implement the `child_index` worker.
- Add exact-id child fetch methods.
- Convert missing or empty child projections into OpenSearch delete events.
- Run queue-driven indexing in parallel with the legacy scheduler in a verification environment.

Success criteria:

- Queue-driven child indexing produces the same OpenSearch end state as the legacy scheduler.
- Retries are item-scoped rather than scan-window-scoped.

### Phase 4: Cutover and cleanup

- Enable the queue worker in production behind the feature flag.
- Disable timestamp-scan processing for the steady-state path.
- Keep legacy scan code as fallback for one release.
- Remove `SubResourcesLockRepository` and the old watermark path after confidence is established.

Success criteria:

- No steady-state child indexing relies on `last_updated_date` scans.
- Queue lag stays within the accepted eventual-consistency window.

## Alternative Approaches Considered

### Keep the current scheduler and tune indexes only

Rejected because it reduces pain but does not change the cost model. Large tenants still pay scan cost on every cycle.

### Queue only parent work and keep child-table timestamp scans

Rejected because it leaves half of the scan cost in place. The second scan stage becomes the next bottleneck.

### Move steady-state child updates to an external stream processor

Rejected for this plan because the goal is to stay inside `mod-search` with Postgres only.

## System-Wide Impact

### Interaction graph

Kafka event triggers `KafkaMessageListener.handleInstanceEvents(...)`, which invokes the instance indexing path. In parallel, `PopulateInstanceBatchInterceptor` persists parent facts and enqueues `parent_expand`. The parent worker then calls `InstanceChildrenResourceService.persistChildren(...)`, which calls the child extractors, which call child repositories to update entity and relation tables. That update enqueues `child_index`, which the child worker drains and sends through `ResourceService.indexResources(...)` into OpenSearch.

### Error and failure propagation

- Queue item processing errors become localized to a single resource key.
- The queue row retains failure state and retry metadata instead of relying on a broad watermark retry.
- OpenSearch failures do not lose work because the `child_index` row is only deleted after successful indexing.

### State lifecycle risks

- Parent persistence without enqueue would lose work.
- Child table updates without `child_index` enqueue would leave OpenSearch stale.
- Duplicate queue rows would create wasted work.

Mitigations:

- transactional enqueue with the write step
- unique queue key per stage/resource
- delayed retry with item-level backoff

### API surface parity

The queue-backed design must be used consistently anywhere parent facts are mutated for steady-state processing.

Interfaces to review:

- Kafka batch interceptor path
- any direct parent-fact mutation helper
- any future admin or repair endpoint for child-resource backfill

### Integration test scenarios

- Item ownership transfer across tenants enqueues and processes both tenant-specific work items correctly.
- Instance delete removes child relations and deletes empty child docs from OpenSearch.
- Repeated Kafka updates for the same item collapse to a single queue row and still converge correctly.
- Parent worker failure after child-table mutation but before queue ack retries without corrupting state.
- OpenSearch indexing failure leaves `child_index` work retryable and does not drop the update.

## Acceptance Criteria

### Functional requirements

- [ ] Steady-state child-resource processing is driven by explicit queue rows instead of `last_updated_date` scans.
- [ ] Instance and item changes enqueue parent work transactionally with parent fact persistence.
- [ ] Parent work enqueues exact child ids for the second-stage indexing step.
- [ ] Child index work supports create, update, and delete outcomes.
- [ ] Ownership-move and resource-sharing behavior remains correct.

### Non-functional requirements

- [ ] Queue processing stays within the existing eventual-consistency expectation.
- [ ] Duplicate updates do not create unbounded duplicate work.
- [ ] Retry behavior is item-scoped and observable.
- [ ] The new path reduces steady-state Postgres scan pressure versus the legacy scheduler.

### Quality gates

- [ ] Unit tests cover queue repository behavior, worker locking, retry, and dedupe.
- [ ] Integration tests cover end-to-end Kafka-to-child-index behavior.
- [ ] Feature-flag rollout path is documented and reversible.

## Success Metrics

- Reduction in steady-state queries that scan `instance`, `item`, `subject`, `contributor`, `classification`, and `call_number` by timestamp.
- Lower average and p95 Postgres load during normal Kafka-driven indexing.
- Queue lag remains inside the accepted eventual-consistency window.
- Child-resource indexing retries are attributable to specific resource keys rather than broad scan windows.

## Dependencies and Risks

### Dependencies

- Liquibase migration for the queue table and indexes
- repository APIs for exact-id fetches
- extractor/repository contract changes to surface affected child ids

### Risks

- queue amplification for very high-fanout parents
- incorrect affected-id reporting could leave child docs stale
- poor retry policy could create hot-loop failures
- dual-running queue and legacy scheduler could produce duplicate work if not isolated carefully

### Mitigations

- cap batch size and add per-stage backoff
- instrument queue size and max age
- verify queue-driven output against the legacy scheduler before cutover
- keep the legacy scheduler behind a fallback flag for one release

## Documentation Plan

- Document the new queue flag and worker settings in `README.md`.
- Document queue metrics and failure recovery procedures in operational notes.
- Add developer notes explaining why the legacy timestamp-scan path remains temporarily available during rollout.

## Sources and References

### Internal references

- `src/main/java/org/folio/search/integration/message/KafkaMessageListener.java`
- `src/main/java/org/folio/search/integration/message/interceptor/PopulateInstanceBatchInterceptor.java`
- `src/main/java/org/folio/search/service/ScheduledInstanceSubResourcesService.java`
- `src/main/java/org/folio/search/service/InstanceChildrenResourceService.java`
- `src/main/java/org/folio/search/service/converter/preprocessor/extractor/ChildResourceExtractor.java`
- `src/main/java/org/folio/search/service/reindex/jdbc/SubResourcesLockRepository.java`

### Notes

- This plan intentionally targets the steady-state Kafka path only.
- Full reindex architecture should be planned separately.
