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

Check the status before retrying:

```http
GET /search/index/instance-records/reindex/status
x-okapi-tenant: <tenant>
```

Look for entries with `"status": "MERGE_FAILED"`. If none are present, there is nothing to retry.

## Step 2: Retry failed merge ranges

```http
POST /search/index/instance-records/reindex/merge/failed
x-okapi-tenant: <tenant>
```

No request body.

### Response

`200 OK` — retry started (or returned immediately if no failed ranges existed). No response body.

## Step 3: Monitor progress

```http
GET /search/index/instance-records/reindex/status
x-okapi-tenant: <tenant>
```

Watch for `MERGE_FAILED` entries to transition back to `MERGE_IN_PROGRESS` and then `MERGE_COMPLETED`. Once merge completes, the upload phase starts automatically.

If ranges fail again, investigate the underlying cause before retrying. Repeated failures indicate a persistent infrastructure issue rather than a transient one.

## Notes

- Only ranges in `MERGE_FAILED` state are retried — ranges already in `MERGE_COMPLETED` or later states are not re-processed.
- If the merge failure is systemic (e.g. inventory is unreachable for an extended period), consider restarting the full reindex after the issue is resolved rather than retrying repeatedly.
