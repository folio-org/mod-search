---
feature_id: call-number-browse
title: Call Number Browse
updated: 2026-05-25
---

# Browse Call Numbers

## What it does
Allows callers to navigate instance records alphabetically by call number using a bidirectional browse API. Returns a window of call number entries centered around the requested anchor, with configurable preceding record count and optional match highlighting.

## Why it exists
Shelf browsing is a fundamental library discovery pattern. Patrons and staff need to navigate the virtual shelf to find items shelved near a known call number, mimicking the physical experience of browsing a shelf.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/browse/instances/by-call-number/{browseOptionId}` | Browse instances by call number type |
| GET | `/browse/config/instance-call-number` | Get all call number browse option configurations |
| GET | `/browse/config/instance-call-number/{browseOptionId}` | Get a specific call number browse option configuration |
| PUT | `/browse/config/instance-call-number/{browseOptionId}` | Update a call number browse option configuration |

### Query parameters
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `query` | string | **required** | CQL range expression on `fullCallNumber` — see [browsing.md](browsing.md#query-syntax) |
| `limit` | integer | **required** | Total number of items to return in the page |
| `precedingRecordsCount` | integer | `limit / 2` | How many slots go to items **before** the anchor |
| `highlightMatch` | boolean | `true` | Inject a placeholder item with `isAnchor=true` when the anchor is not in the index |

## Business rules and constraints

### Index model
- The browse index is the **`INSTANCE_CALL_NUMBER`** resource. Each document represents one distinct `(callNumber, callNumberPrefix, callNumberSuffix, callNumberTypeId)` combination sourced from **`items.effectiveCallNumberComponents`**. Holdings-level and instance-level call numbers are not used directly.
- The **full call number** displayed and used as the browse anchor is `callNumber + " " + suffix` (when suffix is non-null). The prefix is stored and returned but is **not** part of the sort key or anchor.

### Sort order
- Results are sorted by: normalized shelving-order key → `callNumberSuffix` → `callNumberPrefix` → `callNumberTypeId`; missing values sort last.
- Shelving-order normalization converts the raw call number to a zero-padded, case-folded key so that numeric sub-classes sort numerically (e.g. `QA9` before `QA10`).

### Browse options
- `{browseOptionId}` selects the shelving algorithm: `all` (best-match, no type filter), `lc`, `dewey`, `nlm`, or `sudoc`.
- For typed options (e.g. `lc`), an exact match is only produced when the item's `callNumberTypeId` is in the configured type list. Otherwise a placeholder is returned even if the raw string is present in the index.
- The `all` option never filters by type and always produces an exact match when the call number exists.
- Browse option configurations are refreshed when `inventory.(call-number-type|classification-type)` Kafka events are received.

### Response semantics
- `totalRecords` per item is the number of item records sharing the same effective call number components, scoped to the requesting tenant.
- `instanceTitle` is populated **only when `totalRecords == 1`**; it is `null` for multiple matches or placeholders.
- `isAnchor` is set **exclusively** on `aroundIncluding` queries. It is never set for forward-only or backward-only queries.
- When `highlightMatch=true` and the anchor is absent, a placeholder `{ "fullCallNumber": "...", "totalRecords": 0, "isAnchor": true }` is injected.

### Constraints and validation
- Call numbers containing backslashes must be double-escaped in CQL — see [CQL escaping](browsing.md#cql-escaping-for-special-characters).
- The feature can be disabled via `BROWSE_CALL_NUMBERS_ENABLED`.
- All other parameter validation, `isAnchor` semantics, and offset limits follow the [common browse rules](browsing.md#common-rules-and-constraints).

### Response item fields
| Field | Description |
|-------|-------------|
| `id` | Stable hash of the call number components |
| `fullCallNumber` | Display form: `callNumber [ + " " + suffix]` |
| `callNumber` | Base shelf number as stored on the item |
| `callNumberPrefix` | Optional prefix (e.g. `"REF"`, `"Oversize"`) |
| `callNumberSuffix` | Optional suffix (e.g. `"c.1"`, `"FT MEADE"`) |
| `callNumberTypeId` | UUID of the call number type |
| `totalRecords` | Number of item records sharing this call number (tenant-scoped) |
| `instanceTitle` | Title of the matching instance — present only when `totalRecords == 1` |
| `isAnchor` | `true` only on the anchor item in `aroundIncluding` queries |

### Filterable fields
| Field | Example |
|-------|---------|
| `callNumberTypeId` | `callNumberTypeId=="cbc422b0-1d17-4d43-9cc0-6c89b2efd014"` |
| `items.effectiveLocationId` | `items.effectiveLocationId=="65b6c2e9-8a7b-4a10-9b5d-ba1cf0313cd7"` |
| `instances.tenantId` | `instances.tenantId=="tenant_a"` |
| `instances.shared` | `instances.shared==true` |

### Example — browsing around an anchor with LC option
```
GET /browse/instances/by-call-number/lc
  ?query=fullCallNumber >= "RC280.N4 N49" or fullCallNumber < "RC280.N4 N49"
  &limit=5
  &precedingRecordsCount=2
```

## Error behavior
- See [common browse error behavior](browsing.md#error-behavior).

## Configuration
| Variable | Purpose |
|----------|---------|
| `BROWSE_CALL_NUMBERS_ENABLED` | Enables or disables call number browse |
| `KAFKA_BROWSE_CONFIG_DATA_CONCURRENCY` | Concurrency for call-number-type/classification-type config sync events |

See also [common browse configuration](browsing.md#configuration).

## Dependencies and interactions
- Depends on OpenSearch for browse query execution via `search_after` cursor with shelving-order normalization.
- Kafka consumer (`folio.kafka.listener.browse-config-data.topicPattern`) listens for `inventory.(classification-type|call-number-type)` events to keep browse option data in sync.
- See [browsing.md](browsing.md) for the shared browse algorithm, query syntax, response structure, and service class hierarchy.
