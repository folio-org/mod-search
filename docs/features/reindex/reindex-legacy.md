---
title: Legacy Reindex
---

# Legacy Reindex

## Overview

The legacy reindex endpoint handles entity types outside the instance-based reindex pipeline. It triggers a self-contained reindex job for `authority` or `location`, and optionally recreates the underlying index before repopulating it. `linked-data-*` types are accepted by the API but are not currently implemented — the call succeeds with no data being reindexed.

> Sequence diagram: [legacy-reindex.png](../../diagrams/legacy-reindex.png) ([PlantUML source](../../diagrams/legacy-reindex.puml)).

## When to use

- After authority records in mod-entities-links change in bulk
- After location data (campus, institution, library, location) changes
- After index mapping changes for `authority` or `location` (`recreateIndex: true`)

## Step 1: Trigger legacy reindex

```http
POST /search/index/inventory/reindex
x-okapi-tenant: <tenant>
Content-Type: application/json

{
  "resourceName": "authority",
  "recreateIndex": true
}
```

| Field           | Type    | Required              | Description                                                                                |
|-----------------|---------|-----------------------|--------------------------------------------------------------------------------------------|
| `resourceName`  | string  | Yes                   | The resource type to reindex — see valid values below                                      |
| `recreateIndex` | boolean | No (default: `false`) | If `true`, drops and recreates the index before reindexing. Required after mapping changes |
| `indexSettings` | object  | No                    | Optional index settings override (numberOfShards, numberOfReplicas, refreshInterval)       |

### Valid `resourceName` values

| Value                  | Description                                                         |
|------------------------|---------------------------------------------------------------------|
| `authority`            | Authority records from mod-entities-links (async)                   |
| `location`             | Location hierarchy (institution, campus, library, location)         |
| `linked-data-instance` | Linked-data instance records (**not implemented — see note below**) |
| `linked-data-work`     | Linked-data work records (**not implemented — see note below**)     |
| `linked-data-hub`      | Linked-data hub records (**not implemented — see note below**)      |

### Response

For `authority` (async — delegates to mod-entities-links):

```json
{
  "id": "68ec4438-8b93-46df-8c36-232db4f7862e",
  "jobStatus": "In progress",
  "submittedDate": "2024-12-05T10:22:22"
}
```

For `location` (synchronous — runs entirely in mod-search):

```json
{
  "id": "68ec4438-8b93-46df-8c36-232db4f7862e",
  "jobStatus": "Completed",
  "submittedDate": "2024-12-05T10:22:22"
}
```

| Field           | Description                                             |
|-----------------|---------------------------------------------------------|
| `id`            | Job identifier                                          |
| `jobStatus`     | `"In progress"` (authority) or `"Completed"` (location) |
| `submittedDate` | Timestamp when the job was submitted                    |

## Performance

Legacy reindex does not use the range-based merge/upload pipeline, so its performance profile differs by `resourceName`:

- **`authority`** — asynchronous. mod-search delegates to mod-entities-links, which streams records back through the standard real-time indexing pipeline. Throughput is bounded by that pipeline and the authority dataset size; monitor via the returned job object rather than the reindex status endpoint.
- **`location`** — synchronous. The entire location hierarchy (institution, campus, library, location) is reindexed in a single job that completes before the HTTP call returns. The dataset is small, so this is typically fast. Records are read in batches of `REINDEX_LOCATION_BATCH_SIZE`.

The `indexSettings` override is accepted for both types and is most useful together with `recreateIndex: true` after mapping changes.

### Key tuning variables

| Variable                      | Default | Effect                                           |
|-------------------------------|---------|--------------------------------------------------|
| `REINDEX_LOCATION_BATCH_SIZE` | `1000`  | Records read per batch during `location` reindex |

For the full configuration reference, see the [Configuration Reference](../reindex.md#configuration-reference).

## Notes

- This endpoint does not use the same merge/upload phase tracking as the instance-based reindex. Progress is reflected in the returned job object rather than `GET /search/index/instance-records/reindex/status`.
- `recreateIndex: true` should be used whenever index mappings have changed. Without it, new field mappings may not be applied and reindexed data may not be searchable correctly.
- The `location` resource type handles the entire location hierarchy in a single synchronous job — the response returns `"jobStatus": "Completed"` when the call returns.
- **`linked-data-instance`, `linked-data-work`, `linked-data-hub` are accepted by the schema but not currently implemented.** The endpoint returns a `200 OK` with an empty job object and performs no reindex. `recreateIndex: true` will still drop and recreate the index for these types before returning.

## Related

- [Reindex overview](../reindex.md) — phases, monitoring, and shared configuration for the instance-based reindex
- [Consortium Search](../consortium-search.md) — how `location` data is served across an ECS consortium

