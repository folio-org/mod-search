---
title: Failed Merge Reindex
---

# Failed Merge Reindex

## Overview

When a full reindex's merge phase fails for one or more ranges, those ranges are marked `MERGE_FAILED` in the status table. The failed-merge reindex endpoint retries only those specific ranges without restarting the entire reindex operation. If no failed ranges exist, the call returns immediately with no action taken.

## When to use

- When `GET /search/index/instance-records/reindex/status` shows `MERGE_FAILED` for any entity type
- After identifying and resolving the root cause of the merge failure (e.g. inventory connectivity, Kafka consumer lag, database locking issues)

## Step 1: Confirm failed ranges exist

Check [Monitoring Progress](../reindex.md#monitoring-progress) and look for entries with `"status": "MERGE_FAILED"`. If none are present, there is nothing to retry.

## Step 2: Retry failed merge ranges

```http
POST /search/index/instance-records/reindex/merge/failed
x-okapi-tenant: <tenant>
```

No request body.

### Response

`200 OK` — retry started (or returned immediately if no failed ranges existed). No response body.

## Step 3: Monitor progress

Monitor via [Monitoring Progress](../reindex.md#monitoring-progress). Watch for `MERGE_FAILED` entries to transition to `MERGE_IN_PROGRESS` then `MERGE_COMPLETED`. Once all merges complete, the upload phase starts automatically.

If ranges fail again, investigate the underlying cause before retrying — repeated failures indicate a persistent infrastructure issue.

## Performance

This operation retries **only the ranges in `MERGE_FAILED`**, so its duration scales with the number of failed ranges rather than the full dataset — it is normally a small fraction of a full reindex. It reuses the same merge-phase machinery as a PUBLISH full reindex, so the same tuning variables apply (`REINDEX_MERGE_RANGE_PUBLISHER_*`, `KAFKA_REINDEX_RECORDS_CONCURRENCY`, `EXCHANGE_HTTP_MAX_CONN_PER_ROUTE`) — see [Full Reindex — Kafka › Performance](reindex-full-kafka.md#performance) and the full [Configuration Reference](../reindex.md#configuration-reference).

Once all failed ranges recover to `MERGE_COMPLETED`, the upload phase starts automatically; upload-phase throughput is governed by the variables in [Upload-Phase Reindex › Performance](reindex-upload.md#performance).

## Notes

- Only ranges in `MERGE_FAILED` state are retried — ranges already in `MERGE_COMPLETED` or later states are not re-processed.
- If the merge failure is systemic (e.g. inventory is unreachable for an extended period), consider restarting the full reindex after the issue is resolved rather than retrying repeatedly.

## Related

- [Full Reindex — Kafka](reindex-full-kafka.md) — the operation that produces `MERGE_FAILED` ranges
- [Reindex overview › Error Behavior](../reindex.md#error-behavior) — how ranges enter `MERGE_FAILED`
- [Reindex overview](../reindex.md) — phases, monitoring, and status values

