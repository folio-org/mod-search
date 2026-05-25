---
feature_id: cql-query
title: CQL Query Translation
updated: 2026-05-22
---

# CQL Query Translation

## What it does
Translates incoming CQL (Common Query Language) queries into Elasticsearch DSL query objects. Handles field-level query modifiers, sort clauses, facet query building, and search term normalisation for all search and browse endpoints.

## Why it exists
FOLIO adopts CQL as its standard query language across modules. This feature provides the translation layer that bridges the CQL surface expected by API consumers and the Elasticsearch query DSL required internally, ensuring consistent query semantics regardless of which record type or endpoint is used.

## Business rules and constraints

### Query processing pipeline
- All CQL queries pass through `CqlSearchQueryConverter` before being submitted to Elasticsearch.
- Field-level modifiers are applied by `SearchFieldModifier` implementations (e.g. `ItemSearchFieldModifier`).
- Sort clauses are resolved by `CqlSortProvider` against the field metadata registry.
- Facet queries are constructed separately by `FacetQueryBuilder`.
- SuDoc call number normalisation is handled by `SuDocCallNumber` during term processing.

### Validation
- Unrecognised fields or unsupported modifiers return `400 Bad Request` to the caller.

## Error behavior
- `400 Bad Request` — malformed CQL syntax or reference to an unknown/unsupported field.

## Configuration
| Variable | Purpose |
|----------|---------|
| `SEARCH_QUERY_TIMEOUT` | Elasticsearch request timeout applied to all translated queries |
