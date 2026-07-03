---
title: Full Reindex — Kafka (PUBLISH mode)
---

# Full Reindex — Kafka (PUBLISH mode)

## Overview

PUBLISH mode is the default full reindex strategy (`REINDEX_TYPE=PUBLISH`). mod-search's merge range publisher makes parallel HTTP calls to mod-inventory-storage, which responds by streaming records to Kafka. mod-search consumes those records and stages them in PostgreSQL. Once all entity types have been staged (merge phase complete), the upload phase reads staged records in ranges and bulk-writes them to OpenSearch.

## When to use

- After OpenSearch index mapping changes that require a full rebuild
- After suspected index corruption
- After a major data migration in mod-inventory-storage
- When standing up a new environment from scratch

Do **not** use when:
- OpenSearch was upgraded or its indices deleted, but inventory data is current → use [Upload-Phase Reindex](reindex-upload.md) instead
- Reindexing a specific ECS consortium member tenant → use [Full Reindex — ECS Member Tenant](reindex-full-ecs-member.md) instead
- Reindexing authorities, locations, or linked-data records → use [Legacy Reindex](reindex-legacy.md) instead

## Prerequisites

- `REINDEX_TYPE=PUBLISH` (the default — no change needed in production)
- mod-inventory-storage is reachable from mod-search
- Kafka cluster is available with sufficient throughput

## Step 1: Trigger full reindex

```http
POST /search/index/instance-records/reindex/full
x-okapi-tenant: <tenant>
Content-Type: application/json
```

### Request body

All fields are optional. An empty body `{}` is valid.

```json
{
  "indexSettings": {
    "numberOfShards": 1,
    "numberOfReplicas": 0,
    "refreshInterval": -1
  }
}
```

| Field                            | Type              | Description                                                                                           |
|----------------------------------|-------------------|-------------------------------------------------------------------------------------------------------|
| `indexSettings.numberOfShards`   | integer (1–100)   | Primary shard count for the recreated index                                                           |
| `indexSettings.numberOfReplicas` | integer (0–100)   | Replica count. Setting `0` during reindex removes replica write overhead                              |
| `indexSettings.refreshInterval`  | integer (-1–3600) | Refresh interval in seconds. `-1` disables periodic refresh, significantly improving write throughput |

> **Performance tip:** Pass `"numberOfReplicas": 0` and `"refreshInterval": -1` to maximize indexing speed. Restore production values with `PUT /search/index/settings` after reindex completes.

### Response

`200 OK` — reindex started. No response body.

## Step 2: Monitor progress

```http
GET /search/index/instance-records/reindex/status
x-okapi-tenant: <tenant>
```

Example response:

```json
[
  {
    "entityType": "instance",
    "status": "MERGE_IN_PROGRESS",
    "totalMergeRanges": 100,
    "processedMergeRanges": 42,
    "startTimeMerge": "2024-04-01T01:37:34.15755006Z"
  },
  {
    "entityType": "holdings",
    "status": "MERGE_COMPLETED",
    "totalMergeRanges": 100,
    "processedMergeRanges": 100,
    "startTimeMerge": "2024-04-01T01:37:30.15755006Z",
    "endTimeMerge": "2024-04-01T01:37:33.15755006Z"
  }
]
```

### Status values

| Status                | Description                                                                           |
|-----------------------|---------------------------------------------------------------------------------------|
| `MERGE_IN_PROGRESS`   | Receiving records from inventory via Kafka                                            |
| `MERGE_COMPLETED`     | All ranges staged in PostgreSQL                                                       |
| `MERGE_FAILED`        | One or more merge ranges failed — see [Failed Merge Reindex](reindex-failed-merge.md) |
| `STAGING_IN_PROGRESS` | Staging phase in progress                                                             |
| `STAGING_COMPLETED`   | Staging phase completed                                                               |
| `STAGING_FAILED`      | Staging phase failed                                                                  |
| `UPLOAD_IN_PROGRESS`  | Uploading staged records to OpenSearch                                                |
| `UPLOAD_COMPLETED`    | All records indexed in OpenSearch                                                     |
| `UPLOAD_FAILED`       | One or more upload ranges failed                                                      |

Reindex is complete when all upload-phase entity types (`instance`, `subject`, `contributor`, `classification`, `call-number`) show `UPLOAD_COMPLETED`, and all merge-only entity types (`holdings`, `item`) show `MERGE_COMPLETED`.

## Step 3: Restore index settings (if modified)

If you passed custom `indexSettings` in step 1, restore production values after reindex completes:

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

## Key configuration variables

| Variable                                       | Default | Purpose                                                                             |
|------------------------------------------------|---------|-------------------------------------------------------------------------------------|
| `REINDEX_MERGE_RANGE_SIZE`                     | `500`   | Records per merge range                                                             |
| `REINDEX_MERGE_RANGE_PUBLISHER_CORE_POOL_SIZE` | `30`    | Core thread pool for parallel HTTP calls to inventory                               |
| `REINDEX_MERGE_RANGE_PUBLISHER_MAX_POOL_SIZE`  | `30`    | Max thread pool for parallel HTTP calls to inventory                                |
| `EXCHANGE_HTTP_MAX_CONN_PER_ROUTE`             | `50`    | HTTP connection pool size — must be ≥ `REINDEX_MERGE_RANGE_PUBLISHER_MAX_POOL_SIZE` |
| `KAFKA_REINDEX_RECORDS_CONCURRENCY`            | `4`     | Parallel Kafka consumers for the merge (records) topic                              |
