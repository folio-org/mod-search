#!/usr/bin/env python3
"""
import.py — Seed test-data.db from instances.json, holdings.json, items.json.
Run from any directory. Idempotent: drops and recreates all data tables.

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
                dateTypeId, date1, date2)
               VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
            (
                r["id"],
                r.get("title"),
                r.get("indexTitle"),
                r.get("source"),
                r.get("instanceTypeId"),
                r.get("statusId"),
                1 if r.get("discoverySuppress") else 0,
                1 if r.get("staffSuppress") else 0,
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
                "INSERT INTO instance_subjects (instanceId, value, authorityId) VALUES (?,?,?)",
                (iid, s.get("value"), s.get("authorityId")),
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


def import_holdings(cur, records):
    for r in records:
        upsert_ref(cur, "ref_locations",         r.get("permanentLocationId"))
        upsert_ref(cur, "ref_call_number_types",  r.get("callNumberTypeId"))
        upsert_ref(cur, "ref_holdings_types",     r.get("holdingsTypeId"))
        cur.execute(
            """INSERT OR REPLACE INTO holdings
               (id, instanceId, callNumber, callNumberPrefix, callNumberSuffix,
                callNumberTypeId, permanentLocationId, holdingsTypeId,
                copyNumber, shelvingTitle, discoverySuppress)
               VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
            (
                r["id"],
                r.get("instanceId"),
                r.get("callNumber"),
                r.get("callNumberPrefix"),
                r.get("callNumberSuffix"),
                r.get("callNumberTypeId"),
                r.get("permanentLocationId"),
                r.get("holdingsTypeId"),
                r.get("copyNumber"),
                r.get("shelvingTitle"),
                1 if r.get("discoverySuppress") else 0,
            ),
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
                itemLevelCallNumber, itemLevelCallNumberTypeId,
                effectiveLocationId,
                effectiveCallNumber, effectiveCallNumberPrefix,
                effectiveCallNumberSuffix, effectiveCallNumberTypeId,
                materialTypeId, status_name, status_date, discoverySuppress)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            (
                r["id"],
                r.get("holdingsRecordId"),
                r.get("instanceId"),
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


def main():
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
