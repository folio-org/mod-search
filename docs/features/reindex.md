---
feature_id: reindex
title: Reindex
updated: 2026-07-06
---

# Reindex

## Overview

Orchestrates full and partial re-population of the OpenSearch index for all inventory record types: instances, holdings, items, subjects, contributors, classifications, and call numbers. A full reindex runs both a merge phase (ingesting raw records from inventory into staging tables) and an upload phase (reading those tables and pushing ranges to OpenSearch). Provides status tracking and the ability to retry failed merge ranges.

After upgrades, configuration changes, or index corruption, the search index must be rebuilt from the authoritative database state. Reindex enables operators to trigger and monitor this process without downtime, using a distributed range-based approach that is safe to run in a clustered deployment.

## Choosing a Reindex Type

Each reindex type has its own guide with complete run instructions, request format, configuration, and performance notes. Start here to pick the right one, then follow the linked guide.

| Your situation | Use | Guide |
|---|---|---|
| Full rebuild from inventory — default environment (`REINDEX_TYPE=PUBLISH`) | Full Reindex — Kafka | [reindex-full-kafka.md](reindex/reindex-full-kafka.md) |
| Full rebuild via S3 object storage — cost-optimized (`REINDEX_TYPE=EXPORT`) | Full Reindex — S3 | [reindex-full-s3.md](reindex/reindex-full-s3.md) |
| Reindex a specific consortium member tenant in an ECS deployment | Full Reindex — ECS Member Tenant | [reindex-full-ecs-member.md](reindex/reindex-full-ecs-member.md) |
| OpenSearch deleted or upgraded; inventory data is current in PostgreSQL | Upload-Phase Reindex | [reindex-upload.md](reindex/reindex-upload.md) |
| Previous full reindex left ranges in `MERGE_FAILED` | Failed Merge Reindex | [reindex-failed-merge.md](reindex/reindex-failed-merge.md) |
| Reindex `authority` or `location` records | Legacy Reindex | [reindex-legacy.md](reindex/reindex-legacy.md) |

The remainder of this page covers concepts and reference material shared by **all** reindex types: phases, progress monitoring, index-settings restore, the consolidated configuration reference, and FAQ. Each guide links back here rather than repeating it.

## Kafka Topics

| Type     | Topic pattern                                | Description                                                                                         |
|----------|----------------------------------------------|-----------------------------------------------------------------------------------------------------|
| Consumer | `(env)(tenant).search.reindex.range-index`   | Processes upload range-index events for instance, subject, contributor, classification, call number |
| Consumer | `(env)(tenant).inventory.reindex-records`    | Processes inventory reindex record batches for instance, holdings, item (PUBLISH mode)              |
| Consumer | `(env)(tenant).inventory.reindex.file-ready` | Processes S3 file-ready events for instance, holdings, item (EXPORT mode)                           |

## Diagrams

Sequence diagrams for the reindex flow are in [`docs/diagrams/`](../diagrams/):

| Diagram                                          | Source                                 | Description                                                                           |
|--------------------------------------------------|----------------------------------------|---------------------------------------------------------------------------------------|
| [full-reindex.png](../diagrams/full-reindex.png) | [full.puml](../diagrams/full.puml)     | Full reindex — merge phase (PUBLISH and EXPORT modes) followed by upload phase        |
| [upload-phase.png](../diagrams/upload-phase.png) | [upload.puml](../diagrams/upload.puml) | Upload-only reindex — triggered directly via API or internally after merge completion |

---

## Reindex Phases

A reindex progresses through up to three phases:

- **Merge phase** — records are received from inventory via Kafka (PUBLISH mode) or read from object storage (EXPORT mode) and staged in PostgreSQL. Applies to: `instance`, `holdings`, `item`.
- **Staging phase** *(ECS member tenant reindex only)* — an intermediary stage between merge and upload that promotes a member tenant's merged records into the central tenant's index data. It is tracked separately via the `STAGING_IN_PROGRESS` / `STAGING_COMPLETED` / `STAGING_FAILED` status values and driven by the `REINDEX_MIGRATION_*` settings. Standalone (non-consortium) and full-consortium reindexes skip this phase. See [Full Reindex — ECS Member Tenant](reindex/reindex-full-ecs-member.md).
- **Upload phase** — staged records are read from the local DB in ranges and pushed to OpenSearch. Applies to: `instance`, `subject`, `contributor`, `classification`, `call-number`.

All entity type tables are truncated before a full reindex starts.

## Monitoring Progress

Use `GET /search/index/instance-records/reindex/status` to track progress. Each item in the response represents one entity type:

