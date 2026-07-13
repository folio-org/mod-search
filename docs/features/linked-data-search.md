---
feature_id: linked-data-search
title: Linked Data Search
updated: 2026-07-10
---

# Linked Data Search

## What it does
Allows callers to search Linked Data works, authorities, and hubs using CQL queries and receive paginated results. Works results optionally suppress embedded instance details via the `omitInstances` flag.

## Why it exists
FOLIO supports a Linked Data cataloguing model alongside its traditional MARC-based model. This feature exposes a dedicated search surface for Linked Data resource types so that Linked Data-aware clients can discover works, authorities, and hubs independently of the MARC instance index.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| GET | /search/linked-data/works | Search Linked Data works by CQL query |
| GET | /search/linked-data/authorities | Search Linked Data authorities by CQL query |
| GET | /search/linked-data/hubs | Search Linked Data hubs by CQL query |

## Business rules and constraints

### Query
- Queries must be valid CQL. Malformed queries return `400 Bad Request`.
- Works results support an `omitInstances` parameter to suppress nested instance data.

### Indexing
- Linked Data events are indexed directly from the event body — records are not fetched by ID from an external source.

## Error behavior
- `400 Bad Request` — invalid CQL syntax or missing required parameters.
- `500 Internal Server Error` — Elasticsearch or internal failure.

## Configuration
| Variable | Purpose |
|----------|---------|
| `SEARCH_QUERY_TIMEOUT` | Elasticsearch query timeout |
| `KAFKA_LINKED_DATA_CONCURRENCY` | Kafka consumer concurrency for linked data events |

## Dependencies and interactions
- Depends on Elasticsearch/OpenSearch for query execution.
- Kafka consumer (`folio.kafka.listener.linked-data.topicPattern`) ingests linked data authority, work, and hub events matching pattern `(env\.)(.*\.)linked-data\.(authority|work|hub)`.
