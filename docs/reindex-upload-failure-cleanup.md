# Reindex Upload-Phase Failure — Data Cleanup (test environment only)

## Purpose

A reindex can fail in its **upload phase** when a single OpenSearch document grows too large: an
instance with too many holdings/items, or a shared browse entity (subject, classification,
contributor, call number) referenced by too many instances. This runbook locates that oversized data
and removes it on a **testing environment** so the reindex can complete:

- **Diagnostics** (§1) run on the **`mod-search`** database to find the oversized data.
- **Cleanup** (§2) runs on the **`mod-inventory-storage`** database to remove it — row deletes for
  items/holdings, JSONB edits for the rest.

> ⚠️ These scripts delete and modify data. Only run them on a testing environment against data you
> are authorized to remove, and confirm the offending records with the diagnostic queries first.

## Schema placeholders

Replace these placeholders before running the scripts:

| Placeholder               | Example                | Notes                                                                                         |
|---------------------------|------------------------|-----------------------------------------------------------------------------------------------|
| `<tenant>`                | `test_tenant`          | Tenant id used as the schema prefix (`<tenant>_mod_search`, `<tenant>_mod_inventory_storage`) |
| `<instance_ids>`          | `'id-1', 'id-2'`       | Comma-separated list of quoted instance ids to clean up (holdings cleanup, §2.2).             |
| `<holdings_record_ids>`   | `'id-1', 'id-2'`       | Comma-separated list of quoted holdings record ids to clean up (items cleanup, §2.1).         |
| `<subject_value>`         | `World War, 1939-1945` | Subject value from diagnostic query 4 (subjects cleanup, §2.3).                               |
| `<contributor_name>`      | `Smith, John`          | Contributor name from diagnostic query 6 (contributors cleanup, §2.4).                        |
| `<classification_number>` | `QA76.73`              | Classification number from diagnostic query 5 (classifications cleanup, §2.5).                |
| `<call_number>`           | `PS3552.R6858`         | Call number from diagnostic query 7 (call numbers cleanup, §2.6).                             |

---

## 1. Diagnostic queries (run on the `mod-search` database)

These identify which instance (or which shared entity) aggregates an unusually high number of
related records. An outlier at the top of any of these lists is a likely cause of the upload failure.

Each query groups by `tenant_id`. In a consortium the central `mod_search` schema holds every member's
data, so an outlier's `tenant_id` identifies **which member's `mod_inventory_storage` schema** to
clean up.

```sql
-- 1. Holdings per instance (top 10)
SELECT h.tenant_id,
       h.instance_id,
       COUNT(*) AS holding_count
FROM <tenant>_mod_search.holding h
GROUP BY h.tenant_id, h.instance_id
ORDER BY holding_count DESC
LIMIT 10;

-- 2. Items per holding (top 10)
SELECT i.tenant_id,
       i.holding_id,
       COUNT(*) AS item_count
FROM <tenant>_mod_search.item i
GROUP BY i.tenant_id, i.holding_id
ORDER BY item_count DESC
LIMIT 10;

-- 3. Items per instance (top 10)
SELECT i.tenant_id,
       i.instance_id,
       COUNT(*) AS item_count
FROM <tenant>_mod_search.item i
GROUP BY i.tenant_id, i.instance_id
ORDER BY item_count DESC
LIMIT 10;

-- 4. Instances per subject value (top 10)
SELECT ins.tenant_id,
       s.value AS subject_value,
       COUNT(DISTINCT ins.instance_id) AS instance_count
FROM <tenant>_mod_search.instance_subject ins
JOIN <tenant>_mod_search.subject s ON s.id = ins.subject_id
GROUP BY ins.tenant_id, s.value
ORDER BY instance_count DESC
LIMIT 10;

-- 5. Instances per classification number (top 10)
SELECT ic.tenant_id,
       c.number AS classification_number,
       COUNT(DISTINCT ic.instance_id) AS instance_count
FROM <tenant>_mod_search.instance_classification ic
JOIN <tenant>_mod_search.classification c ON c.id = ic.classification_id
GROUP BY ic.tenant_id, c.number
ORDER BY instance_count DESC
LIMIT 10;

-- 6. Instances per contributor name (top 10)
SELECT ic.tenant_id,
       c.name AS contributor_name,
       COUNT(DISTINCT ic.instance_id) AS instance_count
FROM <tenant>_mod_search.instance_contributor ic
JOIN <tenant>_mod_search.contributor c ON c.id = ic.contributor_id
GROUP BY ic.tenant_id, c.name
ORDER BY instance_count DESC
LIMIT 10;

-- 7. Instances per call number (top 10)
SELECT icn.tenant_id,
       cn.call_number,
       COUNT(DISTINCT icn.instance_id) AS instance_count
FROM <tenant>_mod_search.instance_call_number icn
JOIN <tenant>_mod_search.call_number cn ON cn.id = icn.call_number_id
GROUP BY icn.tenant_id, cn.call_number
ORDER BY instance_count DESC
LIMIT 10;
```

