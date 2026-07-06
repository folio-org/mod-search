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

Monitor via `GET /search/index/instance-records/reindex/status` using the **central tenant** header — see [Monitoring Progress](../reindex.md#monitoring-progress). The `targetTenantId` field in each status item identifies the member tenant being reindexed. Reindex is complete when all upload-phase entity types show `UPLOAD_COMPLETED` and all staging-phase entity types show `STAGING_COMPLETED`.

## Performance

A member tenant reindex shares the merge bottleneck of its underlying mode ([PUBLISH](reindex-full-kafka.md#performance) or [EXPORT](reindex-full-s3.md)) and adds a [**staging phase**](../reindex.md#reindex-phases) that promotes the member's merged records into the central tenant's index data. Two factors dominate:

- **Fetching the member's records** — same characteristics as a standard full reindex merge for the configured mode.
- **Staging into the central index** — governed by the PostgreSQL migration settings below. On large members, raising `REINDEX_MIGRATION_WORK_MEM` reduces spill-to-disk during staging queries.

The `indexSettings` override applies here too (pass it in the [Step 1](#step-1-trigger-member-reindex-from-central-tenant) request) — restore production values afterwards via [Restoring Index Settings After Reindex](../reindex.md#restoring-index-settings-after-reindex).

### Key tuning variables

| Variable                              | Default | Effect                                                            |
|---------------------------------------|---------|-------------------------------------------------------------------|
| `REINDEX_MIGRATION_WORK_MEM`          | `64MB`  | PostgreSQL `work_mem` for staging migration queries               |
| `REINDEX_MIGRATION_STATEMENT_TIMEOUT` | `0`     | PostgreSQL statement timeout for staging migration (0 = no limit) |

The underlying mode's merge and upload tuning variables also apply — see [Full Reindex — Kafka › Performance](reindex-full-kafka.md#performance) (including [Scaling consumers and partitions](reindex-full-kafka.md#scaling-consumers-and-partitions) and [mod-inventory-storage configuration](reindex-full-kafka.md#mod-inventory-storage-configuration)), the shared [database settings](../reindex.md#shared-database-settings) (`DB_MAXSHAREDPOOLSIZE`, `DB_QUERYTIMEOUT`), and the full [Configuration Reference](../reindex.md#configuration-reference).

## Constraints

- Full reindex requested directly on a member tenant (i.e., with the member's tenant header and no `tenantId` field) is rejected.
- If `tenantId` is omitted, all consortium members are reindexed. Be aware of the resource impact.
- Simultaneous multi-tenant reindexing is not supported — run one member at a time.

## Related

- [Full Reindex — Kafka](reindex-full-kafka.md) / [Full Reindex — S3](reindex-full-s3.md) — the underlying merge modes a member reindex builds on
- [Consortium Search](../consortium-search.md) — how member records are served from the central index
- [Reindex overview](../reindex.md) — phases, monitoring, status values, and shared configuration

