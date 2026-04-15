---
feature_id: instance-records-reindex
title: Instance Records Reindex
updated: 2026-03-02
---

# Instance Records Reindex

## What it does
This feature runs tenant-scoped reindexing for instance, holdings, and item search data, including full reindex, upload-only reindex, and failed-merge retry flows. It processes merge/upload ranges asynchronously and tracks progress by entity status. It supports two merge ingestion modes: direct inventory reindex-record messages and S3 file-ready events.

## Why it exists
Search indexes must be rebuilt or repaired when source inventory data changes at scale, when index settings change, or when prior merge processing fails. This feature provides controlled restart/retry paths and status visibility so operators can run reindex operations without manual database intervention.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| POST | /search/index/instance-records/reindex/full | Starts full reindex for instance records |
| POST | /search/index/instance-records/reindex/upload | Starts upload reindex for selected entity types |
| POST | /search/index/instance-records/reindex/merge/failed | Re-runs failed merge ranges |
| GET | /search/index/instance-records/reindex/status | Returns reindex status per entity type |

| Type | Topic | Description |
|------|-------|-------------|
| Kafka Consumer | `folio.kafka.listener.reindex-range-index` | Processes upload range indexing events |
| Kafka Consumer | `folio.kafka.listener.reindex-records` | Processes inventory reindex record batches |
| Kafka Consumer | `folio.kafka.listener.reindex-file-ready` | Processes file-ready events and reads exported records from S3 |

### Event processing
- When processed: per incoming message.
- Event types handled: `ReindexRangeIndexEvent`, `ReindexRecordsEvent`, `ReindexFileReadyEvent`.
- Processing behavior: successful merge completion updates range status and can trigger upload reindex submission when merge is fully complete.

## Business rules and constraints
- Full reindex is rejected for consortium member tenants; only central/non-member tenant execution is allowed.
- Full reindex clears existing reindex records, recreates merge status records, recreates upload-supported indices, then builds/publishes merge ranges.
- Merge ranges are created per inventory record type; when count is zero, an empty UUID range is still created to keep workflow progression explicit.
- Merge ranges include a `traceId` correlation value used by EXPORT ingestion events/files.
- Upload reindex is rejected when merge is in progress/failed or when upload for a selected entity type is already in progress.
- Failed-merge reindex returns immediately if no failed ranges exist.
- In EXPORT mode, file-ready events are read line-by-line from object storage and persisted in batches.

## Error behavior (if applicable)
- REST entry points expose `400` and `500` responses per OpenAPI definitions for reindex APIs.
- Kafka listener processing uses retry backoff from common Kafka error handler settings (`folio.kafka.retry-interval-ms`, `folio.kafka.retry-delivery-attempts`).
- Recoverable pessimistic locking failures in merge processing raise `ReindexException`; non-recoverable merge errors mark merge status/range as failed.

## Configuration (if applicable)
| Variable | Purpose |
|----------|---------|
| `folio.reindex.reindex-type` (`REINDEX_TYPE`) | Selects merge ingestion mode (`PUBLISH` or `EXPORT`) |
| `folio.reindex.merge-range-size` (`REINDEX_MERGE_RANGE_SIZE`) | Controls merge range partition size |
| `folio.reindex.upload-range-size` (`REINDEX_UPLOAD_RANGE_SIZE`) | Controls upload range partitioning |
| `folio.reindex.upload-range-level` (`REINDEX_UPLOAD_RANGE_LEVEL`) | Controls upload range granularity |
| `folio.reindex.merge-range-publisher-core-pool-size` (`REINDEX_MERGE_RANGE_PUBLISHER_CORE_POOL_SIZE`) | Core publisher concurrency for merge range publishing |
| `folio.reindex.merge-range-publisher-max-pool-size` (`REINDEX_MERGE_RANGE_PUBLISHER_MAX_POOL_SIZE`) | Max publisher concurrency for merge range publishing |
| `folio.reindex.merge-range-publisher-retry-interval-ms` (`REINDEX_MERGE_RANGE_PUBLISHER_RETRY_INTERVAL_MS`) | Retry delay for publishing merge ranges |
| `folio.reindex.merge-range-publisher-retry-attempts` (`REINDEX_MERGE_RANGE_PUBLISHER_RETRY_ATTEMPTS`) | Retry attempts for publishing merge ranges |
| `folio.kafka.listener.reindex-range-index.*` | Consumer settings for upload range indexing events |
| `folio.kafka.listener.reindex-records.*` | Consumer settings for inventory reindex records events |
| `folio.kafka.listener.reindex-file-ready.*` | Consumer settings for exported file-ready events |
| `folio.remote-storage.endpoint` (`S3_REINDEX_URL`) | S3 endpoint for EXPORT mode file reads |
| `folio.remote-storage.region` (`S3_REINDEX_REGION`) | S3 region for EXPORT mode |
| `folio.remote-storage.bucket` (`S3_REINDEX_BUCKET`) | Bucket used for EXPORT mode |
| `folio.remote-storage.access-key` (`S3_REINDEX_ACCESS_KEY_ID`) | Access key for EXPORT mode storage client |
| `folio.remote-storage.secret-key` (`S3_REINDEX_SECRET_ACCESS_KEY`) | Secret key for EXPORT mode storage client |
| `folio.remote-storage.aws-sdk` (`S3_REINDEX_IS_AWS`) | Selects AWS SDK-compatible behavior for storage client |

## Dependencies and interactions (if applicable)
- Depends on Inventory reindex APIs through `inventory-reindex-records/publish` and `inventory-reindex-records/export` for merge range publication.
- Consumes Kafka events for `search.reindex.range-index`, `inventory.reindex-records`, and `inventory.reindex.file-ready` via configured topic patterns.
- In EXPORT mode, reads exported records from object storage through `FolioS3Client`.
