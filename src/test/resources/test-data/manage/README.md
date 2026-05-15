# Test Data Manager

SQLite-backed developer tool for managing shared integration-test data.

## Purpose

All JSON files in the `test-data/` folder are shared test data loaded by `SharedTestDataManager`
and used by the following integration test suites:

- **`SearchBrowseIT`** — loads all records into a single tenant and runs browse, facet, search, sort,
  and stream-IDs tests across instances, authorities, call numbers, classifications, contributors,
  subjects, holdings, items, and linked-data resources.
- **`SearchBrowseConsortiumIT`** — loads records into a central tenant and a member tenant and runs
  consortium search/browse tests for linked-data, locations, libraries, institutions, and campuses.

Both test classes share the same Elasticsearch index for the duration of their test run, so any
change to these JSON files affects all nested test classes inside them.

The full set of JSON files consumed by both suites:

| File                         | Resource type         |
|------------------------------|-----------------------|
| `instances.json`             | Instances             |
| `holdings.json`              | Holdings records      |
| `items.json`                 | Items                 |
| `authorities.json`           | Authorities           |
| `linked-data-instances.json` | Linked-data instances |
| `linked-data-works.json`     | Linked-data works     |
| `linked-data-hubs.json`      | Linked-data hubs      |
| `locations.json`             | Locations             |
| `libraries.json`             | Libraries             |
| `institutions.json`          | Institutions          |
| `campuses.json`              | Campuses              |

### test-data.db

`test-data.db` is a local SQLite utility that mirrors `instances.json`, `holdings.json`,
`items.json`, and `authorities.json` in a relational, queryable form. It is the recommended way
to explore, modify, and maintain those files without hand-editing large JSON arrays. The database
is **gitignored** — it lives only on your machine. Use `import.py` to populate it from the JSON
files, edit records with any SQLite client (DBeaver, DataGrip, `sqlite3` CLI), then regenerate
the JSON with `export.py`.

## Prerequisites

Python 3 (stdlib only — no dependencies).

## Setup

Seed the database from the current JSON files (run once):

```bash
python manage/import.py
# Imported 119 instances, 65 holdings, 146 items, 132 authorities
```

## Editing data

Edit records directly in the DB, then regenerate the JSON:

```bash
python manage/export.py
# Exported 119 instances, 65 holdings, 146 items, 132 authorities
# Integrity OK
git add ../instances.json ../holdings.json ../items.json ../authorities.json
git commit -m "Update test data"
```

## Adding a new instance (example)

```sql
-- 1. Insert instance
INSERT INTO instances (id, title, source, instanceTypeId, discoverySuppress)
VALUES ('aaaaaaaa-0000-0000-0000-000000000001', 'My Test Title', 'MARC',
        (SELECT id FROM ref_instance_types LIMIT 1), 0);

-- 2. Add contributor
INSERT INTO instance_contributors (instanceId, name, isPrimary)
VALUES ('aaaaaaaa-0000-0000-0000-000000000001', 'Doe, Jane', 1);

-- 3. Add holding
INSERT INTO holdings (id, instanceId, callNumber, permanentLocationId)
VALUES ('bbbbbbbb-0000-0000-0000-000000000001',
        'aaaaaaaa-0000-0000-0000-000000000001',
        'QA76.73.J38',
        (SELECT id FROM ref_locations LIMIT 1));

-- 4. Add item
INSERT INTO items (id, holdingsRecordId, instanceId, materialTypeId, status_name)
VALUES ('cccccccc-0000-0000-0000-000000000001',
        'bbbbbbbb-0000-0000-0000-000000000001',
        'aaaaaaaa-0000-0000-0000-000000000001',
        (SELECT id FROM ref_material_types LIMIT 1), 'Available');
```

Then: `python manage/export.py` and commit the JSON files.

## ⚠️ Updating sub-resource counts after data changes

`SearchBrowseIT` verifies the number of derived sub-resource documents that Elasticsearch contains
after indexing. These counts are hardcoded constants in the test class and **must be kept in sync**
whenever instances (and their contributors, subjects, classifications, call numbers) or authorities
are added, removed, or changed:

```java
// SearchBrowseIT.java
private static final int EXPECTED_AUTHORITY_COUNT       = 132;
private static final int EXPECTED_CALL_NUMBER_COUNT     = 116;
private static final int EXPECTED_CLASSIFICATION_COUNT  = 92;
private static final int EXPECTED_CONTRIBUTOR_COUNT     = 68;
private static final int EXPECTED_SUBJECT_COUNT         = 52;
```

If you change the data and the test fails on `verifyIndexedResourceCounts`, update these constants
to match the new counts reported in the assertion error message.

## Useful queries

`schema.sql` defines convenience views that join all related tables for easy inspection:
`v_instance_full`, `v_holdings_full`, `v_items_full`, `v_authority_full`. Use them instead of
writing multi-table joins by hand.

```sql
-- Full instance with contributors, subjects, classifications, etc.
SELECT * FROM v_instance_full WHERE title LIKE '%keyword%';

-- Full authority with sft/saft headings and identifiers
SELECT * FROM v_authority_full WHERE heading LIKE '%keyword%';

-- Holdings with no items
SELECT h.id, h.callNumber, i.title
FROM holdings h JOIN instances i ON i.id = h.instanceId
WHERE NOT EXISTS (SELECT 1 FROM items WHERE holdingsRecordId = h.id);

-- Instances with no holdings
SELECT id, title FROM instances
WHERE NOT EXISTS (SELECT 1 FROM holdings WHERE instanceId = instances.id);

-- Browse lookup UUIDs
SELECT id, name FROM ref_locations ORDER BY name;
SELECT id, name FROM ref_material_types ORDER BY name;
SELECT id, name FROM ref_call_number_types ORDER BY name;
```

## Scope

`instances.json`, `holdings.json`, `items.json`, and `authorities.json` are managed via
`test-data.db` (import → edit → export workflow above).

The remaining files (`linked-data-*.json`, `locations.json`, `libraries.json`,
`institutions.json`, `campuses.json`) are edited manually.
