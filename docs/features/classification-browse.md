---
feature_id: classification-browse
title: Classification Number Browse
updated: 2026-05-25
---

# Browse Classification Numbers

## What it does
Allows callers to navigate instance records alphabetically by classification number using a bidirectional browse API. Returns a window of classification number entries centered around a requested anchor, with configurable preceding record count and optional match highlighting.

## Why it exists
Classification-based browsing lets users explore the catalogue by subject scheme (e.g. LC, Dewey) in a way that reflects the intellectual organisation of knowledge, complementing keyword search.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/browse/instances/by-classification/{browseOptionId}` | Browse instances by classification number type |
| GET | `/browse/config/instance-classification` | Get all classification browse option configurations |
| GET | `/browse/config/instance-classification/{browseOptionId}` | Get a specific classification browse option configuration |
| PUT | `/browse/config/instance-classification/{browseOptionId}` | Update a classification browse option configuration |

### Query parameters
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `query` | string | **required** | CQL range expression on `classificationNumber` — see [browsing.md](browsing.md#query-syntax) |
| `limit` | integer | **required** | Total number of items to return in the page |
| `precedingRecordsCount` | integer | `limit / 2` | How many slots go to items **before** the anchor |
| `highlightMatch` | boolean | `true` | Inject a placeholder item with `isAnchor=true` when the anchor is not in the index |

## Business rules and constraints

### Index model
- The browse index is the **`INSTANCE_CLASSIFICATION`** resource. Each document represents one distinct `(classificationNumber, classificationTypeId)` combination.
- `{browseOptionId}` (e.g. `LC`, `DEWEY`, `ALL`) selects a pre-configured browse profile defining a `shelvingAlgorithm` and a list of `typeIds`. An empty `typeIds` list includes all classification types (`ALL` semantics).

### Sort order
- Results are sorted by a **pre-computed shelving-order field**, not by raw string. Two numbers that look alphabetically similar may be far apart in browse order (e.g. LC shelf keys for `QA76.73.C15`, `QA100`, `QA1771`).
- The `search_after` cursor is seeded with `[normalizedAnchor.toLowerCase(), rawAnchor.toLowerCase()]`.

### Response semantics
- `totalRecords` per item is the sum of instance counts across sub-resources visible to the requesting tenant.
- `instanceTitle` and `instanceContributors` are populated **only when `totalRecords == 1`**; they are `null` for multiple matches or placeholders.
- `isAnchor` is set **exclusively** on `aroundIncluding` queries. It is never set for forward-only or backward-only queries.
- When `highlightMatch=true` and the anchor is absent, a placeholder `{ "classificationNumber": "...", "totalRecords": 0, "isAnchor": true }` is injected.

### Constraints and validation
- The feature can be disabled via `BROWSE_CLASSIFICATIONS_ENABLED`.
- All other parameter validation, `isAnchor` semantics, and offset limits follow the [common browse rules](browsing.md#common-rules-and-constraints).

### Response item fields
| Field | Description |
|-------|-------------|
| `id` | Stable hash of the classification number |
| `classificationNumber` | Raw classification number as stored on the instance |
| `classificationTypeId` | UUID of the classification type (e.g. LC, Dewey) |
| `totalRecords` | Number of instances carrying this classification (tenant-scoped) |
| `instanceTitle` | Title of the matching instance — present only when `totalRecords == 1` |
| `instanceContributors` | Contributors from the matching instance — present only when `totalRecords == 1` |
| `isAnchor` | `true` only on the anchor item in `aroundIncluding` queries |

### Filterable fields
| Field | Example |
|-------|---------|
| `classificationTypeId` | `classificationTypeId=="42471af9-7d25-4f3a-bf78-60d29dcf463b"` |
| `instances.tenantId` | `instances.tenantId=="tenant_a"` |
| `instances.shared` | `instances.shared==true` |

### Example — browsing around an anchor with LC option
```
GET /browse/instances/by-classification/lc
  ?query=number < "QA76.73.C15" or number >= "QA76.73.C15"
  &limit=5
  &precedingRecordsCount=2
```

## Error behavior
- See [common browse error behavior](browsing.md#error-behavior).

## Configuration
| Variable | Purpose |
|----------|---------|
| `BROWSE_CLASSIFICATIONS_ENABLED` | Enables or disables classification number browse |

See also [common browse configuration](browsing.md#configuration).

## Dependencies and interactions
- Depends on OpenSearch for browse query execution via `search_after` cursor with shelving-order normalization.
- Classification type data is used to configure `typeIds` per browse option.
- See [browsing.md](browsing.md) for the shared browse algorithm, query syntax, response structure, and service class hierarchy.
