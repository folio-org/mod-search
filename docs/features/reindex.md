---
feature_id: reindex
title: Reindex
updated: 2026-07-03
---

# Reindex

## Overview

Orchestrates full and partial re-population of the OpenSearch index for all inventory record types: instances, holdings, items, subjects, contributors, classifications, and call numbers. A full reindex runs both a merge phase (ingesting raw records from inventory into staging tables) and an upload phase (reading those tables and pushing ranges to OpenSearch). Provides status tracking and the ability to retry failed merge ranges.

After upgrades, configuration changes, or index corruption, the search index must be rebuilt from the authoritative database state. Reindex enables operators to trigger and monitor this process without downtime, using a distributed range-based approach that is safe to run in a clustered deployment.

## Reindex Types

| Type                                                                   | Description                                                                                   |
|------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| [Full Reindex — Kafka (PUBLISH mode)](reindex/reindex-full-kafka.md)   | Full reindex using Kafka to stream records from mod-inventory (default mode, most performant) |
| [Full Reindex — S3 (EXPORT mode)](reindex/reindex-full-s3.md)          | Full reindex using S3/object storage as an intermediary for cost optimization                 |
| [Full Reindex — ECS Member Tenant](reindex/reindex-full-ecs-member.md) | Full reindex triggered on a consortium member tenant in an ECS deployment                     |
| [Upload-Phase Reindex](reindex/reindex-upload.md)                      | Runs only the upload phase (OpenSearch indexing) without re-merging records from inventory    |
| [Failed Merge Reindex](reindex/reindex-failed-merge.md)                | Retries only the merge ranges that previously failed                                          |
| [Legacy Reindex](reindex/reindex-legacy.md)                            | Reindex for entity types other than instance via the legacy inventory reindex endpoint        |

## Choosing a Reindex Type

| Situation                                                            | Use                                                                    |
|----------------------------------------------------------------------|------------------------------------------------------------------------|
| Full index rebuild — default environment (`REINDEX_TYPE=PUBLISH`)    | [Full Reindex — Kafka](reindex/reindex-full-kafka.md)                  |
| Full index rebuild — S3 export environment (`REINDEX_TYPE=EXPORT`)   | [Full Reindex — S3](reindex/reindex-full-s3.md)                        |
| Reindex a specific ECS consortium member tenant                      | [Full Reindex — ECS Member Tenant](reindex/reindex-full-ecs-member.md) |
| OpenSearch deleted or upgraded; inventory data already in PostgreSQL | [Upload-Phase Reindex](reindex/reindex-upload.md)                      |
| Previous full reindex left some ranges in `MERGE_FAILED`             | [Failed Merge Reindex](reindex/reindex-failed-merge.md)                |
| Reindexing authorities, locations, or linked-data records            | [Legacy Reindex](reindex/reindex-legacy.md)                            |

## API Endpoints

| Method | Path                                                  | Description                                                            |
|--------|-------------------------------------------------------|------------------------------------------------------------------------|
| POST   | `/search/index/instance-records/reindex/full`         | Initiates a full reindex (merge + upload) for all entity types         |
| POST   | `/search/index/instance-records/reindex/upload`       | Initiates an upload-phase reindex for selected entity types            |
| POST   | `/search/index/instance-records/reindex/merge/failed` | Retries failed merge ranges                                            |
| GET    | `/search/index/instance-records/reindex/status`       | Returns reindex status per entity type                                 |
| POST   | `/search/index/inventory/reindex`                     | Triggers legacy reindex for non-instance entity types                  |
| PUT    | `/search/index/settings`                              | Updates dynamic index settings (use to restore settings after reindex) |

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

Entity types are split into two phases:

- **Merge phase** — records are received from inventory via Kafka (PUBLISH mode) or read from object storage (EXPORT mode) and staged in PostgreSQL. Applies to: `INSTANCE`, `HOLDINGS`, `ITEM`.
- **Upload phase** — staged records are read from the local DB in ranges and pushed to OpenSearch. Applies to: `INSTANCE`, `SUBJECT`, `CONTRIBUTOR`, `CLASSIFICATION`, `CALL_NUMBER`.

