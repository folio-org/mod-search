# Reindex job id filtering (MSEARCH-1192 PoC)

When two mod-search instances run side by side (e.g. live **v1** and background **v2**), both consume the shared
`inventory.reindex-records` and `inventory.reindex.file-ready` topics with their own consumer groups, so each instance
receives every reindex event. Job id filtering makes each instance process only the events of the reindex job it
started itself.

## How it works

1. `POST /search/index/instance-records/reindex/full` registers a new `reindexJobId` (UUID) in the tenant's
   `reindex_job` table, replacing the previous one. From that moment events of any earlier job are ignored.
2. Every merge range stores the job id as its `traceId`. Each `/inventory-reindex-records/publish|export` request
   sends it in the `X-Okapi-Reindex-Job-Id` header (EXPORT also sends it as `traceId` in the body).
3. mod-inventory-storage copies the header onto the Kafka events it publishes for that request.
4. `ReindexKafkaListener` resolves the job id of each event and asks `ReindexJobEventFilter` before processing:

| Event                          | Job id source                                         |
|--------------------------------|-------------------------------------------------------|
| `inventory.reindex-records`    | Kafka header `X-Okapi-Reindex-Job-Id`                 |
| `inventory.reindex.file-ready` | Kafka header, falling back to payload `traceId`       |
| `search.reindex.range-index`   | payload `reindexJobId` (produced by mod-search itself) |

The active job is resolved in the tenant that owns the reindex (central tenant for consortia), so jobs of different
tenants are independent. A skipped event is acknowledged and never touches reindex status, merge/upload ranges,
PostgreSQL entity/browse tables, or OpenSearch indexes.

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

| Env variable                    | Default   | v1 (live)  | v2 (background) |
|---------------------------------|-----------|------------|-----------------|
| `REINDEX_JOB_FILTER_ENABLED`    | `true`    | `true`     | `true`          |
| `REINDEX_JOB_MISSING_ID_POLICY` | `PROCESS` | `PROCESS`  | `IGNORE`        |

`REINDEX_JOB_FILTER_ENABLED=false` restores the previous process-everything behavior (rollback switch).

## Database

Liquibase changeset `MSEARCH-1192@@create:reindex_job` creates the `reindex_job` table (`id UUID PK`,
`created_at timestamp`) in every tenant schema of the instance on tenant init/upgrade. No data migration is needed.

## Compatibility and deployment notes

- **Both instances must run this build.** An unpatched v1 does not filter and keeps processing v2's events.
- **PUBLISH mode requires the patched mod-inventory-storage** (branch `MSEARCH-1192-poc`), because the
  `reindex-records` payload has no other place for the job id. Without it, events have no job id: v1 (`PROCESS`)
  falls back to processing them, and v2 (`IGNORE`) skips all of them, so its reindex never completes.
- **EXPORT mode works with unpatched mod-inventory-storage** through the `traceId` fallback.
- A full reindex that was already running when v1 is upgraded to this build has no registered job; its in-flight
  events that carry a job id are reported as `no_active_job`. Start reindexes after the upgrade.
- An upload-only reindex (`POST /search/index/instance-records/reindex/upload`) reuses the active job, or registers one
  if the tenant has none.
- Live (non-reindex) Kafka listeners are not affected.