---

## 2. Cleanup (run on the `mod-inventory-storage` database)

Cleanup is per entity type in two shapes: **items and holdings** are tables, so rows are deleted in
batches; **subjects, classifications, contributors, and call numbers** live in the JSONB of `instance`
and `item`, so the offending entry is stripped from the JSONB in batches.

> **Triggers:** each procedure runs between `ALTER TABLE … DISABLE TRIGGER USER` and
> `… ENABLE TRIGGER USER`, suspending the auditing/metadata triggers while keeping foreign-key
> enforcement. Always re-enable them afterwards, even if a procedure fails part-way through.

> **Delete order:** delete items before their holdings — the item → holding foreign key stays
> enforced. JSONB edits update rows in place and have no such constraint.

> **JSONB sections:** take the value from diagnostic queries 4–7 (run on **mod-search**) and paste it
> into the update run on **mod-inventory-storage**. Matching by value removes every dedup variant that
> shares it (e.g. the same subject value under different authorities).

### 2.1 Items

Deletes items on the given holdings. Substitute `<holdings_record_ids>`.

```sql
ALTER TABLE <tenant>_mod_inventory_storage.item DISABLE TRIGGER USER;

CREATE OR REPLACE PROCEDURE <tenant>_mod_inventory_storage.delete_items_batched()
LANGUAGE plpgsql AS $$
DECLARE
  deleted_count INT;
BEGIN
  LOOP
    DELETE FROM <tenant>_mod_inventory_storage.item
    WHERE id IN (
      SELECT id FROM <tenant>_mod_inventory_storage.item
      WHERE holdingsrecordid IN (
        <holdings_record_ids>
      )
      LIMIT 10000
    );
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    COMMIT;
    EXIT WHEN deleted_count = 0;
  END LOOP;
END $$;

CALL <tenant>_mod_inventory_storage.delete_items_batched();
DROP PROCEDURE <tenant>_mod_inventory_storage.delete_items_batched();

ALTER TABLE <tenant>_mod_inventory_storage.item ENABLE TRIGGER USER;
```

### 2.2 Holdings

Deletes holdings on the given instances; delete their items first (§2.1). Substitute `<instance_ids>`.

```sql
ALTER TABLE <tenant>_mod_inventory_storage.holdings_record DISABLE TRIGGER USER;

CREATE OR REPLACE PROCEDURE <tenant>_mod_inventory_storage.delete_holdings_batched()
LANGUAGE plpgsql AS $$
DECLARE
  deleted_count INT;
BEGIN
  LOOP
    DELETE FROM <tenant>_mod_inventory_storage.holdings_record
    WHERE id IN (
      SELECT id FROM <tenant>_mod_inventory_storage.holdings_record
      WHERE instanceid IN (
        <instance_ids>
      )
      LIMIT 10000
    );
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    COMMIT;
    EXIT WHEN deleted_count = 0;
  END LOOP;
END $$;

CALL <tenant>_mod_inventory_storage.delete_holdings_batched();
DROP PROCEDURE <tenant>_mod_inventory_storage.delete_holdings_batched();

ALTER TABLE <tenant>_mod_inventory_storage.holdings_record ENABLE TRIGGER USER;
```

### 2.3 Subjects

Removes every subject with the given value from every instance that carries it. Substitute
`<subject_value>`.

