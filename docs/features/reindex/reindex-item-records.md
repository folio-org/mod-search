---
title: Item Records Reindex
---

# Item Records Reindex

## Overview

Rebuilds only the `item` index, used by `GET /search/inventory/items`, directly from inventory. Item records go straight to OpenSearch and no item data is stored in PostgreSQL. No other index and no regular reindex state is touched.

It is meant to run after a release that changed the index, and only when needed: the status endpoint reports whether a reindex is required for each tenant.

> Sequence diagram: [item-records-reindex.png](../../diagrams/item-records-reindex.png) ([PlantUML source](../../diagrams/item-records-reindex.puml)). States: [Index Rebuild States](../reindex.md#index-rebuild-states).

## Run it

```http
POST /search/index/item-records/reindex/full
x-okapi-tenant: <central tenant>
Content-Type: application/json

{ "indexSettings": { "numberOfReplicas": 0, "refreshInterval": -1 } }
```

| Field           | Description                                                                                  |
|-----------------|----------------------------------------------------------------------------------------------|
| `tenantId`      | Optional consortium tenant to reload. Without it, the whole index is dropped and rebuilt.    |
| `indexSettings` | Optional settings of the recreated index, restore them with `PUT /search/index/settings`.    |

A single tenant rerun deletes and reloads only that tenant's documents, for example for a member added after the deploy. It doesn't recreate the index, so it is rejected until the index is on the current version.

The request is rejected with `400 Bad Request` when `ITEM_INDEX_ENABLED` is `false`, it comes from a consortium member tenant, the target tenant is not valid, or a run is already active for any of the tenants. It fails with `500 Internal Server Error` when the index can't be prepared.

## Monitor it

`GET /search/index/item-records/reindex/status` returns one entry per tenant.

| Status        | Meaning                                                                                         |
|---------------|-------------------------------------------------------------------------------------------------|
| `NOT_STARTED` | No run has been executed for the tenant                                                         |
| `IN_PROGRESS` | Records are being requested and indexed                                                         |
| `FINALIZING`  | All ranges succeeded, items deleted during the run are being removed and the index is refreshed |
| `COMPLETED`   | Finished                                                                                        |
| `FAILED`      | A range or the finalization failed, see `failCause`                                             |

`reindexRequired` is `true` until the tenant has a completed run for the current index mappings, analysis settings and data version. `blockedBy` lists indexes that have to be rebuilt first.

## Retry

`POST /search/index/item-records/reindex/failed` requests all unfinished ranges of failed runs again, and completes runs that stopped during finalization. A new run can also be started at any time after a failure: records of the previous run are ignored.

## Consistency

Item events keep updating the index during a run.

- Documents are only created, so an older reindex record never overwrites a document written by an event.
- Items deleted during a run are recorded in a temporary list and removed from the index when all ranges of the tenant have succeeded.
- Searches return partial results until the run finishes.

## Permissions

| Endpoint                                          | Permission                                       |
|---------------------------------------------------|--------------------------------------------------|
| `POST /search/index/item-records/reindex/full`    | `search.index.item-records.reindex.full.post`    |
| `POST /search/index/item-records/reindex/failed`  | `search.index.item-records.reindex.failed.post`  |
| `GET /search/index/item-records/reindex/status`   | `search.index.item-records.reindex.status.get`   |
