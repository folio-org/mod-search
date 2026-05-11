## Overview

Browse is a virtual-shelf navigation feature that lets clients page through an ordered index (subjects, call numbers,
contributors, classifications, authorities) using an **anchor value** and a **limit**. Instead of offset-based
pagination, it uses OpenSearch's `search_after` cursor mechanism, which makes deep navigation efficient at any position
in the index.

---

## API Endpoints

| Browse type          | HTTP endpoint                                              | Target field              |
|:---------------------|:-----------------------------------------------------------|:--------------------------|
| Subject              | `GET /browse/instances/by-subject`                        | `value`                   |
| Call number          | `GET /browse/instances/by-call-number/{browseOptionId}`   | `callNumber`              |
| Classification       | `GET /browse/instances/by-classification/{browseOptionId}`| `classificationNumber`    |
| Contributor          | `GET /browse/instances/by-contributor`                    | `contributor`             |
| Authority            | `GET /browse/authorities`                                  | `headingRef`              |

### Common query parameters

| Parameter              | Type    | Default      | Description                                                                                     |
|:-----------------------|:--------|:-------------|:------------------------------------------------------------------------------------------------|
| `query`                | string  | **required** | CQL range expression (see Query Syntax below)                                                   |
| `limit`                | integer | **required** | Total number of items to return in the page                                                     |
| `precedingRecordsCount`| integer | `limit / 2`  | How many of the `limit` slots go to items **before** the anchor (only meaningful for `around`)  |
| `highlightMatch`       | boolean | `true`       | When `true` and the anchor is not in the index, a placeholder item with `isAnchor=true` is injected |

**Constraint:** `precedingRecordsCount` must be strictly less than `limit`.

---

## Query Syntax

Browsing can be performed in two directions – forward and backward. Browsing around combines results from these two
queries.

| Direction          | Query                               | Description                                                                                                              |
|:-------------------|:------------------------------------|:-------------------------------------------------------------------------------------------------------------------------|
| forward            | `callNumber > F`                    | Returns all records **after** the anchor, ascending                                                                      |
| forward_including  | `callNumber >= F`                   | Returns all records **from** the anchor (inclusive), ascending                                                           |
| backward           | `callNumber < F`                    | Returns all records **before** the anchor, descending, then reversed                                                     |
| backward_including | `callNumber <= F`                   | Returns all records **up to** the anchor (inclusive), descending, then reversed                                          |
| around             | `callNumber > F or callNumber < F`  | Returns records both before and after the anchor; the anchor itself is **excluded** (placeholder injected if `highlightMatch`) |
| around_including   | `callNumber >= F or callNumber < F` | Returns records around the anchor; the anchor is **included** (real item if found, placeholder if not)                  |

_where `callNumber` is the name of the field being browsed and `F` is the anchor value._

### Adding filters

Filters can be appended to any browse query using the `and` keyword. The browse range expression must be wrapped in
parentheses when combined with additional conditions:

```
(value >= "music" or value < "music") and authorityId=="308c950f-8209-4f2e-9702-0c004a9f21bc"
```

Only fields declared with `"searchTypes": ["filter"]` in the index model support filtering.

---

## Response Structure

Every browse endpoint returns a result object with the same shape:

```json
{
  "totalRecords": 50,
  "prev": "Library science",
  "next": "Neuroscience",
  "items": [
    { "value": "Library science", "totalRecords": 6 },
    { "value": "Machine learning", "totalRecords": 7, "isAnchor": true },
    { "value": "Media studies",    "totalRecords": 3 }
  ]
}
```

| Field          | Description                                                                                                    |
|:---------------|:---------------------------------------------------------------------------------------------------------------|
| `totalRecords` | Total number of distinct values in the index (not in this page). Used by clients to know the full index size.  |
| `prev`         | Value of the **first** item in `items`; pass as anchor with a `<` query to navigate to the previous page.     |
| `next`         | Value of the **last** item in `items`; pass as anchor with a `>` query to navigate to the next page. `null` if the last item is the global last. |
| `items`        | Ordered list of browse items for this page.                                                                    |
| `isAnchor`     | Present and `true` only on the item that matches the requested anchor value.                                   |

---

## Algorithm Details

### Context construction (`BrowseContextProvider`)

Before any OpenSearch query is issued, the CQL query string is parsed into a `BrowseContext` object that contains:

- **`anchor`** – the value extracted from the range condition (identical in both clauses for `around` queries; validated to match).
- **`precedingQuery`** – the `<` / `<=` range query builder (present only when browsing backward or around).
- **`succeedingQuery`** – the `>` / `>=` range query builder (present only when browsing forward or around).
- **`precedingLimit`** / **`succeedingLimit`** – how many slots each side of the page gets.
- **`filters`** – extra `filter` clauses extracted from any `and …` conditions in the query.

For **one-direction** queries only one of `precedingQuery`/`succeedingQuery` is populated.
For **around** queries both are populated and the sum of their limits equals `limit`.

### Limit allocation for `around` queries

```
precedingLimit  = precedingRecordsCount      (default: floor(limit / 2))
succeedingLimit = limit - precedingRecordsCount
```

The anchor itself is **not counted** toward either limit – it is handled as a third, separate query.

### OpenSearch execution

All browse variants use OpenSearch's `search_after` cursor (not `from`/`size`) for efficient deep navigation.

#### One-direction browse

| Anchor included? | Queries issued | How anchor is handled |
|:-----------------|:---------------|:----------------------|
| No (`>` or `<`)  | 1 main search  | Not fetched; cursor starts strictly after/before anchor value |
| Yes (`>=` or `<=`)| 2 (main + anchor) | Anchor fetched separately, prepended/appended to the page |

The anchor search uses an exact `term` query on the target field.

