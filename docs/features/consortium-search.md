---
feature_id: consortium-search
title: Consortium Search
updated: 2026-05-22
---

# Consortium Search

## What it does
Provides cross-tenant search for consolidated holdings, items, and locations across all member institutions within a consortium (ECS) environment. Exposes endpoints to retrieve individual holdings/items by ID, batch-fetch multiple holdings/items, and list shared reference data (locations, campuses, libraries, institutions).

## Why it exists
In a consortium, holdings and items are distributed across many member tenants. Consortium search aggregates this data into a single queryable surface so that a central tenant or shared discovery layer can present a unified view of availability across the entire consortium without federating queries to each member.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| GET | /search/consortium/holdings | Get paginated consolidated holdings |
| GET | /search/consortium/holding/{id} | Get a single consolidated holding by ID |
| POST | /search/consortium/batch/holdings | Batch-fetch consolidated holdings |
| GET | /search/consortium/items | Get paginated consolidated items |
| GET | /search/consortium/item/{id} | Get a single consolidated item by ID |
| POST | /search/consortium/batch/items | Batch-fetch consolidated items |
| GET | /search/consortium/locations | Get all consortium locations |
| GET | /search/consortium/campuses | Get all consortium campuses |
| GET | /search/consortium/libraries | Get all consortium libraries |
| GET | /search/consortium/institutions | Get all consortium institutions |

## Business rules and constraints

### Applicability
- Consortium endpoints are only meaningful in a consortium (ECS) environment. In a single-tenant deployment they return empty or tenant-scoped results.

### Data filtering
- Holdings and items can be filtered by `instanceId` and `tenantId`.
- Shadow location/unit records (identified by consortium source prefix) are excluded from location indexing.

### Data updates
- When a consortium instance-sharing operation completes (`CONSORTIUM_INSTANCE_SHARING_COMPLETE` event), call number `lastUpdatedDate` is updated in the central tenant's index.

### Limitations
- Page size for consortium record fetching is controlled by `SEARCH_CONSORTIUM_RECORDS_PAGE_SIZE`.

## Error behavior
- `400 Bad Request` — invalid parameters.
- `500 Internal Server Error` — Elasticsearch or internal failure.

## Configuration
| Variable | Purpose |
|----------|---------|
| `SEARCH_CONSORTIUM_RECORDS_PAGE_SIZE` | Page size when fetching consolidated consortium records |
| `KAFKA_CONSORTIUM_INSTANCE_SHARING_COMPLETE_CONCURRENCY` | Concurrency for instance-sharing-complete event consumer |

## Dependencies and interactions
- Depends on Elasticsearch/OpenSearch for query execution.
- Kafka consumer (`folio.kafka.listener.instance-sharing-complete.topicPattern`) listens for `CONSORTIUM_INSTANCE_SHARING_COMPLETE` events to trigger call number updates on the central tenant.
- Kafka consumer (`folio.kafka.listener.location.topicPattern`) listens for `inventory.(location|campus|institution|library)` events to keep reference data in sync.
