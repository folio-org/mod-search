---
feature_id: authority-search
title: Authority Search
updated: 2026-05-22
---

# Authority Search

## What it does
Allows callers to search authority records using CQL queries and receive paginated results. Optionally returns a count of bibliographic titles linked to each authority heading. Supports selective field inclusion.

## Why it exists
Cataloguers and authority control workflows need to locate and verify authority headings. This feature exposes the authority index to support heading lookup, deduplication, and linking from bibliographic records.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| GET | /search/authorities | Search authority records by CQL query |
| GET | /search/{recordType}/facets | Get facet counts for authority fields |

## Business rules and constraints

### Indexing
- Authority events sourced from consortium shadow copies (identified by `SOURCE_CONSORTIUM_PREFIX`) are filtered out before indexing; only canonical authority records are indexed.

### Query
- Queries must be valid CQL. Malformed queries return `400 Bad Request`.

### Response enrichment
- The `includeNumberOfTitles` flag, when set, enriches each authority result with the count of bibliographic titles linked to that heading.

## Error behavior
- `400 Bad Request` — invalid CQL syntax or missing required parameters.
- `500 Internal Server Error` — Elasticsearch or internal failure.

## Configuration
| Variable | Purpose |
|----------|---------|
| `SEARCH_QUERY_TIMEOUT` | Elasticsearch query timeout |
| `KAFKA_AUTHORITIES_CONCURRENCY` | Kafka consumer concurrency for authority change events |
| `KAFKA_AUTHORITIES_CONSUMER_PATTERN` | Topic pattern for authority change events |

## Dependencies and interactions
- Depends on Elasticsearch/OpenSearch for query execution.
- Kafka consumer (`folio.kafka.listener.authorities.topicPattern`) ingests authority create/update/delete events from mod-authorities.
