---
feature_id: subject-browse
title: Subject Browse
updated: 2026-05-25
---

# Browse Subjects

## What it does
Allows callers to navigate subject headings alphabetically with a bidirectional browse API. Returns a window of subject entries centered around a requested anchor, each linked to the count of instances that carry that subject, with optional match highlighting.

## Why it exists
Subject browsing gives users a way to explore the catalogue by topic without needing to formulate a keyword query, enabling serendipitous discovery and scoping of a subject domain.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/browse/instances/by-subject` | Browse instances by subject heading |

### Query parameters
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `query` | string | **required** | CQL range expression — see [browsing.md](browsing.md#query-syntax) |
| `limit` | integer | **required** | Total number of items to return in the page |
| `precedingRecordsCount` | integer | `limit / 2` | How many slots go to items **before** the anchor |
| `highlightMatch` | boolean | `true` | Inject a placeholder item with `isAnchor=true` when the anchor is not in the index |

## Business rules and constraints

### Index model
- The browse index is the **`INSTANCE_SUBJECT`** resource. Each document represents one distinct `(value, authorityId, sourceId, typeId)` combination.
- A single subject value (e.g. "Music") can appear as multiple browse items when it has different authority IDs — all appearing as consecutive entries ordered by `authorityId`.

### Sort order
- Results are sorted by: `value` (ICU collation, ascending) → `authorityId` → `sourceId` → `typeId`; missing values sort last.

### Response semantics
- `totalRecords` per item is the sum of instance counts across all sub-resources visible to the requesting tenant (consortium-aware).

### Constraints and validation
- The feature can be disabled via `BROWSE_SUBJECTS_ENABLED`.
- All other parameter validation, filter syntax, `isAnchor` semantics, and offset limits follow the [common browse rules](browsing.md#common-rules-and-constraints).

### Filterable fields
| Field | Example filter query |
|-------|---------------------|
| `sourceId` | `sourceId=="e62bbefe-adf5-4b1e-b3e7-43d877b0c91b"` |
| `typeId` | `typeId=="e62bbefe-adf5-4b1e-b3e7-43d877b0c91c"` |
| `instances.tenantId` | `instances.tenantId=="tenant_a"` |
| `instances.shared` | `instances.shared==true` |

## Error behavior
- See [common browse error behavior](browsing.md#error-behavior).

## Configuration
| Variable | Purpose |
|----------|---------|
| `BROWSE_SUBJECTS_ENABLED` | Enables or disables subject browse |

See also [common browse configuration](browsing.md#configuration).

## Dependencies and interactions
- Depends on OpenSearch for browse query execution via `search_after` cursor.
- See [browsing.md](browsing.md) for the shared browse algorithm, query syntax, response structure, and service class hierarchy.
