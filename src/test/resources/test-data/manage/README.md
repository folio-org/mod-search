# Test Data Manager

SQLite-backed developer tool for managing `instances.json`, `holdings.json`, and `items.json`.

## Prerequisites

Python 3 (stdlib only — no dependencies).

## Setup

Seed the database from the current JSON files (run once):

```bash
python manage/import.py
# Imported 119 instances, 65 holdings, 146 items
```

The DB file (`manage/test-data.db`) is gitignored. Open it with any SQLite tool — DBeaver, DataGrip, or the `sqlite3` CLI.

## Editing data

Edit records directly in the DB, then regenerate the JSON:

```bash
python manage/export.py
# Exported 119 instances, 65 holdings, 146 items
# Integrity OK
git add ../instances.json ../holdings.json ../items.json
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

## Useful queries

```sql
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

Only `instances.json`, `holdings.json`, `items.json` are managed here.
`authorities.json` and `linked-data-*.json` are edited manually.