All entity type tables are truncated before a full reindex starts.

## Monitoring Progress

Use `GET /search/index/instance-records/reindex/status` to track progress. Each item in the response represents one entity type:

```json
[
  {
    "entityType": "instance",
    "status": "UPLOAD_COMPLETED",
    "totalMergeRanges": 100,
    "processedMergeRanges": 100,
    "totalUploadRanges": 200,
    "processedUploadRanges": 200,
    "startTimeMerge": "2024-04-01T01:37:30Z",
    "endTimeMerge": "2024-04-01T02:10:00Z",
    "startTimeUpload": "2024-04-01T02:10:01Z",
    "endTimeUpload": "2024-04-01T02:38:00Z"
  }
]
```

> `targetTenantId` is only populated during member tenant reindexing — see [Full Reindex — ECS Member Tenant](reindex/reindex-full-ecs-member.md).

### Status values

| Status                | Description                                                    |
|-----------------------|----------------------------------------------------------------|
| `MERGE_IN_PROGRESS`   | Receiving records from inventory via Kafka                     |
| `MERGE_COMPLETED`     | All records staged in PostgreSQL                               |
| `MERGE_FAILED`        | One or more merge ranges failed                                |
| `STAGING_IN_PROGRESS` | Member tenant reindex: staging member records to central index |
| `STAGING_COMPLETED`   | Member tenant reindex: staging phase done                      |
| `STAGING_FAILED`      | Member tenant reindex: staging phase failed                    |
| `UPLOAD_IN_PROGRESS`  | Uploading staged records to OpenSearch                         |
| `UPLOAD_COMPLETED`    | All records indexed — this entity type is done                 |
| `UPLOAD_FAILED`       | One or more upload ranges failed                               |

Reindex is complete when all upload-phase entity types (`instance`, `subject`, `contributor`, `classification`, `call-number`) show `UPLOAD_COMPLETED`, and all merge-only entity types (`holdings`, `item`) show `MERGE_COMPLETED`.

## Performance Tuning

### Indexing throughput during reindex

Pass `indexSettings` in the reindex request to disable replica writes and background refresh while indexing — this can reduce reindex time significantly on large datasets:

```json
{
  "indexSettings": {
    "numberOfReplicas": 0,
    "refreshInterval": -1
  }
}
```

Restore production values after completion:

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

### Key tuning variables

**Merge phase (PUBLISH mode):**

| Variable                                       | Default | Effect                                                                         |
|------------------------------------------------|---------|--------------------------------------------------------------------------------|
| `REINDEX_MERGE_RANGE_PUBLISHER_CORE_POOL_SIZE` | `30`    | Parallel HTTP requests to mod-inventory-storage                                |
| `REINDEX_MERGE_RANGE_PUBLISHER_MAX_POOL_SIZE`  | `30`    | Maximum parallel HTTP requests                                                 |
| `EXCHANGE_HTTP_MAX_CONN_PER_ROUTE`             | `50`    | HTTP connection pool — must be ≥ `REINDEX_MERGE_RANGE_PUBLISHER_MAX_POOL_SIZE` |
| `KAFKA_REINDEX_RECORDS_CONCURRENCY`            | `4`     | Parallel Kafka consumers staging records                                       |
| `REINDEX_MERGE_RANGE_SIZE`                     | `500`   | Records per merge range                                                        |
| `REINDEX_MIGRATION_WORK_MEM`                   | `64MB`  | PostgreSQL `work_mem` for staging migration queries                            |

**Upload phase:**

| Variable                                | Default | Effect                                     |
|-----------------------------------------|---------|--------------------------------------------|
| `KAFKA_REINDEX_RANGE_INDEX_CONCURRENCY` | `8`     | Parallel Kafka consumers for upload ranges |
| `REINDEX_UPLOAD_RANGE_SIZE`             | `1000`  | Records per upload range                   |

