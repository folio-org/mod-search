---
title: Upload-Phase Reindex
---

# Upload-Phase Reindex

## Overview

The upload-phase reindex skips the merge phase entirely. It reads records already staged in PostgreSQL and re-pushes them to OpenSearch in ranges. **It does not communicate with mod-inventory-storage** — no Kafka messages are sent to inventory, and no new data is fetched. The data source is exclusively the local PostgreSQL staging tables populated by a previous full reindex.

## When to use

- After an OpenSearch cluster upgrade or index deletion — inventory data is current in PostgreSQL, only OpenSearch needs to be rebuilt
- After changing index configuration (e.g. shard count, analyzers) — drop and recreate the index, then re-upload from the existing resource tables in PostgreSQL
- After a failed upload phase from a previous full reindex — the merge data is already there, no need to re-merge

Do **not** use when:
- The PostgreSQL staging tables are empty (no full reindex has ever run) — there is nothing to upload
- You need fresh data from inventory — use [Full Reindex — Kafka](reindex-full-kafka.md) or [Full Reindex — S3](reindex-full-s3.md) instead
- A merge is currently in progress or in `MERGE_FAILED` state for the requested entity types — the request will be rejected

## Prerequisites

- A full reindex (merge phase) must have completed successfully at least once
- No merge in progress or in `MERGE_FAILED` state for the requested entity types

## Step 1: Trigger upload reindex

```http
POST /search/index/instance-records/reindex/upload
x-okapi-tenant: <tenant>
Content-Type: application/json

{
  "entityTypes": ["instance", "subject", "contributor", "classification", "call-number"]
}
```

| Field           | Type   | Required    | Description                                    |
|-----------------|--------|-------------|------------------------------------------------|
| `entityTypes`   | array  | Yes (min 1) | Entity types to upload. See valid values below |
| `indexSettings` | object | No          | Optional index settings override               |

### Valid `entityTypes` values

| Value            | Description                |
|------------------|----------------------------|
| `instance`       | Instance records           |
| `subject`        | Subject browse data        |
| `contributor`    | Contributor browse data    |
| `classification` | Classification browse data |
| `call-number`    | Call number browse data    |

### Response

`200 OK` — upload reindex started. No response body.

## Step 2: Monitor progress

```http
GET /search/index/instance-records/reindex/status
x-okapi-tenant: <tenant>
```

```json
[
  {
    "entityType": "instance",
    "status": "UPLOAD_IN_PROGRESS",
    "totalUploadRanges": 200,
    "processedUploadRanges": 75,
    "startTimeUpload": "2024-04-01T01:37:34.15755006Z"
  },
  {
    "entityType": "contributor",
    "status": "UPLOAD_COMPLETED",
    "totalUploadRanges": 3,
    "processedUploadRanges": 3,
    "startTimeUpload": "2024-04-01T01:37:34.15755006Z",
    "endTimeUpload": "2024-04-01T01:37:35.15755006Z"
  }
]
```

Upload is complete when all requested entity types show `UPLOAD_COMPLETED`.

## Key configuration variables

| Variable                                | Default | Purpose                                                   |
|-----------------------------------------|---------|-----------------------------------------------------------|
| `REINDEX_UPLOAD_RANGE_SIZE`             | `1000`  | Records per upload range                                  |
| `REINDEX_UPLOAD_RANGE_LEVEL`            | `3`     | Range tree depth for upload phase                         |
| `KAFKA_REINDEX_RANGE_INDEX_CONCURRENCY` | `8`     | Parallel Kafka consumers for the upload range-index topic |
