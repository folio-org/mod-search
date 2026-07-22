---
feature_id: reindex
title: Reindex
updated: 2026-05-25
---

# Reindex

## What it does
Orchestrates full and partial re-population of the OpenSearch index for all inventory record types: instances, holdings, items, subjects, contributors, classifications, and call numbers. A full reindex runs both a merge phase (ingesting raw records from inventory into staging tables) and an upload phase (reading those tables and pushing ranges to OpenSearch). Provides status tracking and the ability to retry failed merge ranges.

## Why it exists
After upgrades, configuration changes, or index corruption, the search index must be rebuilt from the authoritative database state. Reindex enables operators to trigger and monitor this process without downtime, using a distributed range-based approach that is safe to run in a clustered deployment.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/search/index/instance-records/reindex/full` | Initiates a full reindex (merge + upload) for all entity types |
| POST | `/search/index/instance-records/reindex/upload` | Initiates an upload-phase reindex for selected entity types |
| POST | `/search/index/instance-records/reindex/merge/failed` | Retries failed merge ranges |
| GET | `/search/index/instance-records/reindex/status` | Returns reindex status per entity type |
| POST | `/search/index/inventory/reindex` | Triggers legacy inventory reindex |

| Type | Topic pattern | Description |
|------|---------------|-------------|
| Kafka Consumer | `folio.kafka.listener.reindex-range-index.topicPattern` | Processes upload range-index events for instance, subject, contributor, classification, call number |
| Kafka Consumer | `folio.kafka.listener.reindex-records.topicPattern` | Processes inventory reindex record batches for instance, holdings, item |
| Kafka Consumer | `folio.kafka.listener.reindex-file-ready.topicPattern` | Processes S3 file-ready events for instance, holdings, item (EXPORT mode) |

## Diagrams

Sequence diagrams for the reindex flow are in [`docs/diagrams/`](../diagrams/):

| Diagram | Source | Description |
|---------|--------|-------------|
| [full-reindex.png](../diagrams/full-reindex.png) | [full.puml](../diagrams/full.puml) | Full reindex — merge phase (PUBLISH and EXPORT modes) followed by upload phase |
| [upload-phase.png](../diagrams/upload-phase.png) | [upload.puml](../diagrams/upload.puml) | Upload-only reindex — triggered directly via API or internally after merge completion |

---

## Runbooks

| Runbook | Description |
|---------|-------------|
| [reindex-upload-failure-cleanup.md](../reindex-upload-failure-cleanup.md) | Diagnose and clean up oversized instance data (too many related holdings/items/etc.) that makes the upload phase fail on a testing environment. |

## Business rules and constraints

### Reindex phases
- Entity types split into two phases:
  - **Merge phase** — records are received from inventory via Kafka and staged in PostgreSQL: `INSTANCE`, `HOLDINGS`, `ITEM`.
  - **Upload phase** — ranges are read from the local DB and pushed to OpenSearch: `INSTANCE`, `SUBJECT`, `CONTRIBUTOR`, `CLASSIFICATION`, `CALL_NUMBER`.
- All entity type tables are truncated before a full reindex starts.
- Merge ranges include a `traceId` correlation value used in EXPORT mode to match file-ready events to the originating reindex run.

### Limitations and preconditions
- Full reindex is rejected for consortium member tenants; only central/non-member tenant execution is allowed.
- Upload reindex is rejected while a merge is in progress or in a failed state for the requested entity type.
- Failed-merge reindex (`POST .../merge/failed`) returns immediately if no failed ranges exist.

### EXPORT mode behaviour
- In EXPORT mode (`REINDEX_TYPE=EXPORT`), file-ready events are read line-by-line from object storage and persisted in batches before upload ranges are submitted.

## Error behavior
- `400 Bad Request` — invalid request parameters.
- `500 Internal Server Error` — internal failure.
- Kafka listener processing uses retry backoff (`folio.kafka.retry-interval-ms`, `folio.kafka.retry-delivery-attempts`).
- Recoverable pessimistic locking failures raise `ReindexException`; non-recoverable errors mark the merge range as failed.
- Failed merge ranges can be retried via `POST /search/index/instance-records/reindex/merge/failed`.

## Configuration
| Variable | Purpose |
|----------|---------|
| `REINDEX_TYPE` | Selects merge ingestion mode (`PUBLISH` or `EXPORT`) |
| `REINDEX_UPLOAD_RANGE_SIZE` | Number of records per upload range |
| `REINDEX_UPLOAD_RANGE_LEVEL` | Range tree depth for upload phase |
| `REINDEX_MERGE_RANGE_SIZE` | Number of records per merge range |
| `REINDEX_MERGE_RANGE_PUBLISHER_CORE_POOL_SIZE` | Core thread pool size for merge range publishing |
| `REINDEX_MERGE_RANGE_PUBLISHER_MAX_POOL_SIZE` | Max thread pool size for merge range publishing |
| `REINDEX_MERGE_RANGE_PUBLISHER_RETRY_INTERVAL_MS` | Retry interval for merge range publishing |
| `REINDEX_MERGE_RANGE_PUBLISHER_RETRY_ATTEMPTS` | Retry attempts for merge range publishing |
| `KAFKA_REINDEX_RANGE_INDEX_CONCURRENCY` | Concurrency for reindex range-index consumer |
| `KAFKA_REINDEX_RECORDS_CONCURRENCY` | Concurrency for reindex records consumer |
| `S3_REINDEX_URL` | S3 endpoint for EXPORT mode file reads |
| `S3_REINDEX_REGION` | S3 region for EXPORT mode |
| `S3_REINDEX_BUCKET` | S3 bucket for EXPORT mode |
| `S3_REINDEX_ACCESS_KEY_ID` | Access key for EXPORT mode storage client |
| `S3_REINDEX_SECRET_ACCESS_KEY` | Secret key for EXPORT mode storage client |
| `S3_REINDEX_IS_AWS` | Selects AWS SDK-compatible behaviour for storage client |

## Dependencies and interactions
- Depends on OpenSearch for bulk index write operations.
- Depends on PostgreSQL for staging and reading all entity type records.
- Publishes range events to the internal `search.reindex.range-index` Kafka topic.
- Consumes `inventory.reindex-records` and `inventory.reindex.file-ready` topics produced by mod-inventory.
- In EXPORT mode, reads exported records from object storage via `FolioS3Client`.
