# Reindex job id filtering (MSEARCH-1284 PoC)

When two mod-search instances run side by side (e.g. live **v1** and background **v2**), both consume the shared
`inventory.reindex-records` topic, so each instance can receive reindex events of the other instance's job. Job id
filtering makes each instance process only the events of the reindex job it started itself.

## How it works

1. `POST /search/index/instance-records/reindex/full` registers a new `reindexJobId` (UUID) in the tenant's
   `reindex_job` table, replacing the previous one. From that moment events of any earlier job are ignored.
2. Every `/inventory-reindex-records/publish` request sends it in the `X-Okapi-Reindex-Job-Id` header.
3. mod-inventory-storage (patched, branch `MSEARCH-1283-poc` on `v29.0.23`) copies the header onto the
   `inventory.reindex-records` Kafka events it publishes for that request.
4. `ReindexKafkaListener` resolves the job id of each event and asks `ReindexJobEventFilter` before processing:

| Event                        | Job id source                                          |
|------------------------------|--------------------------------------------------------|
| `inventory.reindex-records`  | Kafka header `X-Okapi-Reindex-Job-Id`                  |
| `search.reindex.range-index` | payload `reindexJobId` (produced by mod-search itself) |

The active job is resolved in the tenant that owns the reindex (central tenant for consortia), so jobs of different
tenants are independent. A skipped event is acknowledged and never touches reindex status, merge/upload ranges,
PostgreSQL entity/browse tables, or OpenSearch indexes. Retrying failed merge ranges and upload-only reindexes reuse
the active job (or register one if the tenant has none).

The header name must start with `X-Okapi-`: RMB (mod-inventory-storage) drops any other request header. A header is
used instead of a body field because the request schema has `additionalProperties: false`, so a body field would make
unpatched mod-inventory-storage reject the request.

## Decisions

| Event job id                        | Outcome     | Reason tag                | Processed? |
|-------------------------------------|-------------|---------------------------|------------|
| equals the active job               | `processed` | `job_id_matched`          | yes        |
| filter disabled                     | `processed` | `filter_disabled`         | yes        |
| missing, policy `PROCESS`           | `processed` | `missing_job_id_fallback` | yes        |
| missing, policy `IGNORE`            | `ignored`   | `missing_job_id`          | no         |
| not a UUID                          | `invalid`   | `malformed_job_id`        | no         |
| tenant has no active job            | `unmatched` | `no_active_job`           | no         |
| another job (other instance, or an earlier/expired job of this one) | `unmatched` | `job_id_mismatch` | no |

Every decision increments the Micrometer counter `search.reindex.events` tagged `eventType`, `outcome`, `reason`, and
is logged with module name, tenant, event type, range id, job id and its source (`DEBUG` for processed, `INFO` for
ignored/unmatched, `WARN` for invalid). To read the counter over HTTP, add `metrics` to
`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` and query `/admin/metrics/search.reindex.events`.

## Configuration

| Env variable                    | Default   | v1 (live) | v2 (background) |
|---------------------------------|-----------|-----------|-----------------|
| `REINDEX_JOB_FILTER_ENABLED`    | `true`    | `true`    | `true`          |
| `REINDEX_JOB_MISSING_ID_POLICY` | `PROCESS` | `PROCESS` | `IGNORE`        |

`REINDEX_JOB_FILTER_ENABLED=false` restores the previous process-everything behavior (rollback switch).

## Database

Liquibase changeset `MSEARCH-1192@@create:reindex_job` creates the `reindex_job` table (`id UUID PK`,
`created_at timestamp`) in every tenant schema of the instance on tenant init/upgrade. No data migration is needed.

## Deployment requirements

- **v1 and v2 must use different Kafka consumer group ids.** This version hard-codes them
  (`${folio.environment}-mod-search-*-group`). If both instances share a group, Kafka splits events between them
  instead of delivering every event to both; with filtering on, events of v2's job that land on v1 are skipped and
  never reach v2, so v2's reindex never completes.
- **v1 and v2 must use different database schemas.** The active job is stored per tenant schema; instances sharing a
  schema share the same active job, and filtering cannot tell them apart.
- **Both instances must run this build.** An unpatched v1 does not filter and keeps processing v2's events.
- **mod-inventory-storage must run the patched build** (`MSEARCH-1283-poc`). Without it, events carry no job id:
  v1 (`PROCESS`) falls back to processing them, and v2 (`IGNORE`) skips all of them, so its reindex never completes.
- A full reindex that was already running when v1 is upgraded to this build has no registered job; its in-flight
  events that carry a job id are reported as `no_active_job`. Start reindexes after the upgrade.
- Live (non-reindex) Kafka listeners are not affected.
