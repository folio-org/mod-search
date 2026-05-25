---
feature_id: contributor-browse
title: Contributor Browse
updated: 2026-05-25
---

# Browse Contributors

## What it does
Allows callers to navigate contributor names alphabetically with a bidirectional browse API. Returns a window of contributor entries centered around a requested anchor, each linked to the count of instances associated with that contributor, with optional match highlighting.

## Why it exists
Contributor browsing allows users to find all works by an author or other contributor by navigating around their name in a sorted list, useful when the exact name form is uncertain.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/browse/instances/by-contributor` | Browse instances by contributor name |

### Query parameters
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `query` | string | **required** | CQL range expression — see [browsing.md](browsing.md#query-syntax) |
| `limit` | integer | **required** | Total number of items to return in the page |
| `precedingRecordsCount` | integer | `limit / 2` | How many slots go to items **before** the anchor |
| `highlightMatch` | boolean | `true` | Inject a placeholder item with `isAnchor=true` when the anchor is not in the index |

## Business rules and constraints

### Index model
- The browse index is the **`INSTANCE_CONTRIBUTOR`** resource. Each document represents one distinct `(name, contributorNameTypeId, authorityId)` combination.
- A single contributor name yields **multiple browse items** when it appears with different name types or authority links (e.g. "John Lennon" linked to an authority record and "John Lennon" without one are separate items).

### Sort order
- Results are sorted by: `name` (ICU collation, ascending; digits sort before letters) → `contributorNameTypeId` → `authorityId`; missing values sort last.

### Response semantics
- `totalRecords` per item is the sum of `instances.count` across sub-resources visible to the requesting tenant (consortium-aware).

### Constraints and validation
- The feature can be disabled via `BROWSE_CONTRIBUTORS_ENABLED`.
- All other parameter validation, filter syntax, `isAnchor` semantics, and offset limits follow the [common browse rules](browsing.md#common-rules-and-constraints).

### Index document fields
| Field | Type | Description |
|-------|------|-------------|
| `name` | `keyword_icu` | Contributor name as stored on the instance |
| `contributorNameTypeId` | `keyword` | MARC-defined name type (e.g. personal, corporate) |
| `authorityId` | `keyword` | ID of the controlling authority record (may be absent) |
| `instances` | nested object | One entry per contributing instance/tenant pair |
| `instances.typeId` | `keyword` (array) | Contributor role/type IDs on that instance |
| `instances.tenantId` | `keyword` | Owning tenant |
| `instances.shared` | `bool` | Whether the instance is shared across the consortium |
| `instances.count` | integer | Number of instances from that tenant sharing this combination |

### Response item fields
| Field | Description |
|-------|-------------|
| `name` | Contributor name |
| `contributorNameTypeId` | Name type UUID of this browse entry |
| `authorityId` | Authority record UUID (absent when no authority link) |
| `contributorTypeId` | Deduplicated, sorted list of contributor role IDs from all instances sharing this entry |
| `totalRecords` | Instance count for this entry (tenant-scoped) |
| `isAnchor` | `true` on the anchor item |

### Filterable / facetable fields
| Field | Example filter / facet query |
|-------|------------------------------|
| `contributorNameTypeId` | `contributorNameTypeId=="e2ef4075-310a-4447-a231-712bf10cc985"` |
| `instances.tenantId` | `instances.tenantId=="tenant_a"` |
| `instances.shared` | `instances.shared==true` |

### Example — browsing around a name with a name-type filter
```
GET /browse/instances/by-contributor
  ?query=( name >= "John Lennon" or name < "John Lennon" )
         and contributorNameTypeId=="e2ef4075-310a-4447-a231-712bf10cc985"
  &limit=5
```

## Error behavior
- See [common browse error behavior](browsing.md#error-behavior).

## Configuration
| Variable | Purpose |
|----------|---------|
| `BROWSE_CONTRIBUTORS_ENABLED` | Enables or disables contributor browse |

See also [common browse configuration](browsing.md#configuration).

## Dependencies and interactions
- Depends on OpenSearch for browse query execution via `search_after` cursor.
- See [browsing.md](browsing.md) for the shared browse algorithm, query syntax, response structure, and service class hierarchy.
