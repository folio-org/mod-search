---
title: Item Records Reindex
---

# Item Records Reindex

## Overview

Rebuilds only the `item` index (used by `GET /search/inventory/items`) from inventory. Item records are pulled from inventory with the same merge-range machinery as a full reindex, but the consumers write them straight to the `item` index instead of the PostgreSQL `item` table. No other index, table or status row is changed, so a regular full or ECS member reindex stays as it is and does not rebuild the item index.

Use it when the item index must be rebuilt after index recreation, a mapping change or data loss; the index is otherwise filled only by inventory item events.

Available only when `ITEM_INDEX_ENABLED=true`; otherwise the request is rejected with `400`. It works in both `REINDEX_TYPE=PUBLISH` (Kafka records) and `REINDEX_TYPE=EXPORT` (S3 files) modes.

## What it changes and what it does not

| Changed                                                                              | Not changed                                                                    |
|--------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| The `item` index is dropped and recreated                                            | `instance`, subject, contributor, classification and call number indexes        |
| `merge_range` rows of entity type `item` are replaced                                | `merge_range` rows of other entity types                                       |
| The `ITEM` row of `reindex_status` is reset (`item_only` flag set)                   | Status rows of other entity types                                              |
| The `reindex_status` trigger is bound to the consortium member variant               | The PostgreSQL `item` and `call_number` tables, `sub_resources_lock`           |

The status trigger variant has the same status transitions as the default one but does not touch `sub_resources_lock`, so finishing an items-only run neither advances the item watermark nor releases a lock held by the background scheduler. The next full reindex binds the default trigger again.

## Step 1: Check preconditions

The request is rejected with `400 Bad Request` when:

- the tenant is an ECS member tenant (run it from the central tenant; member-scoped runs are not supported);
- `ITEM_INDEX_ENABLED` is `false`;
- any entity type is `MERGE_IN_PROGRESS`, `UPLOAD_IN_PROGRESS`, `STAGING_IN_PROGRESS`, or in a failed status, or `instance` is `MERGE_COMPLETED` or `STAGING_COMPLETED` (between merge and upload phases). An `item` row that failed in a previous items-only run does not block a restart, unless ranges of that run are still being processed: some ranges have no final status and there was range activity in the last 10 minutes. Retry only the failed ranges, or wait for the run to go quiet.

If the `item` index can't be dropped or created, the request fails with `500 Internal Server Error` and the `item` status becomes `MERGE_FAILED`.

## Step 2: Start the reindex

```http
POST /search/index/item-records/reindex/full
x-okapi-tenant: <tenant>
Content-Type: application/json

{
  "indexSettings": {
    "numberOfShards": 1,
    "numberOfReplicas": 1,
    "refreshInterval": 1
  }
}
```

The body and `indexSettings` are optional. `200 OK` means the item index was recreated and merge ranges are being published to inventory.

In a consortium, ranges are created for the central tenant and every member tenant. Each document gets its own `tenantId`, the id `<itemId>|<tenant>` and the `shared` flag of its instance, exactly as for event-indexed items.

## Step 3: Monitor progress

Use `GET /search/index/item-records/reindex/status`. It returns a one-element list with the `item` status: `MERGE_IN_PROGRESS` while ranges are processed, `MERGE_COMPLETED` when done, `MERGE_FAILED` if ranges failed. There is no upload phase. The list is empty if no items-only reindex has run since the last full reindex, because a full reindex resets the status rows. It requires the `search.index.item-records.reindex.status.get` permission.

## Step 4: Recover failed ranges

If the `item` entry is `MERGE_FAILED` (for example OpenSearch rejected a document), fix the cause and run:

```http
POST /search/index/item-records/reindex/merge/failed
x-okapi-tenant: <tenant>
```

Only failed `item` ranges are republished; ranges of other entity types are ignored. Documents of the range that were already written are kept. If the ranges could not be created at all, restart with `reindex/full`.

## Behavior and limitations

- Documents are only created, never overwritten. The index was just recreated, so an existing document was written by an event during the run and is newer than the reindex record; version conflicts (`409`) are not failures. This also makes Kafka redelivery harmless.
- A batch is retried with `itemIndexingRetryTemplate` and a persistent failure fails the range. There is no one-by-one fallback and nothing is stored in `failed_indexing_event`.
- Searches return partial results until the run finishes, as with any recreate-based reindex.
- An item deleted in inventory while its older record is still in flight can be indexed again, because a delete leaves nothing for `create` to conflict with. This matches existing reindex behavior.
- While an items-only run is in progress or failed, `reindex/upload` is rejected only for requests that include `call-number`, because call numbers are derived from items. Uploads of the other entity types are not affected.
- The `metadata` dates of reindex records are inventory's raw JSON, while events are serialized from inventory's POJO. The item index maps them as `date` (`epoch_millis`, `strict_date_optional_time_nanos`, `strict_date_optional_time`), which accepts offset and `Z` forms with millisecond or microsecond precision, so both produce equivalent documents.

## Configuration

The same settings as the full reindex apply (`REINDEX_TYPE`, `REINDEX_MERGE_RANGE_SIZE`, `REINDEX_MERGE_RANGE_PUBLISHER_*`, `KAFKA_REINDEX_RECORDS_CONCURRENCY`, `REINDEX_MERGE_EXPORT_BATCH_SIZE`); see the [Configuration Reference](../reindex.md#configuration-reference). Document indexing retries use the Kafka retry settings (`KAFKA_RETRY_INTERVAL_MS`, `KAFKA_RETRY_DELIVERY_ATTEMPTS`).

## Permissions

| Endpoint                                              | Permission                                         |
|-------------------------------------------------------|----------------------------------------------------|
| `POST /search/index/item-records/reindex/full`         | `search.index.item-records.reindex.full.post`       |
| `POST /search/index/item-records/reindex/merge/failed` | `search.index.item-records.reindex.merge.failed.post` |
| `GET /search/index/item-records/reindex/status`        | `search.index.item-records.reindex.status.get`      |