#### Around browse

Three OpenSearch queries are issued via a single `msearch` call:

1. **Anchor query** – `term(targetField, anchor)` – determines the exact sort values to seed the cursors.
2. **Preceding query** – `search_after` cursor descending from the anchor, fetching `precedingLimit + 1` items.
3. **Succeeding query** – `search_after` cursor ascending from the anchor, fetching `succeedingLimit + 1` items.

The `+1` overfetch on each side is used to detect whether a `prev`/`next` pointer exists beyond the current page without
issuing an extra request.

The anchor query's sort values are used to seed the `search_after` parameter of both the preceding and succeeding
queries, so they start exactly at the anchor boundary.

#### Final page assembly

```
precedingRecords  = reverse(trim(preceding results, precedingLimit))
succeedingRecords = [anchor item] + trim(succeeding results, succeedingLimit)
page              = precedingRecords + succeedingRecords
prev              = first item of precedingRecords  (null if none)
next              = last item of succeedingRecords   (null if ≤ succeedingLimit results returned)
```

When `highlightMatch=true` and the anchor value is not found in the index, a **placeholder** item is injected:

```json
{ "value": "genetics", "totalRecords": 0, "isAnchor": true }
```

When `highlightMatch=false`, no placeholder is injected and the anchor term does not appear if it has no matching
records.

---

## Subject Browse

Subject browse operates on the **`INSTANCE_SUBJECT`** resource, which is a denormalized sub-index derived from
instances. Each document represents one distinct `(value, authorityId, sourceId, typeId)` combination and stores a
nested list of instance back-references with per-tenant counts.

### Sort order

Results within a page are sorted by:

1. `value` – ICU collation ascending (case-insensitive, language-aware)
2. `authorityId` – ascending, missing values sort **last**
3. `sourceId` – ascending, missing values sort last
4. `typeId` – ascending (max mode), missing values sort last

This means that when two subjects share the same `value` (e.g. two "Music" entries with different authority IDs) they
both appear as separate items within the same page, ordered by their `authorityId`.

### `totalRecords` per item

The count shown on a subject browse item is the **sum of instance counts** across all `instances` sub-resources
that belong to the requesting tenant (or, in consortium mode, the active affiliation).

### Filterable / facetable fields

The following fields on the subject index support filter and facet queries:

| Field         | Example filter query                                      |
|:--------------|:----------------------------------------------------------|
| `sourceId`    | `sourceId=="e62bbefe-adf5-4b1e-b3e7-43d877b0c91b"`       |
| `typeId`      | `typeId=="e62bbefe-adf5-4b1e-b3e7-43d877b0c91c"`         |
| `instances.tenantId` | `instances.tenantId=="tenant_a"`                  |
| `instances.shared`   | `instances.shared==true`                          |

---

## Call Number Browse

Call number browse operates on the **`INSTANCE_CALL_NUMBER`** resource and additionally requires a `browseOptionType`
(e.g. `LC`, `DEWEY`, `NLM`, …) which selects the shelving algorithm to use when normalizing the anchor.

The normalized call number is stored in a dedicated shelving-order field. The `search_after` cursor is seeded with
the **normalized** anchor value so results are ordered by shelving position, not raw text.

Each result item carries:
- `fullCallNumber`, `callNumber`, `callNumberPrefix`, `callNumberSuffix`, `callNumberTypeId`
- `instanceTitle` – populated only when exactly one instance has that call number (avoids ambiguity)
- `totalRecords` – number of instances with that call number in the requesting tenant

---

## Consortium / Multi-tenant Support

In ECS (consortium) mode, browse queries are automatically filtered to the **active affiliation** tenant by
`ConsortiumSearchHelper`. The `totalRecords` field on each browse item counts only items visible to that affiliation.
The top-level `totalRecords` in the response reflects the full index size across all tenants.

---

## Service Class Hierarchy

```
AbstractBrowseService<T>
│   browse(request)                 – entry point; dispatches to direction or around
│   browseInOneDirection(...)       – abstract
│   browseAround(...)               – abstract
│
└── AbstractBrowseServiceBySearchAfter<T, R>
    │   browseInOneDirection(...)   – uses search_after; handles anchor-include logic
    │   browseAround(...)           – issues msearch (2 or 3 queries); assembles page
    │   getSearchQuery(...)         – abstract (per-resource sort fields)
    │   getAnchorSearchQuery(...)   – abstract (term query on targetField)
    │   getEmptyBrowseItem(...)     – abstract (placeholder for missing anchor)
    │   mapToBrowseResult(...)      – abstract (maps raw ES doc to DTO)
    │
    ├── SubjectBrowseService          (resource: INSTANCE_SUBJECT)
    ├── ContributorBrowseService      (resource: INSTANCE_CONTRIBUTOR)
    ├── AuthorityBrowseService        (resource: AUTHORITY)
    │
    └── AbstractShelvingOrderBrowseServiceBySearchAfter<T, R>
        │   getSearchQuery(...)       – normalizes anchor via ShelvingOrderCalculationHelper
        │
        ├── CallNumberBrowseService       (resource: INSTANCE_CALL_NUMBER)
        └── ClassificationBrowseService   (resource: INSTANCE_CLASSIFICATION)
```

---

## CQL Escaping for Special Characters

Anchor values containing CQL-special characters (backslash, double-quote) must be double-escaped when placed inside a
CQL string literal. For example, the subject value:

```
backslash\ "double\-quotes\" te\st
```

must be written in a CQL query as:

```
value >= "backslash\\ \"double\\-quotes\\\" te\\st"
```

And in a Java string literal (as used in tests):

```java
"backslash\\\\ \\\"double\\\\-quotes\\\\\\\" te\\\\st"
```
