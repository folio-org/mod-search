---
feature_id: instance-search
title: Instance Search
updated: 2026-05-22
---

# Instance Search

## What it does
Allows callers to search bibliographic instance records using CQL queries and receive paginated results. Supports optional field expansion and selective field inclusion. Faceted counts for instance fields are available via the shared facets endpoint.

## Why it exists
Libraries need full-text and fielded search over their bibliographic catalogue. This feature is the primary entry point for OPAC and staff catalogue search over instance records held in Elasticsearch.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| GET | /search/instances | Search instances by CQL query |
| GET | /search/{recordType}/facets | Get facet counts for instance fields |
| POST | /search/resources/jobs | Create an async job to stream matching instance IDs |
| GET | /search/resources/jobs/{jobId} | Get status of a streaming IDs job |
| GET | /search/resources/jobs/{jobId}/ids | Stream the result IDs of a completed job |

## Business rules and constraints

### Query
- Queries must be valid CQL. Malformed queries return `400 Bad Request`.
- `limit` and `offset` control pagination of results.
- `expandAll` controls whether nested sub-resources are expanded in the response.
- `includeFields` limits which fields are returned in each result.

### Resource ID streaming jobs
- Jobs expire after `folio.stream-ids.job-expiration-days` days; expired jobs are purged daily at midnight.
- Maximum IDs returned in a single streaming job is controlled by `MAX_SEARCH_BATCH_REQUEST_IDS_COUNT`.

## Error behavior
- `400 Bad Request` — invalid CQL syntax or missing required parameters.
- `500 Internal Server Error` — Elasticsearch or internal failure.

## Configuration
| Variable | Purpose |
|----------|---------|
| `MAX_SEARCH_BATCH_REQUEST_IDS_COUNT` | Maximum number of IDs returned in a single streaming job |
| `SCROLL_QUERY_SIZE` | Page size used when scrolling IDs from Elasticsearch |
| `STREAM_ID_RETRY_INTERVAL_MS` | Retry interval for ID scroll failures |
| `STREAM_ID_RETRY_ATTEMPTS` | Number of retry attempts for ID scroll failures |
| `STREAM_ID_JOB_EXPIRATION_DAYS` | Days before an inactive streaming job is deleted |
| `SEARCH_QUERY_TIMEOUT` | Elasticsearch query timeout |

## Dependencies and interactions
- Depends on Elasticsearch/OpenSearch for query execution (`ELASTICSEARCH_URL`).
- Scheduled job (`0 0 0 * * *`) purges expired resource ID jobs daily.
