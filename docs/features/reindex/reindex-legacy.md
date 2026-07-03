---
title: Legacy Reindex
---

# Legacy Reindex

## Overview

The legacy reindex endpoint handles entity types that are outside the instance-based reindex pipeline: `authority`, `location`, `linked-data-instance`, `linked-data-work`, and `linked-data-hub`. It triggers a self-contained reindex job for the specified resource type and optionally recreates the underlying index before repopulating it.

## When to use

- After authority records in mod-authority-storage change in bulk
- After location data (campus, institution, library, location) changes
- After linked-data (instance, work, hub) records need reindexing
- After index mapping changes for any of these resource types (`recreateIndex: true`)

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

| Value                  | Description                                                 |
|------------------------|-------------------------------------------------------------|
| `authority`            | Authority records from mod-authority-storage                |
| `location`             | Location hierarchy (institution, campus, library, location) |
| `linked-data-instance` | Linked-data instance records                                |
| `linked-data-work`     | Linked-data work records                                    |
| `linked-data-hub`      | Linked-data hub records                                     |

### Response

```json
{
  "id": "68ec4438-8b93-46df-8c36-232db4f7862e",
  "jobStatus": "In progress",
  "submittedDate": "2024-12-05T10:22:22"
}
```

| Field           | Description                              |
|-----------------|------------------------------------------|
| `id`            | Job identifier                           |
| `jobStatus`     | Current job status (`In progress`, etc.) |
| `submittedDate` | Timestamp when the job was submitted     |

## Notes

- This endpoint does not use the same merge/upload phase tracking as the instance-based reindex. Progress is reflected in the returned job object rather than `GET /search/index/instance-records/reindex/status`.
- `recreateIndex: true` should be used whenever index mappings have changed. Without it, new field mappings may not be applied and reindexed data may not be searchable correctly.
- The `location` resource type handles the entire location hierarchy in a single job.
