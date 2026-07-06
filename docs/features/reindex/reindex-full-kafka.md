---
title: Full Reindex — Kafka (PUBLISH mode)
---

# Full Reindex — Kafka (PUBLISH mode)

## Overview

PUBLISH mode is the default full reindex strategy (`REINDEX_TYPE=PUBLISH`). mod-search's merge range publisher makes parallel HTTP calls to mod-inventory-storage, which responds by streaming records to Kafka. mod-search consumes those records and stages them in PostgreSQL. Once all entity types have been staged (merge phase complete), the upload phase reads staged records in ranges and bulk-writes them to OpenSearch.

## When to use

- After OpenSearch index mapping changes that require a full rebuild
- After MARC-to-FOLIO mapping changes (the inventory representation of records changed)
- After suspected index corruption
- After a major data migration in mod-inventory-storage
- After running mod-marc-migrations with `publishEvents=false` — mod-search received no change events, so the index must be rebuilt from source
- When standing up a new environment from scratch

Do **not** use when:
- OpenSearch was upgraded or its indices deleted, but inventory data is current → use [Upload-Phase Reindex](reindex-upload.md) instead
- Reindexing a specific ECS consortium member tenant → use [Full Reindex — ECS Member Tenant](reindex-full-ecs-member.md) instead
- Reindexing authorities, locations, or linked-data records → use [Legacy Reindex](reindex-legacy.md) instead

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

