---
feature_id: item-search
title: Item Search
updated: 2026-10-23
---

# Item Search

## What it does
Allows callers to search item records directly using CQL queries and receive paginated results (with facet counts available via the shared facets endpoint), without going through the instance search or the consortium search. Returns a default set of item fields and supports selective field inclusion.

## Why it exists
Integrations need to look up items (e.g. by barcode, effective location or status) without retrieving the parent instances. This feature exposes a standalone, per-tenant items search over the dedicated `item` index.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| GET | /search/inventory/items | Search items by CQL query |
| GET | /search/items/facets | Get facet counts for item fields (shared `/search/{recordType}/facets` endpoint) |

## Business rules and constraints

### Query
- Queries must be valid CQL. Malformed queries return `400 Bad Request`.
- `limit` and `offset` control pagination of results.
- `expandAll` is not supported: only a default set of fields is returned. `include` adds extra fields to that set.
- Default fields: `id`, `instanceId`, `holdingsRecordId`, `barcode`, `callNumber`, `callNumberPrefix`, `callNumberSuffix`, `callNumberTypeId`, `statusName`, `permanentLoanTypeId`, `temporaryLoanTypeId`, `effectiveLocationId`, `materialTypeId` (marked `showInResponse` in `model/item.json`).
- `instanceTitle` is added to every returned item by `ItemSearchResponsePostProcessor`, which looks up the `plain_title` of the instances (by `instanceId`) in the instance index. It is not stored in the item index.
- Responses have the shape `{ "totalRecords": <int>, "items": [ ... ] }`, where `items` are full item objects.
- The `x-okapi-tenant` header is required; the search runs against the tenant's own `item` index.
- Access requires the `search.inventory.items.collection.get` permission (interface `search-inventory`).

### Facets
- Facets are requested through the shared facets endpoint with `recordType=items`: `GET /search/items/facets?query=<cql>&facet=<field>`.
- Facetable item fields (marked as `facet` in `model/item.json`): `effectiveLocationId`, `statisticalCodeIds`, `statusName`, `materialTypeId`, `discoverySuppress`.
- Access requires the `search.facets.collection.get` permission (interface `search-facets`).

### Index
- Field mappings are defined in `src/main/resources/model/item.json`; index settings and analyzers in `src/main/resources/elasticsearch/index/item.json` (same as the instance index).
- The index is created empty on tenant initialization and populated from inventory item events when item indexing is enabled. How documents are produced, retried and how failures are stored is described in [Indexing and Messaging](indexing-and-messaging.md#standalone-item-indexing).
- Known follow-up: consortium affiliation filtering on item search (a member tenant seeing other members' items) is not implemented.

## Error behavior
- `400 Bad Request` — invalid CQL syntax or missing required parameters.
- `500 Internal Server Error` — Elasticsearch or internal failure.

## Configuration
| Variable | Purpose |
|----------|---------|
| `SEARCH_QUERY_TIMEOUT` | Elasticsearch query timeout |

## Dependencies and interactions
- Depends on Elasticsearch/OpenSearch for query execution (`ELASTICSEARCH_URL`).
- The `item` index is populated by [Indexing and Messaging](indexing-and-messaging.md#standalone-item-indexing).
- Shares the CQL parsing and search pipeline with [Instance Search](instance-search.md); see also [CQL Query](cql-query.md).