```sql
ALTER TABLE <tenant>_mod_inventory_storage.instance DISABLE TRIGGER USER;

CREATE OR REPLACE PROCEDURE <tenant>_mod_inventory_storage.strip_subject_batched()
LANGUAGE plpgsql AS $$
DECLARE
  processed_count INT;
BEGIN
  DROP TABLE IF EXISTS tmp_target_ids;
  CREATE TEMP TABLE tmp_target_ids (id UUID PRIMARY KEY);

  -- one sequential scan to collect every instance carrying the subject value
  INSERT INTO tmp_target_ids (id)
  SELECT i.id
  FROM <tenant>_mod_inventory_storage.instance i
  WHERE EXISTS (
    SELECT 1 FROM jsonb_array_elements(i.jsonb->'subjects') s
    WHERE s->>'value' = '<subject_value>'
  );
  COMMIT;

  LOOP
    WITH batch AS (
      DELETE FROM tmp_target_ids
      WHERE ctid IN (SELECT ctid FROM tmp_target_ids LIMIT 5000)
      RETURNING id
    )
    UPDATE <tenant>_mod_inventory_storage.instance i
    SET jsonb = jsonb_set(i.jsonb, '{subjects}', (
      SELECT coalesce(jsonb_agg(s), '[]'::jsonb)
      FROM jsonb_array_elements(i.jsonb->'subjects') s
      WHERE s->>'value' IS DISTINCT FROM '<subject_value>'
    ))
    FROM batch b
    WHERE i.id = b.id;
    GET DIAGNOSTICS processed_count = ROW_COUNT;
    COMMIT;
    EXIT WHEN processed_count = 0;
  END LOOP;

  DROP TABLE IF EXISTS tmp_target_ids;
END $$;

CALL <tenant>_mod_inventory_storage.strip_subject_batched();
DROP PROCEDURE <tenant>_mod_inventory_storage.strip_subject_batched();

ALTER TABLE <tenant>_mod_inventory_storage.instance ENABLE TRIGGER USER;
```

### 2.4 Contributors

Removes every contributor with the given name from every instance that carries it. Substitute
`<contributor_name>`.

```sql
ALTER TABLE <tenant>_mod_inventory_storage.instance DISABLE TRIGGER USER;

CREATE OR REPLACE PROCEDURE <tenant>_mod_inventory_storage.strip_contributor_batched()
LANGUAGE plpgsql AS $$
DECLARE
  processed_count INT;
BEGIN
  DROP TABLE IF EXISTS tmp_target_ids;
  CREATE TEMP TABLE tmp_target_ids (id UUID PRIMARY KEY);

  INSERT INTO tmp_target_ids (id)
  SELECT i.id
  FROM <tenant>_mod_inventory_storage.instance i
  WHERE EXISTS (
    SELECT 1 FROM jsonb_array_elements(i.jsonb->'contributors') c
    WHERE c->>'name' = '<contributor_name>'
  );
  COMMIT;

  LOOP
    WITH batch AS (
      DELETE FROM tmp_target_ids
      WHERE ctid IN (SELECT ctid FROM tmp_target_ids LIMIT 5000)
      RETURNING id
    )
    UPDATE <tenant>_mod_inventory_storage.instance i
    SET jsonb = jsonb_set(i.jsonb, '{contributors}', (
      SELECT coalesce(jsonb_agg(c), '[]'::jsonb)
      FROM jsonb_array_elements(i.jsonb->'contributors') c
      WHERE c->>'name' IS DISTINCT FROM '<contributor_name>'
    ))
    FROM batch b
    WHERE i.id = b.id;
    GET DIAGNOSTICS processed_count = ROW_COUNT;
    COMMIT;
    EXIT WHEN processed_count = 0;
  END LOOP;

  DROP TABLE IF EXISTS tmp_target_ids;
END $$;

CALL <tenant>_mod_inventory_storage.strip_contributor_batched();
DROP PROCEDURE <tenant>_mod_inventory_storage.strip_contributor_batched();

ALTER TABLE <tenant>_mod_inventory_storage.instance ENABLE TRIGGER USER;
```

### 2.5 Classifications

Removes every classification with the given number from every instance that carries it. Substitute
`<classification_number>`.