> To speed up indexing on large datasets, set `numberOfReplicas` to `0` and `refreshInterval` to `-1` in the request above, then restore production values after completion — see [Performance](#performance) below.

### Response

`200 OK` — reindex started. No response body.

## Step 2: Monitor progress

Monitor via `GET /search/index/instance-records/reindex/status` — see [Monitoring Progress](../reindex.md#monitoring-progress).

During merge phase, `instance`, `holdings`, and `item` show `MERGE_IN_PROGRESS`. Reindex is complete when `instance` shows `UPLOAD_COMPLETED` and `holdings`, `item` show `MERGE_COMPLETED`.

If any entity type enters `MERGE_FAILED`, the merge phase halted for those ranges. Retry them without restarting the whole reindex — see [Failed Merge Reindex](reindex-failed-merge.md).

## Performance

A PUBLISH full reindex has two sequential bottlenecks:

1. **Merge phase** — bounded by how fast mod-inventory-storage can stream records over HTTP and how fast mod-search can stage them. Scale up the merge range publisher pool and the records consumer concurrency to push more parallel work, keeping the HTTP connection pool at or above the publisher pool size.
2. **Upload phase** — bounded by OpenSearch bulk-write throughput. Disabling replicas and refresh during indexing (below) and adding data nodes has the largest impact here.

### Speeding up indexing

Pass `indexSettings` in the [Step 1](#step-1-trigger-full-reindex) request to remove replica-write and refresh overhead while indexing:

```json
{
  "indexSettings": {
    "numberOfReplicas": 0,
    "refreshInterval": -1
  }
}
```

Restore production values once the reindex completes — see [Restoring Index Settings After Reindex](../reindex.md#restoring-index-settings-after-reindex).

### Scaling consumers and partitions

Record staging and the upload phase are Kafka-consumer-bound, so their throughput scales with the number of mod-search tasks (container instances): the **effective consumer count is `tasks × concurrency`**, capped by the topic's partition count — any consumers beyond the partition count stay idle. To go faster, raise the consumer concurrency and the topic's partition count together.

> The merge **publish** step is the exception. It runs only on the single mod-search instance that received the trigger, so adding tasks does **not** make it publish faster — merge-publish fan-out is governed by `REINDEX_MERGE_RANGE_PUBLISHER_CORE_POOL_SIZE` / `REINDEX_MERGE_RANGE_PUBLISHER_MAX_POOL_SIZE` on that one instance (plus `EXCHANGE_HTTP_MAX_CONN_PER_ROUTE`, which must be ≥ the max pool size).

A PUBLISH full reindex is bounded by two reindex topics:

| Topic                                 | Consumer concurrency                              | Partition count                                                              |
|---------------------------------------|---------------------------------------------------|------------------------------------------------------------------------------|
| `inventory.reindex-records` (merge)   | `KAFKA_REINDEX_RECORDS_CONCURRENCY` (default `4`) | Owned by mod-inventory-storage (the producer)                                |
| `search.reindex.range-index` (upload) | `KAFKA_REINDEX_RANGE_INDEX_CONCURRENCY` (default `8`) | `KAFKA_REINDEX_RANGE_INDEX_TOPIC_PARTITIONS` (default `16`) — at creation only |

**Adjusting partitions.** These topics are created once and are **not** repartitioned when a partition-count variable changes, so on an existing environment you must raise the partition count manually with Kafka admin tooling before reindexing. For example, to give the upload topic 24 partitions to match 4 tasks running `KAFKA_REINDEX_RANGE_INDEX_CONCURRENCY=6` (4 × 6 = 24):

```
kafka-topics --bootstrap-server <broker> --alter \
  --topic <env>.<tenant>.search.reindex.range-index --partitions 24
```

`KAFKA_REINDEX_RANGE_INDEX_TOPIC_PARTITIONS` only takes effect when mod-search first creates the topic. The `inventory.reindex-records` topic is owned by mod-inventory-storage, so adjust its partition count on that module's side.

### mod-inventory-storage configuration

The merge phase does its real work in mod-inventory-storage — mod-search calls it over HTTP, and it streams the requested records into Kafka — so it must be scaled alongside mod-search or it becomes the merge bottleneck. Two settings control its parallelism:

- **Task count** — the number of mod-inventory-storage tasks (container instances).
- **Vert.x verticle instances** — the number of verticle instances per task, set via the container command. In an AWS task definition, add it to the `containerDefinition` at the same level as `image`, `name`, and `cpu`:

  ```json
  "command": ["-instances", "4"]
  ```

Size these together so that **`(mod-inventory-storage task count) × (verticle -instances)` stays lower than mod-search's `EXCHANGE_HTTP_MAX_CONN_PER_ROUTE`.** The single merge-publishing mod-search instance opens up to `EXCHANGE_HTTP_MAX_CONN_PER_ROUTE` concurrent connections to mod-inventory-storage; keeping the total verticle count under that ceiling ensures every verticle can be driven without exhausting the connection pool.

### Key tuning variables

**Merge phase:**

| Variable                                       | Default | Effect                                                                              |
|------------------------------------------------|---------|-------------------------------------------------------------------------------------|
| `REINDEX_MERGE_RANGE_PUBLISHER_CORE_POOL_SIZE` | `30`    | Parallel HTTP calls to mod-inventory-storage                                        |
| `REINDEX_MERGE_RANGE_PUBLISHER_MAX_POOL_SIZE`  | `30`    | Maximum parallel HTTP calls to mod-inventory-storage                                |
| `EXCHANGE_HTTP_MAX_CONN_PER_ROUTE`             | `50`    | HTTP connection pool size — must be ≥ `REINDEX_MERGE_RANGE_PUBLISHER_MAX_POOL_SIZE` |
| `KAFKA_REINDEX_RECORDS_CONCURRENCY`            | `4`     | Parallel Kafka consumers processing records                                         |
| `REINDEX_MERGE_RANGE_SIZE`                     | `500`   | Records per merge range                                                             |

**Upload phase:**

| Variable                                | Default | Effect                                     |
|-----------------------------------------|---------|--------------------------------------------|
| `KAFKA_REINDEX_RANGE_INDEX_CONCURRENCY` | `8`     | Parallel Kafka consumers for upload ranges |
| `REINDEX_UPLOAD_RANGE_SIZE`             | `1000`  | Records per upload range                   |

For every variable including retry tuning, see the [Configuration Reference](../reindex.md#configuration-reference). The shared PostgreSQL pool settings `DB_MAXSHAREDPOOLSIZE` and `DB_QUERYTIMEOUT` also apply — see [Shared database settings](../reindex.md#shared-database-settings).

## Related

- [Failed Merge Reindex](reindex-failed-merge.md) — retry ranges left in `MERGE_FAILED` after this reindex
- [Upload-Phase Reindex](reindex-upload.md) — re-push already-staged records without re-merging from inventory
- [Full Reindex — ECS Member Tenant](reindex-full-ecs-member.md) — reindex a single consortium member
- [Reindex overview](../reindex.md) — phases, monitoring, status values, and shared configuration
