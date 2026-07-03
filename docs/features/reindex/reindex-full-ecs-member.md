---
title: Full Reindex — ECS Member Tenant
---

# Full Reindex — ECS Member Tenant

## Overview

In a consortium deployment, all member tenant records are stored in the **central tenant's** OpenSearch index — there are no separate per-member indices. The member tenant reindex fetches records belonging to a specific member from mod-inventory-storage and goes through a dedicated **staging phase** before the data is written to the central index. This staging phase is tracked separately from the standard merge/upload phases via the `STAGING_IN_PROGRESS`, `STAGING_COMPLETED`, and `STAGING_FAILED` status values.

To reindex a specific member tenant without reindexing the entire consortium, pass the optional `tenantId` field in the full reindex request body. The request must be made in the context of the **central tenant**.

Triggering a full reindex directly on a member tenant is rejected with an error — the member reindex must always be initiated from the central tenant.

## When to use

- After a new consortium member tenant is onboarded and its data needs to be indexed
- When a specific member's data in the central index is corrupted or stale while other members are healthy
- When selective per-member reindexing is needed without triggering a full consortium reindex

## Prerequisites

- The request must use the **central tenant's** `x-okapi-tenant` header
- The target member tenant ID must be valid and already initialized
- Either [PUBLISH](reindex-full-kafka.md) or [EXPORT (S3)](reindex-full-s3.md) mode must be correctly configured

## Step 1: Trigger member reindex from central tenant

```http
POST /search/index/instance-records/reindex/full
x-okapi-tenant: <central-tenant>
Content-Type: application/json

{
  "tenantId": "college"
}
```

| Field           | Type   | Required | Description                                                                                       |
|-----------------|--------|----------|---------------------------------------------------------------------------------------------------|
| `tenantId`      | string | No       | The member tenant to reindex. If omitted, reindexes all consortium members                        |
| `indexSettings` | object | No       | Optional index settings override — see [Full Reindex — Kafka](reindex-full-kafka.md#request-body) |

### Response

`200 OK` — reindex started. No response body.

## Step 2: Monitor progress

```http
GET /search/index/instance-records/reindex/status
x-okapi-tenant: <central-tenant>
```

The `targetTenantId` field in each status item identifies which member tenant the entry belongs to:

```json
[
  {
    "entityType": "instance",
    "status": "MERGE_IN_PROGRESS",
    "targetTenantId": "college",
    "totalMergeRanges": 50,
    "processedMergeRanges": 12,
    "startTimeMerge": "2024-04-01T01:37:34.15755006Z"
  }
]
```

Reindex is complete when all upload-phase entity types show `UPLOAD_COMPLETED` and all staging-only entity types show their terminal staging status.

## Key configuration variables

| Variable                              | Default | Purpose                                                           |
|---------------------------------------|---------|-------------------------------------------------------------------|
| `REINDEX_MIGRATION_WORK_MEM`          | `64MB`  | PostgreSQL `work_mem` for staging migration queries               |
| `REINDEX_MIGRATION_STATEMENT_TIMEOUT` | `0`     | PostgreSQL statement timeout for staging migration (0 = no limit) |

## Constraints

- Full reindex requested directly on a member tenant (i.e., with the member's tenant header and no `tenantId` field) is rejected.
- If `tenantId` is omitted, all consortium members are reindexed. Be aware of the resource impact.
- Simultaneous multi-tenant reindexing is not supported — run one member at a time.
