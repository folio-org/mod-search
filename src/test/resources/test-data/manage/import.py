#!/usr/bin/env python3
"""
import.py — Seed test-data.db from instances.json, holdings.json, items.json.
Run from any directory. Always recreates DB from scratch.

Usage:
    python manage/import.py
"""

import json
import sqlite3
from pathlib import Path

MANAGE_DIR = Path(__file__).parent
DATA_DIR   = MANAGE_DIR.parent
DB_PATH    = MANAGE_DIR / "test-data.db"
SCHEMA     = MANAGE_DIR / "schema.sql"


def upsert_ref(cur, table, id_val):
    """Insert a UUID into a lookup table if not already present."""
    if id_val:
        cur.execute(
            f"INSERT OR IGNORE INTO {table} (id, name) VALUES (?, ?)",
            (id_val, id_val),
        )


def import_instances(cur, records):
    for r in records:
        dates = r.get("dates") or {}
        upsert_ref(cur, "ref_instance_types", r.get("instanceTypeId"))
        cur.execute(
            """INSERT OR REPLACE INTO instances
               (id, title, indexTitle, source, instanceTypeId, statusId,
                discoverySuppress, staffSuppress,
                hrid, modeOfIssuanceId, isBoundWith, shared,
                dateTypeId, date1, date2)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            (
                r["id"],
                r.get("title"),
                r.get("indexTitle"),
                r.get("source"),
                r.get("instanceTypeId"),
                r.get("statusId"),
                1 if r.get("discoverySuppress") else 0,
                1 if r.get("staffSuppress") else 0,
                r.get("hrid"),
                r.get("modeOfIssuanceId"),
                1 if r.get("isBoundWith") else 0,
                1 if r.get("shared") else 0,
                dates.get("dateTypeId"),
                dates.get("date1"),
                dates.get("date2"),
            ),
        )

        iid = r["id"]

        for c in r.get("contributors", []):
            upsert_ref(cur, "ref_contributor_name_types", c.get("contributorNameTypeId"))
            upsert_ref(cur, "ref_contributor_types",      c.get("contributorTypeId"))
            cur.execute(
                """INSERT INTO instance_contributors
                   (instanceId, name, contributorNameTypeId, contributorTypeId,
                    contributorTypeText, authorityId, isPrimary)
                   VALUES (?,?,?,?,?,?,?)""",
                (
                    iid,
                    c.get("name"),
                    c.get("contributorNameTypeId"),
                    c.get("contributorTypeId"),
                    c.get("contributorTypeText"),
                    c.get("authorityId"),
                    1 if c.get("primary") else 0,
                ),
            )

        for s in r.get("subjects", []):
            cur.execute(
                """INSERT INTO instance_subjects
                   (instanceId, value, authorityId, sourceId, typeId)
                   VALUES (?,?,?,?,?)""",
                (iid, s.get("value"), s.get("authorityId"), s.get("sourceId"), s.get("typeId")),
            )

        for cl in r.get("classifications", []):
            cur.execute(
                "INSERT INTO instance_classifications (instanceId, classificationNumber, classificationTypeId) VALUES (?,?,?)",
                (iid, cl.get("classificationNumber"), cl.get("classificationTypeId")),
            )

        for lang in r.get("languages", []):
            cur.execute(
                "INSERT INTO instance_languages (instanceId, languageCode) VALUES (?,?)",
                (iid, lang),
            )

        for ident in r.get("identifiers", []):
            upsert_ref(cur, "ref_identifier_types", ident.get("identifierTypeId"))
            cur.execute(
                "INSERT INTO instance_identifiers (instanceId, value, identifierTypeId) VALUES (?,?,?)",
                (iid, ident.get("value"), ident.get("identifierTypeId")),
            )

        for s in r.get("series", []):
            cur.execute(
                "INSERT INTO instance_series (instanceId, value, authorityId) VALUES (?,?,?)",
                (iid, s.get("value"), s.get("authorityId")),
            )

        for at in r.get("alternativeTitles", []):
            upsert_ref(cur, "ref_alternative_title_types", at.get("alternativeTitleTypeId"))
            cur.execute(
                "INSERT INTO instance_alternative_titles (instanceId, alternativeTitle, alternativeTitleTypeId, authorityId) VALUES (?,?,?,?)",
                (iid, at.get("alternativeTitle"), at.get("alternativeTitleTypeId"), at.get("authorityId")),
            )

        for pub in r.get("publication", []):
            cur.execute(
                "INSERT INTO instance_publications (instanceId, publisher, place, dateOfPublication, role) VALUES (?,?,?,?,?)",
                (iid, pub.get("publisher"), pub.get("place"), pub.get("dateOfPublication"), pub.get("role")),
            )

        for ed in r.get("editions", []):
            cur.execute(
                "INSERT INTO instance_editions (instanceId, value) VALUES (?,?)",
                (iid, ed),
            )

        for fid in r.get("instanceFormatIds", []):
            cur.execute(
                "INSERT INTO instance_format_ids (instanceId, formatId) VALUES (?,?)",
                (iid, fid),
            )

        for sc in r.get("statisticalCodeIds", []):
            cur.execute(
                "INSERT INTO instance_statistical_code_ids (instanceId, statisticalCodeId) VALUES (?,?)",
                (iid, sc),
            )

        for term in r.get("natureOfContentTermIds", []):
            cur.execute(
                "INSERT INTO instance_nature_of_content_term_ids (instanceId, termId) VALUES (?,?)",
                (iid, term),
            )

        for tag in (r.get("tags") or {}).get("tagList", []):
            cur.execute(
                "INSERT INTO instance_tags (instanceId, tagValue) VALUES (?,?)",
                (iid, tag),
            )

        for note in r.get("administrativeNotes", []):
            cur.execute(
                "INSERT INTO instance_administrative_notes (instanceId, note) VALUES (?,?)",
                (iid, note),
            )

        for n in r.get("notes", []):
            cur.execute(
                "INSERT INTO instance_notes (instanceId, note, staffOnly) VALUES (?,?,?)",
                (iid, n.get("note"), 1 if n.get("staffOnly") else 0),
            )

        for ea in r.get("electronicAccess", []):
            cur.execute(
                """INSERT INTO instance_electronic_access
                   (instanceId, uri, linkText, materialsSpecification, publicNote, relationshipId)
                   VALUES (?,?,?,?,?,?)""",
                (iid, ea.get("uri"), ea.get("linkText"), ea.get("materialsSpecification"),
                 ea.get("publicNote"), ea.get("relationshipId")),
            )

        meta = r.get("metadata")
        if meta:
            cur.execute(
                """INSERT OR REPLACE INTO instance_metadata
                   (instanceId, createdDate, createdByUserId, updatedDate, updatedByUserId)
                   VALUES (?,?,?,?,?)""",
                (iid, meta.get("createdDate"), meta.get("createdByUserId"),
                 meta.get("updatedDate"), meta.get("updatedByUserId")),
            )


def import_holdings(cur, records):
    for r in records:
        upsert_ref(cur, "ref_locations",         r.get("permanentLocationId"))
        upsert_ref(cur, "ref_call_number_types",  r.get("callNumberTypeId"))
        upsert_ref(cur, "ref_holdings_types",     r.get("holdingsTypeId"))
        cur.execute(
            """INSERT OR REPLACE INTO holdings
               (id, instanceId, callNumber, callNumberPrefix, callNumberSuffix,
                callNumberTypeId, permanentLocationId, holdingsTypeId,
                hrid, sourceId,
                copyNumber, shelvingTitle, discoverySuppress)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            (
                r["id"],
                r.get("instanceId"),
                r.get("callNumber"),
                r.get("callNumberPrefix"),
                r.get("callNumberSuffix"),
                r.get("callNumberTypeId"),
                r.get("permanentLocationId"),
                r.get("holdingsTypeId"),
                r.get("hrid"),
                r.get("sourceId"),
                r.get("copyNumber"),
                r.get("shelvingTitle"),
                1 if r.get("discoverySuppress") else 0,
            ),
        )

        hid = r["id"]

        for sc in r.get("statisticalCodeIds", []):
            cur.execute(
                "INSERT INTO holdings_statistical_code_ids (holdingsId, statisticalCodeId) VALUES (?,?)",
                (hid, sc),
            )

        for fid in r.get("formerIds", []):
            cur.execute(
                "INSERT INTO holdings_former_ids (holdingsId, formerId) VALUES (?,?)",
                (hid, fid),
            )

        for tag in (r.get("tags") or {}).get("tagList", []):
            cur.execute(
                "INSERT INTO holdings_tags (holdingsId, tagValue) VALUES (?,?)",
                (hid, tag),
            )

        for note in r.get("administrativeNotes", []):
            cur.execute(
                "INSERT INTO holdings_administrative_notes (holdingsId, note) VALUES (?,?)",
                (hid, note),
            )

        for n in r.get("notes", []):
            cur.execute(
                "INSERT INTO holdings_notes (holdingsId, note, staffOnly) VALUES (?,?,?)",
                (hid, n.get("note"), 1 if n.get("staffOnly") else 0),
            )

        for ea in r.get("electronicAccess", []):
            cur.execute(
                """INSERT INTO holdings_electronic_access
                   (holdingsId, uri, linkText, materialsSpecification, publicNote, relationshipId)
                   VALUES (?,?,?,?,?,?)""",
                (hid, ea.get("uri"), ea.get("linkText"), ea.get("materialsSpecification"),
                 ea.get("publicNote"), ea.get("relationshipId")),
            )

        meta = r.get("metadata")
        if meta:
            cur.execute(
                """INSERT OR REPLACE INTO holdings_metadata
                   (holdingsId, createdDate, createdByUserId, updatedDate, updatedByUserId)
                   VALUES (?,?,?,?,?)""",
                (hid, meta.get("createdDate"), meta.get("createdByUserId"),
                 meta.get("updatedDate"), meta.get("updatedByUserId")),
            )


