---
title: Call Number Records Reindex
---

# Call Number Records Reindex

## Overview

Rebuilds only the `instance_call_number` index, which backs call number browse. Documents are built from the `item` index, with no calls to inventory and no use of the `call_number` tables.

The `item` index has to be up to date first, so run the [item records reindex](reindex-item-records.md) before it when both are required.

> Sequence diagrams: [call-number-records-reindex.png](../../diagrams/call-number-records-reindex.png) ([PlantUML source](../../diagrams/call-number-records-reindex.puml)) and the runtime [call-number-refresh.png](../../diagrams/call-number-refresh.png) ([PlantUML source](../../diagrams/call-number-refresh.puml)). States: [Index Rebuild States](../reindex.md#index-rebuild-states).

## Run it

```http
POST /search/index/call-number-records/reindex/full
x-okapi-tenant: <central tenant>
Content-Type: application/json

{ "tenantId": "<optional consortium tenant>" }
```

| Field           | Description                                                                                         |
|-----------------|-----------------------------------------------------------------------------------------------------|
| `tenantId`      | Optional tenant to recompute. Without it, the whole index is dropped and rebuilt.                   |
| `indexSettings` | Optional settings of the recreated index, restore them with `PUT /search/index/settings`.           |

Call number documents combine items of several tenants, so a single tenant rerun doesn't delete anything first. It recomputes every call number that the tenant's items or existing documents refer to, overwriting each document, or deleting it if no items remain.

Both kinds of runs split call number ids into the same number of ranges, set by `REINDEX_UPLOAD_RANGE_LEVEL` (`16^level` ranges, 4096 by default), because a single tenant can hold most of the call numbers. A range finds only the keys in it, so ranges without keys of the tenant are cheap.

The request is rejected with `400 Bad Request` when:

- browsing call numbers is disabled for the tenant;
- it comes from a consortium member tenant, or the target tenant is not valid;
- the `item` index isn't up to date for a tenant in scope: never reindexed, outdated, failed or in progress;
- a call number run is already active;
- it is a single tenant rerun and the call number index isn't on the current version.

An item records reindex is rejected while a call number run is active.

## Monitor and retry

`GET /search/index/call-number-records/reindex/status` returns one entry per tenant, with `reindexRequired` and `blockedBy`. `POST /search/index/call-number-records/reindex/failed` resumes a failed run. A new run can also be started at any time after a failure.

## Keeping documents up to date

With `CALL_NUMBER_INDEX_FROM_ITEMS_ENABLED=true`, an item change queues its old and new call numbers, and a scheduled job rebuilds those documents from the `item` index every `CALL_NUMBER_REFRESH_DELAY_MS`. The queue holds only ids.

Changes of a tenant wait while its `item` index isn't up to date or a call number run is active for it, and are applied afterwards.

The flag is off by default. While it's off, the call number tables keep publishing documents as before. While it's on, those tables are still populated but no longer publish documents, so turning it off is the rollback.

## Permissions

| Endpoint                                                 | Permission                                              |
|----------------------------------------------------------|---------------------------------------------------------|
| `POST /search/index/call-number-records/reindex/full`    | `search.index.call-number-records.reindex.full.post`    |
| `POST /search/index/call-number-records/reindex/failed`  | `search.index.call-number-records.reindex.failed.post`  |
| `GET /search/index/call-number-records/reindex/status`   | `search.index.call-number-records.reindex.status.get`   |
