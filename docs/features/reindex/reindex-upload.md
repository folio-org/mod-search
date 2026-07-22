---
title: Upload-Phase Reindex
---

# Upload-Phase Reindex

## Overview

The upload-phase reindex skips the merge phase entirely. It reads records already staged in PostgreSQL and re-pushes them to OpenSearch in ranges. **It does not communicate with mod-inventory-storage** — no Kafka messages are sent to inventory, and no new data is fetched. The data source is exclusively the local PostgreSQL staging tables populated by a previous full reindex.

> Sequence diagram: [upload-phase.png](../../diagrams/upload-phase.png) ([PlantUML source](../../diagrams/upload.puml)).

## When to use

- After an OpenSearch cluster upgrade or index deletion — inventory data is current in PostgreSQL, only OpenSearch needs to be rebuilt
- After changing index configuration (e.g. shard count, analyzers) — drop and recreate the index, then re-upload from the existing resource tables in PostgreSQL
- After a failed upload phase from a previous full reindex — the merge data is already there, no need to re-merge

Do **not** use when:
- The PostgreSQL staging tables are empty (no full reindex has ever run) — there is nothing to upload
- You need fresh data from inventory — use [Full Reindex — Kafka](reindex-full-kafka.md) or [Full Reindex — S3](reindex-full-s3.md) instead
- A merge is currently in progress or in `MERGE_FAILED` state for the requested entity types — the request will be rejected

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

Monitor via `GET /search/index/instance-records/reindex/status` — see [Monitoring Progress](../reindex.md#monitoring-progress). Upload is complete when all requested entity types show `UPLOAD_COMPLETED`.

## Performance

An upload-phase reindex has no merge phase and no communication with mod-inventory-storage, so it is typically much faster than a full reindex on the same dataset — the merge phase (usually the longest part of a full reindex) is skipped entirely. Two factors bound its throughput:

1. **OpenSearch bulk-write throughput** — the primary limit. Disabling replicas and refresh during indexing (below) and adding data nodes has the largest impact here.
2. **PostgreSQL read connections** — each upload range reads its staged records from PostgreSQL, so a small connection pool can throttle parallel range processing. Raise `DB_MAXSHAREDPOOLSIZE` if the pool is saturated. Very large ranges can also exceed the statement timeout — raise `DB_QUERYTIMEOUT` or lower `REINDEX_UPLOAD_RANGE_SIZE` if range reads time out. Both come from `folio-spring-base`; see [Shared database settings](../reindex.md#shared-database-settings).

### Scaling consumers and partitions

The upload phase is Kafka-consumer-bound, so throughput scales with the number of mod-search tasks (container instances): the **effective consumer count is `tasks × KAFKA_REINDEX_RANGE_INDEX_CONCURRENCY`**, capped by the partition count of the `search.reindex.range-index` topic — any consumers beyond that stay idle. Raise the concurrency and the partition count together.

The topic's partitions come from `KAFKA_REINDEX_RANGE_INDEX_TOPIC_PARTITIONS` (default `16`) **at creation only**. The topic is created once and is not repartitioned when the variable changes, so on an existing environment raise the partition count manually with Kafka admin tooling before reindexing — for example, to match 4 tasks running `KAFKA_REINDEX_RANGE_INDEX_CONCURRENCY=6` (4 × 6 = 24):

```
kafka-topics --bootstrap-server <broker> --alter \
  --topic <env>.<tenant>.search.reindex.range-index --partitions 24
```

### Speeding up indexing

Pass `indexSettings` in the [Step 1](#step-1-trigger-upload-reindex) request to remove replica-write and refresh overhead while indexing:

```json
{
  "entityTypes": ["instance", "subject", "contributor", "classification", "call-number"],
  "indexSettings": {
    "numberOfReplicas": 0,
    "refreshInterval": -1
  }
}
```

Restore production values once the upload completes — see [Restoring Index Settings After Reindex](../reindex.md#restoring-index-settings-after-reindex).

### Key tuning variables

| Variable                                | Default | Effect                                                                    |
|-----------------------------------------|---------|---------------------------------------------------------------------------|
| `REINDEX_UPLOAD_RANGE_SIZE`             | `1000`  | Records per upload range. Lower it if large ranges hit `DB_QUERYTIMEOUT`  |
| `REINDEX_UPLOAD_RANGE_LEVEL`            | `3`     | Range tree depth for the upload phase                                     |
| `KAFKA_REINDEX_RANGE_INDEX_CONCURRENCY` | `8`     | Parallel Kafka consumers for the upload range-index topic                 |
| `DB_MAXSHAREDPOOLSIZE`                  | `10`    | Max DB connection pool size — raise to relieve read-connection contention |
| `DB_QUERYTIMEOUT`                       | `60000` | PostgreSQL statement timeout (ms) — raise if large range reads time out   |

`DB_MAXSHAREDPOOLSIZE` and `DB_QUERYTIMEOUT` are provided by `folio-spring-base` and apply to all DB-bound reindex types. For the full configuration reference, see the [Configuration Reference](../reindex.md#configuration-reference).

## Related

- [Full Reindex — Kafka](reindex-full-kafka.md) — full rebuild that fetches fresh data from inventory (run this if the staging tables are empty)
- [Failed Merge Reindex](reindex-failed-merge.md) — retry failed merge ranges before uploading
- [Reindex overview](../reindex.md) — phases, monitoring, status values, and shared configuration