def import_items(cur, records):
    for r in records:
        enc = r.get("effectiveCallNumberComponents") or {}
        status = r.get("status") or {}
        upsert_ref(cur, "ref_locations",        r.get("effectiveLocationId"))
        upsert_ref(cur, "ref_material_types",   r.get("materialTypeId"))
        upsert_ref(cur, "ref_call_number_types", r.get("itemLevelCallNumberTypeId"))
        upsert_ref(cur, "ref_call_number_types", enc.get("typeId"))
        cur.execute(
            """INSERT OR REPLACE INTO items
               (id, holdingsRecordId, instanceId,
                hrid, barcode, accessionNumber, itemIdentifier,
                itemLevelCallNumber, itemLevelCallNumberTypeId,
                effectiveLocationId,
                effectiveCallNumber, effectiveCallNumberPrefix,
                effectiveCallNumberSuffix, effectiveCallNumberTypeId,
                materialTypeId, status_name, status_date, discoverySuppress)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            (
                r["id"],
                r.get("holdingsRecordId"),
                r.get("instanceId"),
                r.get("hrid"),
                r.get("barcode"),
                r.get("accessionNumber"),
                r.get("itemIdentifier"),
                r.get("itemLevelCallNumber"),
                r.get("itemLevelCallNumberTypeId"),
                r.get("effectiveLocationId"),
                enc.get("callNumber"),
                enc.get("prefix"),
                enc.get("suffix"),
                enc.get("typeId"),
                r.get("materialTypeId"),
                status.get("name"),
                status.get("date"),
                1 if r.get("discoverySuppress") else 0,
            ),
        )

        iid = r["id"]

        for fid in r.get("formerIds", []):
            cur.execute(
                "INSERT INTO items_former_ids (itemId, formerId) VALUES (?,?)",
                (iid, fid),
            )

        for sc in r.get("statisticalCodeIds", []):
            cur.execute(
                "INSERT INTO items_statistical_code_ids (itemId, statisticalCodeId) VALUES (?,?)",
                (iid, sc),
            )

        for tag in (r.get("tags") or {}).get("tagList", []):
            cur.execute(
                "INSERT INTO items_tags (itemId, tagValue) VALUES (?,?)",
                (iid, tag),
            )

        for note in r.get("administrativeNotes", []):
            cur.execute(
                "INSERT INTO items_administrative_notes (itemId, note) VALUES (?,?)",
                (iid, note),
            )

        for n in r.get("notes", []):
            cur.execute(
                "INSERT INTO items_notes (itemId, note, staffOnly) VALUES (?,?,?)",
                (iid, n.get("note"), 1 if n.get("staffOnly") else 0),
            )

        for cn in r.get("circulationNotes", []):
            cur.execute(
                "INSERT INTO items_circulation_notes (itemId, note, staffOnly) VALUES (?,?,?)",
                (iid, cn.get("note"), 1 if cn.get("staffOnly") else 0),
            )

        for ea in r.get("electronicAccess", []):
            cur.execute(
                """INSERT INTO items_electronic_access
                   (itemId, uri, linkText, materialsSpecification, publicNote, relationshipId)
                   VALUES (?,?,?,?,?,?)""",
                (iid, ea.get("uri"), ea.get("linkText"), ea.get("materialsSpecification"),
                 ea.get("publicNote"), ea.get("relationshipId")),
            )

        meta = r.get("metadata")
        if meta:
            cur.execute(
                """INSERT OR REPLACE INTO items_metadata
                   (itemId, createdDate, createdByUserId, updatedDate, updatedByUserId)
                   VALUES (?,?,?,?,?)""",
                (iid, meta.get("createdDate"), meta.get("createdByUserId"),
                 meta.get("updatedDate"), meta.get("updatedByUserId")),
            )


def main():
    DB_PATH.unlink(missing_ok=True)
    con = sqlite3.connect(DB_PATH)
    con.execute("PRAGMA foreign_keys = ON")

    # Apply schema
    con.executescript(SCHEMA.read_text())

    # Clear existing data (not lookup tables — preserve edited names)
    junction_tables = [
        "instance_contributors", "instance_subjects", "instance_classifications",
        "instance_languages", "instance_identifiers", "instance_series",
        "instance_alternative_titles", "instance_publications",
        "instance_editions", "instance_format_ids",
        "instance_statistical_code_ids", "instance_nature_of_content_term_ids",
        "instance_tags", "instance_administrative_notes", "instance_notes",
        "instance_electronic_access", "instance_metadata",
        "holdings_statistical_code_ids", "holdings_former_ids",
        "holdings_tags", "holdings_administrative_notes", "holdings_notes",
        "holdings_electronic_access", "holdings_metadata",
        "items_former_ids", "items_statistical_code_ids",
        "items_tags", "items_administrative_notes", "items_notes",
        "items_circulation_notes", "items_electronic_access", "items_metadata",
    ]
    for t in junction_tables:
        con.execute(f"DELETE FROM {t}")
    con.execute("DELETE FROM items")
    con.execute("DELETE FROM holdings")
    con.execute("DELETE FROM instances")
    con.commit()

    cur = con.cursor()

    instances = json.loads((DATA_DIR / "instances.json").read_text())
    holdings  = json.loads((DATA_DIR / "holdings.json").read_text())
    items     = json.loads((DATA_DIR / "items.json").read_text())

    import_instances(cur, instances)
    import_holdings(cur, holdings)
    import_items(cur, items)

    con.commit()
    con.close()

    print(f"Imported {len(instances)} instances, {len(holdings)} holdings, {len(items)} items")


if __name__ == "__main__":
    main()