```json
[
  {
    "entityType": "instance",
    "status": "UPLOAD_COMPLETED",
    "targetTenantId": null,
    "totalMergeRanges": 100,
    "processedMergeRanges": 100,
    "totalUploadRanges": 200,
    "processedUploadRanges": 200,
    "startTimeMerge": "2024-04-01T01:37:30Z",
    "endTimeMerge": "2024-04-01T02:10:00Z",
    "startTimeUpload": "2024-04-01T02:10:01Z",
    "endTimeUpload": "2024-04-01T02:38:00Z",
    "startTimeStaging": null,
    "endTimeStaging": null
  }
]
```

`targetTenantId`, `startTimeStaging`, and `endTimeStaging` are only populated during ECS member tenant reindexing — see [Full Reindex — ECS Member Tenant](reindex/reindex-full-ecs-member.md).

### Status values

| Status                | Description                                                    |
|-----------------------|----------------------------------------------------------------|
| `MERGE_IN_PROGRESS`   | Merge phase in progress — records being staged in PostgreSQL   |
| `MERGE_COMPLETED`     | All records staged in PostgreSQL                               |
| `MERGE_FAILED`        | One or more merge ranges failed                                |
| `STAGING_IN_PROGRESS` | Member tenant reindex: staging member records to central index |
| `STAGING_COMPLETED`   | Member tenant reindex: staging phase done                      |
| `STAGING_FAILED`      | Member tenant reindex: staging phase failed                    |
| `UPLOAD_IN_PROGRESS`  | Uploading staged records to OpenSearch                         |
| `UPLOAD_COMPLETED`    | All records indexed — this entity type is done                 |
| `UPLOAD_FAILED`       | One or more upload ranges failed                               |

Reindex is complete when all upload-phase entity types (`instance`, `subject`, `contributor`, `classification`, `call-number`) show `UPLOAD_COMPLETED`, and all merge-only entity types (`holdings`, `item`) show `MERGE_COMPLETED`.

## Restoring Index Settings After Reindex

The full and upload reindex requests accept an `indexSettings` override that disables replica writes and periodic refresh to speed up indexing on large datasets. See each reindex guide's **Performance** section for the request-time settings and type-specific tuning.

Those overrides persist after the reindex finishes, so restore production values with `PUT /search/index/settings` once it completes:

```http
PUT /search/index/settings
x-okapi-tenant: <tenant>
Content-Type: application/json

{
  "resourceName": "instance",
  "indexSettings": {
    "numberOfReplicas": 1,
    "refreshInterval": 1
  }
}
```

> `numberOfShards` is **not** a valid field on this endpoint — it is only applied at index creation time via the reindex trigger.


## Constraints and Preconditions

- Full reindex triggered directly on a consortium member tenant is rejected; see [Full Reindex — ECS Member Tenant](reindex/reindex-full-ecs-member.md) for the correct approach.
- Upload reindex is rejected while a merge is in progress or in a failed state for the requested entity type.
- Failed-merge reindex returns immediately if no failed ranges exist.
- Simultaneous multi-tenant reindexing is not supported — run one tenant at a time.
- Background resource processing is disabled if the reindex is failed. Reindex must be restarted in such case to ensure data consistency.

## Error Behavior

- Kafka listener processing uses retry backoff (`KAFKA_RETRY_INTERVAL_MS`, `KAFKA_RETRY_DELIVERY_ATTEMPTS`).
- Non-recoverable processing errors mark the affected merge range as `MERGE_FAILED`. Retrying via `POST /search/index/instance-records/reindex/merge/failed` — see [Failed Merge Reindex](reindex/reindex-failed-merge.md).

## Configuration Reference