```sql
ALTER TABLE <tenant>_mod_inventory_storage.instance DISABLE TRIGGER USER;

CREATE OR REPLACE PROCEDURE <tenant>_mod_inventory_storage.strip_classification_batched()
LANGUAGE plpgsql AS $$
DECLARE
  processed_count INT;
BEGIN
  DROP TABLE IF EXISTS tmp_target_ids;
  CREATE TEMP TABLE tmp_target_ids (id UUID PRIMARY KEY);

  INSERT INTO tmp_target_ids (id)
  SELECT i.id
  FROM <tenant>_mod_inventory_storage.instance i
  WHERE EXISTS (
    SELECT 1 FROM jsonb_array_elements(i.jsonb->'classifications') c
    WHERE c->>'classificationNumber' = '<classification_number>'
  );
  COMMIT;

  LOOP
    WITH batch AS (
      DELETE FROM tmp_target_ids
      WHERE ctid IN (SELECT ctid FROM tmp_target_ids LIMIT 5000)
      RETURNING id
    )
    UPDATE <tenant>_mod_inventory_storage.instance i
    SET jsonb = jsonb_set(i.jsonb, '{classifications}', (
      SELECT coalesce(jsonb_agg(c), '[]'::jsonb)
      FROM jsonb_array_elements(i.jsonb->'classifications') c
      WHERE c->>'classificationNumber' IS DISTINCT FROM '<classification_number>'
    ))
    FROM batch b
    WHERE i.id = b.id;
    GET DIAGNOSTICS processed_count = ROW_COUNT;
    COMMIT;
    EXIT WHEN processed_count = 0;
  END LOOP;

  DROP TABLE IF EXISTS tmp_target_ids;
END $$;

CALL <tenant>_mod_inventory_storage.strip_classification_batched();
DROP PROCEDURE <tenant>_mod_inventory_storage.strip_classification_batched();

ALTER TABLE <tenant>_mod_inventory_storage.instance ENABLE TRIGGER USER;
```

### 2.6 Call numbers

Removes the whole `effectiveCallNumberComponents` object from every item whose effective `callNumber`
matches the given value, dropping those items from the call-number aggregation. This is safe: the
field is not required, is recomputed by the inventory application layer (not a DB trigger) only when
an item is next saved via the API, and holdings are left untouched. Substitute `<call_number>`.

```sql
ALTER TABLE <tenant>_mod_inventory_storage.item DISABLE TRIGGER USER;

CREATE OR REPLACE PROCEDURE <tenant>_mod_inventory_storage.strip_call_number_batched()
LANGUAGE plpgsql AS $$
DECLARE
  processed_count INT;
BEGIN
  DROP TABLE IF EXISTS tmp_target_ids;
  CREATE TEMP TABLE tmp_target_ids (id UUID PRIMARY KEY);

  -- one sequential scan to collect every item whose effective call number matches
  INSERT INTO tmp_target_ids (id)
  SELECT it.id
  FROM <tenant>_mod_inventory_storage.item it
  WHERE it.jsonb#>>'{effectiveCallNumberComponents,callNumber}' = '<call_number>';
  COMMIT;

  LOOP
    WITH batch AS (
      DELETE FROM tmp_target_ids
      WHERE ctid IN (SELECT ctid FROM tmp_target_ids LIMIT 5000)
      RETURNING id
    )
    UPDATE <tenant>_mod_inventory_storage.item it
    SET jsonb = it.jsonb - 'effectiveCallNumberComponents'
    FROM batch b
    WHERE it.id = b.id;
    GET DIAGNOSTICS processed_count = ROW_COUNT;
    COMMIT;
    EXIT WHEN processed_count = 0;
  END LOOP;

  DROP TABLE IF EXISTS tmp_target_ids;
END $$;

CALL <tenant>_mod_inventory_storage.strip_call_number_batched();
DROP PROCEDURE <tenant>_mod_inventory_storage.strip_call_number_batched();

ALTER TABLE <tenant>_mod_inventory_storage.item ENABLE TRIGGER USER;
```

---

## After cleanup

Cleanup only changed `mod-inventory-storage`, so run a fresh **full** reindex
(`POST /search/index/instance-records/reindex/full`) — its merge phase re-reads the cleaned data
before the upload retries. Re-run the diagnostics after the merge to confirm. See
[reindex.md](features/reindex.md).
