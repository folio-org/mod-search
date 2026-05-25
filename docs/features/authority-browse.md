---
feature_id: authority-browse
title: Authority Browse
updated: 2026-05-25
---

# Browse Authorities

## What it does
Allows callers to navigate authority headings alphabetically using a bidirectional browse API. Returns a window of authority entries centered around a requested anchor, with optional heading-type filtering and match highlighting.

## Why it exists
Authority browse supports cataloguing workflows by letting cataloguers navigate the authority file alphabetically to locate the correct heading form, verify usage, and discover related headings in a sorted view.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/browse/authorities` | Browse authority records by heading reference |

### Query parameters
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `query` | string | **required** | CQL range expression — see [browsing.md](browsing.md#query-syntax) |
| `limit` | integer | **required** | Total number of items to return in the page |
| `precedingRecordsCount` | integer | `limit / 2` | How many slots go to items **before** the anchor |
| `highlightMatch` | boolean | `true` | Inject a placeholder item with `isAnchor=true` when the anchor is not in the index |

## Business rules and constraints

### Index model
- Each document in the `AUTHORITY` browse index represents one **heading entry**, not one authority record. A single authority record typically produces multiple entries: one authorized heading, zero or more reference headings (from `sft*` fields), and optionally title headings (from `*Title` fields).
- Results are sorted by `headingRef` using ICU collation — case-insensitive, diacritic-aware, and language-aware. Characters with diacritics (e.g. `ä`, `Ĵ`) sort near their base-letter equivalents.

### Response semantics
- `totalRecords` reflects the count of browse entries matching the **non-range** filter conditions (e.g. `headingType`, `isTitleHeadingRef`). When filters are applied this may be significantly smaller than the full index size.
- `isAnchor` is set on the real anchor item in `aroundIncluding` queries and on placeholder items when `highlightMatch=true` and the anchor is absent. It is **never set** on items from forward-only or backward-only queries.
- The `authority` object is absent on placeholder items (anchor values not present in the index).

### Constraints and validation
- Filters can be appended with `and`; the range expression must be wrapped in parentheses when combined with filters.
- Authority browse has no dedicated feature-toggle variable.
- All other parameter validation, `isAnchor` semantics, and offset limits follow the [common browse rules](browsing.md#common-rules-and-constraints).

### Index document fields
| Field | Type | Description |
|-------|------|-------------|
| `headingRef` | `keyword_icu` | The heading text — the field being browsed and sorted |
| `headingType` | `keyword` | Heading category (e.g. `Personal Name`, `Corporate Name`, `Topical`, `Genre`) |
| `authRefType` | `keyword` | `Authorized` or `Reference` |
| `isTitleHeadingRef` | `boolean` | `true` when sourced from a `*Title` field |
| `naturalId` | `keyword` | External/LC control number |
| `sourceFileId` | `keyword` | UUID of the authority source file |
| `id` | `keyword` | UUID of the authority record |
| `shared` | `boolean` | Whether shared across the consortium |
| `numberOfTitles` | `integer` | Count of linked title instances (present only on `Authorized` entries) |

### Response item structure
```json
{
  "headingRef": "Brian K. Vaughan",
  "isAnchor": true,
  "authority": {
    "id": "0000002b-0000-4000-a000-000000000000",
    "naturalId": "nb1994732053",
    "headingRef": "Brian K. Vaughan",
    "headingType": "Personal Name",
    "authRefType": "Authorized",
    "isTitleHeadingRef": false,
    "sourceFileId": "b4000001-5de4-4467-b77f-b2057d6d69b6",
    "tenantId": "test_tenant",
    "shared": false,
    "numberOfTitles": 0
  }
}
```
The `authority` object is **absent on placeholder items** (anchor values not found in the index).

### Filterable fields
| Field | Example filter query |
|-------|---------------------|
| `headingType` | `headingType==("Personal Name")` or `headingType==("Personal Name" OR "Corporate Name")` |
| `isTitleHeadingRef` | `isTitleHeadingRef==false` |
| `sourceFileId` | `sourceFileId=="b4000001-5de4-4467-b77f-b2057d6d69b6"` |
| `tenantId` | `tenantId=="test_tenant"` |
| `shared` | `shared==false` |

### Example — browsing around with heading-type filter
```
GET /browse/authorities
  ?query=( headingRef >= "Ĵämes Röllins" or headingRef < "Ĵämes Röllins" )
         and isTitleHeadingRef==false
         and headingType==("Personal Name")
         and sourceFileId=="b4000001-5de4-4467-b77f-b2057d6d69b6"
  &limit=7
  &precedingRecordsCount=2
```

## Error behavior
- See [common browse error behavior](browsing.md#error-behavior).

## Configuration
See [common browse configuration](browsing.md#configuration). Authority browse has no dedicated feature-toggle variable.

## Dependencies and interactions
- Depends on OpenSearch for browse query execution via `search_after` cursor.
- See [browsing.md](browsing.md) for the shared browse algorithm, query syntax, response structure, and service class hierarchy.