### Infrastructure sizing

For environments with ~10 million records, expect **1.5–2.5 hours** for a full reindex depending on configuration. For production scale, recommended minimums:
- PostgreSQL: instance with ≥16 vCPUs, ≥128 GB RAM (e.g. `db.r6g.4xlarge`)
- OpenSearch: cluster with 4+ data nodes

## Constraints and Preconditions

- Full reindex triggered directly on a consortium member tenant is rejected; see [Full Reindex — ECS Member Tenant](reindex/reindex-full-ecs-member.md) for the correct approach.
- Upload reindex is rejected while a merge is in progress or in a failed state for the requested entity type.
- Failed-merge reindex returns immediately if no failed ranges exist.
- Simultaneous multi-tenant reindexing is not supported — run one tenant at a time.

## Error Behavior

- `400 Bad Request` — invalid request parameters.
- `500 Internal Server Error` — internal failure.
- Kafka listener processing uses retry backoff (`KAFKA_RETRY_INTERVAL_MS`, `KAFKA_RETRY_DELIVERY_ATTEMPTS`).
- Recoverable pessimistic locking failures raise `ReindexException`; non-recoverable errors mark the merge range as failed.
- Failed merge ranges can be retried via `POST /search/index/instance-records/reindex/merge/failed` — see [Failed Merge Reindex](reindex/reindex-failed-merge.md).

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

## FAQ

**Can I run reindex for multiple tenants simultaneously?**
No. Parallel multi-tenant reindexing is not supported. Run one tenant at a time.

**Will reindex affect ongoing search performance?**
Yes — indexing puts load on OpenSearch and PostgreSQL. Setting `numberOfReplicas: 0` and `refreshInterval: -1` in the reindex request reduces overhead. Search queries may be slower while reindex is in progress.

**How do I know when reindex is fully complete?**
Poll `GET /search/index/instance-records/reindex/status` until all upload-phase entity types (`instance`, `subject`, `contributor`, `classification`, `call-number`) show `UPLOAD_COMPLETED` and all merge-only entity types (`holdings`, `item`) show `MERGE_COMPLETED`.

**The status shows completed but `processedMergeRanges` or `processedUploadRanges` is greater than `totalMergeRanges` / `totalUploadRanges`. Is something wrong?**
This is a known issue. It means some ranges were retried internally via Kafka, which increments the processed counter beyond the original total. If the counts have stopped growing and the status is `UPLOAD_COMPLETED` / `MERGE_COMPLETED`, the reindex is considered successful.

**Can I stop a reindex that is in progress?**
There is no explicit stop endpoint. Triggering a new full reindex truncates all staging tables and starts fresh, effectively cancelling the previous run.

**What if some merge ranges fail?**
Use [Failed Merge Reindex](reindex/reindex-failed-merge.md) to retry only the failed ranges after resolving the underlying cause.

**Do I need to reindex after every upgrade?**
Only if the upgrade includes OpenSearch mapping changes or if the release notes explicitly call for a reindex. Check the release notes and the changelog in `docs/features/` for the relevant feature area.

**When should I use upload reindex instead of full reindex?**
Use [Upload-Phase Reindex](reindex/reindex-upload.md) when the issue is in OpenSearch only (index deleted, cluster upgraded) and the data in the PostgreSQL resource tables is still valid. If you need fresh data from inventory, run a full reindex.

## Dependencies and Interactions

- Depends on OpenSearch for bulk index write operations.
- Depends on PostgreSQL for staging and reading all entity type records.
- Publishes range events to the internal `search.reindex.range-index` Kafka topic.
- Consumes `inventory.reindex-records` and `inventory.reindex.file-ready` topics produced by mod-inventory.
- In EXPORT mode, reads exported records from object storage via `FolioS3Client`.
