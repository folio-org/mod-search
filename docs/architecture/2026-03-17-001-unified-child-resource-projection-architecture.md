---
title: architecture: Unified scheduled child-resource projection pipeline
type: architecture
status: proposed
date: 2026-03-17
---

# Architecture: Unified scheduled child-resource projection pipeline

## Overview

This document describes a child-resource processing architecture for `mod-search` where OpenSearch is the durable system of record for projection input and output, Postgres is not used for child-resource processing, and a single scheduled pipeline converges child browse indices from both realtime and full-rebuild inputs.

The design treats child resources as derived projections:

- `instance` indices are the canonical source of truth for child-resource computation.
- `child_edge` indices store durable intermediate state for correct incremental add, update, and delete behavior.
- child browse indices store the final aggregated search and browse documents.
- a scheduled background reconciler is the only component that advances child-resource state.

Realtime Kafka updates and full rebuilds use the same projection model, the same intermediate edge schema, and the same final browse schema. Full rebuild uses DuckDB as an in-process analytical engine for full scans; realtime processing stays incremental and scheduler-driven.

## Goals

- Remove Postgres from child-resource processing.
- Use the same projection model for realtime updates and full rebuilds.
- Keep child-resource processing inside the `mod-search` service process.
- Support running full rebuild and realtime updates at the same time.
- Allow restoring OpenSearch to a point in time and rebuilding child resources from restored `instance` data.
- Keep child resources eventually consistent with a one-minute scheduled convergence loop.

## Non-Goals

- Make child browse indices transactional or immediately consistent with Kafka events.
- Require exact restoration of derived child-resource state after disaster recovery.
- Force realtime and full rebuild through the exact same execution engine.
- Replace Kafka as the input source for normal realtime updates.

## Core Principles

### OpenSearch is the durable store

This design does not use Postgres for child-resource processing, dirty tracking, or child-resource reconciliation. The durable projection state lives in OpenSearch.

### The scheduler is authoritative

Kafka consumers do not update child browse indices directly. They record work for the scheduled projection pipeline. The scheduled reconciler is the only component that mutates `child_edge` and final browse indices.

### Derived state is disposable

`child_edge` and browse indices can be rebuilt from `instance` indices. After an OpenSearch restore, it is acceptable to rebuild child resources from restored `instance` data instead of restoring exact derived state.

### Same projection model, different batch producers

Realtime and full rebuild share:

- the same child-edge schema
- the same browse-document schema
- the same reduction rules
- the same generation and cutover model

They differ only in how parent work is produced:

- realtime produces incremental dirty-parent work from Kafka events
- full rebuild produces full-scan work from OpenSearch `instance` data using DuckDB

## Logical Data Model

### 1. Canonical source indices

These indices hold the source `instance` representation used for child-resource derivation.

Examples:

- `instance_current_*`
- `instance_next_*`

### 2. Intermediate child-edge indices

These indices hold one durable document per parent-child relationship. They are the basis for correct incremental recomputation.

Examples:

- `child_edge_current_*`
- `child_edge_next_*`

Properties:

- one document per deterministic edge key
- enough fields to rebuild final browse documents without rereading the original event payload
- rebuildable from `instance` source data

### 3. Final child browse indices

These indices hold the aggregated subject, contributor, classification, and call-number browse/search documents.

Examples:

- `browse_subject_current_*`
- `browse_contributor_current_*`
- `browse_classification_current_*`
- `browse_call_number_current_*`
- corresponding `*_next_*` generations during rebuild

### 4. Dirty-work indices

These indices hold durable scheduler work items in OpenSearch.

Examples:

- `dirty_parent_current_*`
- `dirty_child_current_*`
- optional `dirty_parent_next_*` and `dirty_child_next_*` during rebuild

Dirty-work documents are deduplicated by deterministic ids so repeated events collapse to one live work item.

### 5. Leader lease index

This index stores the single scheduler leader lease.

Example:

- `child_projection_leader`

