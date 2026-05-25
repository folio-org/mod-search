---
feature_id: field-mapping-engine
title: Field Mapping Engine
updated: 2026-05-22
---

# Field Mapping Engine

## What it does
Provides the core infrastructure that defines how each resource type is structured and searchable in Elasticsearch. Loads and validates per-resource-type descriptor files, resolves field type definitions, applies field processors to compute derived field values, and drives the creation of Elasticsearch index mappings and settings at tenant initialisation time.

## Why it exists
Search correctness and performance depend entirely on how records are mapped to Elasticsearch documents. This feature provides the engine that interprets resource descriptors and field type definitions at startup, ensuring every index is created with the right mappings before any documents are written, and that every indexing operation produces correctly shaped documents.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| PUT | /search/index/mappings | Push current mapping definitions to Elasticsearch |
| PUT | /search/index/settings | Push current settings definitions to Elasticsearch |

## Business rules and constraints

### Startup validation
- Resource descriptors are loaded from `src/main/resources/model/*.json` at application startup via `ResourceDescriptionService`.
- Field type definitions are loaded from `src/main/resources/elasticsearch/index-field-types.json`.
- Each field referencing a `processor` is validated at startup: the named `FieldProcessor` bean must exist and its generic input type must be assignable from the resource's event body class. A missing or incompatible processor causes a `ResourceDescriptionException` on startup, **preventing the application from starting**.

### Index lifecycle
- Elasticsearch index mappings and settings are applied per-tenant at tenant initialisation and can be refreshed via the REST API.

### Field processors
- Field processors (`service/setter/`) compute derived or transformed field values during the indexing pipeline. They do not affect query-time behaviour directly.

## Error behavior
- Application fails to start if any resource descriptor references an unknown or type-incompatible field processor.
- `500 Internal Server Error` — failure pushing mappings or settings to Elasticsearch.

## Configuration
| Variable | Purpose |
|----------|---------|
| `ELASTICSEARCH_URL` | Elasticsearch/OpenSearch cluster URL |
| `ELASTICSEARCH_USERNAME` / `ELASTICSEARCH_PASSWORD` | Cluster credentials |
| `ELASTICSEARCH_COMPRESSION_ENABLED` | Enable HTTP compression for Elasticsearch requests |
| `ELASTICSEARCH_SERVER` | Set to `true` when connecting to a native Elasticsearch server instead of OpenSearch |

## Dependencies and interactions
- Depends on Elasticsearch/OpenSearch for mapping and settings operations.
