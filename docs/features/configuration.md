---
feature_id: configuration
title: Configuration
updated: 2026-05-22
---

# Configuration

## What it does
Provides runtime management of search and browse configuration options. Operators and tenants can enable or disable optional search features (e.g. search-all-fields), manage supported indexing languages, and configure browse option parameters — all without redeploying the module.

## Why it exists
Different tenants and deployments have different needs. Some features (such as all-fields search or specific browse types) are optional and resource-intensive. This feature exposes configuration APIs that allow per-tenant tuning of search behaviour at runtime.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| GET | /search/config/features | List all feature configurations |
| POST | /search/config/features | Create or update a feature configuration |
| GET | /search/config/features/{featureId} | Get a specific feature configuration |
| PUT | /search/config/features/{featureId} | Update a specific feature configuration |
| GET | /search/config/languages | List configured indexing languages |
| POST | /search/config/languages | Add a supported language |
| DELETE | /search/config/languages/{code} | Remove a supported language |
| GET | /browse/config/{browseType} | Get all browse option configurations for a browse type |
| GET | /browse/config/{browseType}/{browseOptionId} | Get a specific browse option configuration |
| PUT | /browse/config/{browseType}/{browseOptionId} | Update a specific browse option configuration |

## Business rules and constraints

### Feature flags
- Feature configurations map to `folio.search-config.search-features` keys; the API overrides defaults at runtime without redeployment.
- The `422 Unprocessable Entity` status is returned when a feature configuration value is invalid.

### Language configuration
- Supported languages affect how text fields are analysed during indexing. Adding a language does **not** retroactively re-index existing documents.
- The number of supported languages per tenant is capped by `MAX_SUPPORTED_LANGUAGES`.
- Initial languages are seeded from `INITIAL_LANGUAGES` on tenant initialisation.

### Browse configurations
- Call number type and classification type configurations are synchronised from inventory reference data via Kafka.
- DELETE events for those types trigger a cache eviction of `reference-data-cache`.

### Caching
- Language and feature configurations are cached per tenant (`tenant-languages`, `tenant-features` caches) with a 1-hour TTL.
- In a clustered deployment each node holds its own cache; changes are reflected immediately on the node that handled the request, and on other nodes after cache expiry or eviction.

## Error behavior
- `400 Bad Request` — invalid request body.
- `422 Unprocessable Entity` — invalid feature configuration value.
- `500 Internal Server Error` — persistence or internal failure.

## Caching
Feature configurations and language configurations are cached per tenant with a maximum size of 500 entries and a 1-hour expiry (`spring.cache.caffeine.spec`). In a clustered deployment, each node holds its own cache; changes applied via the API are reflected immediately on the node that handled the request, and on other nodes after cache expiry or eviction.

## Configuration
| Variable | Purpose |
|----------|---------|
| `INITIAL_LANGUAGES` | Comma-separated language codes seeded on tenant init (default: `eng`) |
| `MAX_SUPPORTED_LANGUAGES` | Maximum number of languages a tenant can configure |
| `SEARCH_BY_ALL_FIELDS_ENABLED` | Default value for the search-all-fields feature flag |
| `BROWSE_CONTRIBUTORS_ENABLED` | Default value for the browse-contributors feature flag |
| `BROWSE_SUBJECTS_ENABLED` | Default value for the browse-subjects feature flag |
| `BROWSE_CLASSIFICATIONS_ENABLED` | Default value for the browse-classifications feature flag |
| `BROWSE_CALL_NUMBERS_ENABLED` | Default value for the browse-call-numbers feature flag |