Only the instance holding the active lease is allowed to run the scheduled child-resource reconciler.

## Runtime Roles

### All `mod-search` instances

All service instances:

- consume Kafka events according to normal consumer-group behavior
- update `instance` source indices
- mark affected parents as dirty in the appropriate dirty-work index
- participate in leader election for the scheduled reconciler

### Single scheduler leader

All service instances may have the scheduled job enabled, but only the instance holding the leader lease performs scheduled child-resource processing. If the leader dies or stops renewing the lease, another instance takes over.

This keeps ingestion horizontally scalable while keeping child-resource mutation simple and safe.

## Realtime Processing Flow

Realtime processing is incremental and eventually consistent.

### Step 1. Kafka updates canonical source state

Kafka instance-related events continue to update the canonical `instance` source representation in OpenSearch.

### Step 2. Kafka marks parent work dirty

After the canonical source state is updated, the Kafka consumer upserts a dirty-parent document keyed by:

- tenant
- parent type
- parent id
- generation

This write is idempotent.

### Step 3. Scheduler leader claims dirty parents

On each schedule, the leader:

- searches for pending dirty-parent documents
- claims a batch using application-managed leasing semantics in OpenSearch
- groups the claimed work by tenant and resource type

### Step 4. Parent expansion rebuilds child edges

For each claimed parent:

- read the current parent source document from `instance_current_*`
- derive the complete child-edge set for that parent
- compare the new edge set to the existing `child_edge_current_*` set for that parent
- upsert missing or changed edge documents
- delete obsolete edge documents

This step computes exact parent-level replacement, not incremental patching.

### Step 5. Mark affected child keys dirty

Any child keys touched by the edge diff are written to `dirty_child_current_*` using deterministic ids.

### Step 6. Scheduler leader reduces child keys into browse docs

For each claimed dirty child key:

- read the current edge set for that child key from `child_edge_current_*`
- rebuild the full browse document for that child key
- upsert the browse document if edges remain
- delete the browse document if the edge set is empty

The browse document is rebuilt from edge state, not patched from the event payload.

## Full Rebuild Flow

Full rebuild creates a separate child-resource generation while realtime processing continues against the current generation.

### Step 1. Create next-generation indices

Provision:

- `child_edge_next_*`
- `browse_*_next_*`
- `dirty_parent_next_*`
- `dirty_child_next_*`

The rebuild reads from a concrete `instance` generation, not from a moving alias.

### Step 2. Stage rebuild input

`mod-search` scans the concrete `instance` source generation and writes narrow staging files to MinIO. These files are rebuild artifacts, not the system of record.

Recommended format:

- Parquet partitions by tenant and hash range

### Step 3. Use DuckDB for full-scan parent expansion

DuckDB runs in-process inside `mod-search` and reads the staged files from MinIO to:

- explode source `instance` data into child-edge rows
- write the resulting next-generation edge set into `child_edge_next_*`

DuckDB is used here because this is a full-scan analytical workload.

### Step 4. Build next-generation browse docs

Once `child_edge_next_*` exists, the scheduled reconciler reduces those edges into `browse_*_next_*` using the same browse-reduction rules used by the realtime path.

### Step 5. Realtime continues against current generation

During rebuild:

- realtime events continue to update `child_edge_current_*` and `browse_current_*`
- the same events also mark dirty-parent work for the next generation

This allows the next generation to catch up without interrupting the current live generation.

### Step 6. Catch up next generation

After the full scan completes:

- the leader drains `dirty_parent_next_*`
- the leader drains `dirty_child_next_*`
- the system waits until the next generation has no remaining dirty work

### Step 7. Cut over

When the next generation is fully caught up:

- swap child-resource aliases from current to next
- retire the old current generation after the normal safety window

## Restore and Recovery Model

This architecture assumes that restoring source `instance` data is enough to rebuild child resources.

After an OpenSearch restore:

1. restore the desired `instance` source indices
2. recreate empty `child_edge` and browse generations
3. run a full rebuild from restored `instance` data
4. cut over when rebuild and catch-up complete