| Variable                                          | Default                    | Purpose                                                                                  |
|---------------------------------------------------|----------------------------|------------------------------------------------------------------------------------------|
| `REINDEX_TYPE`                                    | `PUBLISH`                  | Merge ingestion mode: `PUBLISH` (Kafka) or `EXPORT` (S3)                                 |
| `REINDEX_MERGE_RANGE_SIZE`                        | `500`                      | Records per merge range                                                                  |
| `REINDEX_MERGE_RANGE_PUBLISHER_CORE_POOL_SIZE`    | `30`                       | Core thread pool for merge range publishing                                              |
| `REINDEX_MERGE_RANGE_PUBLISHER_MAX_POOL_SIZE`     | `30`                       | Max thread pool for merge range publishing                                               |
| `REINDEX_MERGE_RANGE_PUBLISHER_RETRY_INTERVAL_MS` | `1000`                     | Retry interval (ms) for merge range publishing                                           |
| `REINDEX_MERGE_RANGE_PUBLISHER_RETRY_ATTEMPTS`    | `5`                        | Retry attempts for merge range publishing                                                |
| `REINDEX_UPLOAD_RANGE_SIZE`                       | `1000`                     | Records per upload range                                                                 |
| `REINDEX_UPLOAD_RANGE_LEVEL`                      | `3`                        | Range tree depth for upload phase                                                        |
| `REINDEX_LOCATION_BATCH_SIZE`                     | `1000`                     | Batch size for location reindex                                                          |
| `REINDEX_MIGRATION_WORK_MEM`                      | `64MB`                     | PostgreSQL `work_mem` for staging migration queries                                      |
| `REINDEX_MIGRATION_STATEMENT_TIMEOUT`             | `0`                        | PostgreSQL statement timeout for migration (0 = no limit)                                |
| `KAFKA_REINDEX_RANGE_INDEX_CONCURRENCY`           | `8`                        | Concurrency for upload range-index consumer                                              |
| `KAFKA_REINDEX_RANGE_INDEX_TOPIC_PARTITIONS`      | `16`                       | Partition count for the `search.reindex.range-index` topic                               |
| `KAFKA_REINDEX_RECORDS_CONCURRENCY`               | `4`                        | Concurrency for reindex records consumer (PUBLISH mode)                                  |
| `KAFKA_REINDEX_FILE_READY_CONCURRENCY`            | `4`                        | Concurrency for file-ready consumer (EXPORT mode)                                        |
| `REINDEX_MERGE_EXPORT_BATCH_SIZE`                 | `500`                      | Batch size for reading S3 export files (EXPORT mode)                                     |
| `REINDEX_S3_RETRY_INTERVAL_MS`                    | `1000`                     | Retry interval (ms) for S3 read failures (EXPORT mode)                                   |
| `REINDEX_S3_RETRY_ATTEMPTS`                       | `3`                        | Retry attempts for S3 read failures (EXPORT mode)                                        |
| `S3_REINDEX_URL`                                  | `https://s3.amazonaws.com` | S3 endpoint (EXPORT mode)                                                                |
| `S3_REINDEX_REGION`                               | `us-west-2`                | S3 region (EXPORT mode)                                                                  |
| `S3_REINDEX_BUCKET`                               | _(empty)_                  | S3 bucket name (EXPORT mode)                                                             |
| `S3_REINDEX_ACCESS_KEY_ID`                        | _(empty)_                  | S3 access key (EXPORT mode)                                                              |
| `S3_REINDEX_SECRET_ACCESS_KEY`                    | _(empty)_                  | S3 secret key (EXPORT mode)                                                              |
| `S3_REINDEX_IS_AWS`                               | `true`                     | Use AWS SDK behaviour; `false` for MinIO or other S3-compatible storage                  |
| `EXCHANGE_HTTP_MAX_CONN_PER_ROUTE`                | `50`                       | HTTP connection pool per route — must be ≥ `REINDEX_MERGE_RANGE_PUBLISHER_MAX_POOL_SIZE` |

### Shared database settings

These variables come from the `folio-spring-base` dependency (not mod-search itself) and configure the shared HikariCP PostgreSQL connection pool. They are **not** reindex-specific, but they affect **any** reindex type that reads from or writes to PostgreSQL — full, upload, member, and failed-merge — so they are worth tuning for large reindexes.

| Variable               | Default   | Purpose                                                                                                                              |
|------------------------|-----------|------------------------------------------------------------------------------------------------------------------------------------|
| `DB_MAXSHAREDPOOLSIZE` | `10`      | Maximum HikariCP DB connection pool size (HikariCP default applies when unset). Raising it relieves read-connection contention during the upload phase, where each range reads staged records from PostgreSQL. |
| `DB_QUERYTIMEOUT`      | `60000`   | PostgreSQL statement timeout (ms) applied to pooled connections. Raise it — or lower the upload range size — if large upload ranges hit statement timeouts. |


## FAQ

**Can I run reindex for multiple tenants simultaneously?**                                                                                                                                                      
No. Parallel multi-tenant reindexing is not supported. Run one tenant at a time.  

**The status shows completed but `processedMergeRanges` or `processedUploadRanges` is greater than `totalMergeRanges` / `totalUploadRanges`. Is something wrong?**
This is a known issue. It means some ranges were retried internally via Kafka, which increments the processed counter beyond the original total. If the counts have stopped growing and the status is `UPLOAD_COMPLETED` / `MERGE_COMPLETED`, the reindex is considered successful.

**Can I stop a reindex that is in progress?**
There is no explicit stop endpoint. Stopping mod-search and mod-inventory-storage and cleaning up the Kafka topics will halt the reindex, but is not recommended. To restart reindex after a forced stop, manually correct all entity type statuses in the database to `..._FAILED` or `..._COMPLETED` first — otherwise a new reindex will not start.

**Is it safe to run data import or other inventory updates during a reindex?**
It is possible, but not recommended for large jobs. Concurrent inventory writes compete for the same mod-inventory-storage, Kafka, PostgreSQL, and OpenSearch capacity, slowing the reindex — and the reindex slows the imports in return. If you must overlap them, monitor resource usage closely and expect a longer reindex.

**Do I need to reindex after every upgrade?**
Only if the upgrade includes OpenSearch mapping changes or if the release notes explicitly call for a reindex. Check the release notes and the changelog in `docs/features/` for the relevant feature area.