This means exact restoration of derived child-resource state is not required for correctness.

## Multi-Instance Coordination

### Leader model

All `mod-search` instances can run with scheduling enabled, but only one instance acts as the scheduler leader at a time.

The leader lease is application-managed and stored in OpenSearch. Lease acquisition and renewal use OpenSearch document concurrency controls and lease expiry.

### Why a single leader

The initial architecture uses a single scheduler leader because:

- it is simpler than distributed work claiming
- it avoids concurrent mutation of the same child-resource state
- it is sufficient for a one-minute eventual-consistency target

If throughput later requires more scheduler capacity, the design can evolve to distributed claims without changing the logical data model.

## Consistency Model

This design is eventually consistent.

Expected behavior:

- Kafka events update source `instance` state first
- child-resource state converges on the next scheduler cycle
- duplicate events are harmless because dirty work and edge writes are idempotent
- full rebuild and realtime may overlap safely because they target separate generations

The authoritative child-resource state is the result of the scheduled reconciler, not the Kafka consumer path.

## Refresh and Visibility

OpenSearch index refresh affects when newly written documents become visible to search-based scheduler steps. In this architecture, refresh is a visibility boundary, not the correctness model.

Key implications:

- newly written `instance` source documents are not immediately visible to scheduler searches
- newly written dirty-work documents are not immediately visible to scheduler searches
- newly written `child_edge` documents are not immediately visible to browse reducers that search edge state

This is acceptable for the realtime path because the scheduler runs on a one-minute cadence and the design is explicitly eventual.

### Realtime refresh strategy

For the current generation:

- rely on normal index refresh behavior
- do not force refresh on every Kafka event
- do not force refresh on every dirty-work enqueue

This keeps the hot path cheap and lets refresh latency stay well below the one-minute convergence target.

### Rebuild refresh strategy

For next-generation rebuild indices:

- relax or disable refresh during large bulk loads into `child_edge_next_*`
- run explicit refresh at rebuild phase boundaries
- restore normal refresh settings before cutover

The important refresh points are:

1. after bulk loading `child_edge_next_*`, before reducing those edges into `browse_*_next_*`
2. after building `browse_*_next_*`, before validation or alias swap
3. after replaying final dirty work into the next generation, before cutover

### Scheduler implementation note

If a scheduler pass writes edge state and then immediately reduces affected child keys, it should avoid depending on an immediate refresh when possible. The preferred options are:

- carry fresh edge data in memory within the same scheduler pass, or
- explicitly refresh the edge index before search-based reduction

The design should not assume read-after-write visibility from OpenSearch searches without either refresh or in-memory handoff.

## Why DuckDB Is Limited to Full Rebuild

DuckDB is a strong fit for full scans, large aggregations, and grouped analytical work. It is not required for minute-by-minute incremental reconciliation.

The design therefore uses DuckDB only where it adds clear value:

- full rebuild from a complete `instance` scan
- rebuild after OpenSearch restore

Realtime continues to use the same projection model and output schema, but stays incremental and scheduler-driven.

This keeps the hot path simpler while still giving full rebuild a better execution engine.

## Operational Characteristics

### Advantages

- no Postgres dependency for child-resource processing
- unified projection model across realtime and full rebuild
- clean restore story based on source `instance` data
- safe overlap between live updates and rebuild
- simpler multi-instance behavior through a single scheduler leader

### Costs

- more derived-state indices in OpenSearch
- more application-owned coordination logic
- rebuild requires staging artifacts in MinIO and a DuckDB execution path
- eventual consistency remains a deliberate property of the design

## Architecture Summary

This architecture uses OpenSearch as both the canonical source for child-resource derivation and the durable store for intermediate and final child-resource state. A single scheduled leader inside `mod-search` is responsible for reconciling child-resource projections every minute. Realtime updates and full rebuilds share the same projection model and generation strategy, while DuckDB is used only for full-scan rebuilds where analytical execution is needed.
