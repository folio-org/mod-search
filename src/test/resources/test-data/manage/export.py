#!/usr/bin/env python3
"""
export.py — Regenerate instances.json, holdings.json, items.json from test-data.db.
Run from any directory. Overwrites the three JSON files.

Usage:
    python manage/export.py
"""

import json
import sqlite3
from pathlib import Path

MANAGE_DIR = Path(__file__).parent
DATA_DIR   = MANAGE_DIR.parent
DB_PATH    = MANAGE_DIR / "test-data.db"


def rows_as_dicts(cur, query, params=()):
    cur.execute(query, params)
    cols = [d[0] for d in cur.description]
    return [dict(zip(cols, row)) for row in cur.fetchall()]


def export_instances(cur):
    instances = rows_as_dicts(cur, "SELECT * FROM instances ORDER BY rowid")
    result = []
    for inst in instances:
        iid = inst["id"]

        contributors = []
        for c in rows_as_dicts(cur, "SELECT * FROM instance_contributors WHERE instanceId=? ORDER BY id", (iid,)):
            obj = {"name": c["name"]}
            if c.get("contributorNameTypeId"): obj["contributorNameTypeId"] = c["contributorNameTypeId"]
            if c.get("contributorTypeId"):     obj["contributorTypeId"]     = c["contributorTypeId"]
            if c.get("contributorTypeText") is not None: obj["contributorTypeText"] = c["contributorTypeText"]
            if c.get("authorityId"):           obj["authorityId"]           = c["authorityId"]
            if c["isPrimary"]:                 obj["primary"]               = True
            contributors.append(obj)

        subjects = [
            {k: v for k, v in {"value": s["value"], "authorityId": s.get("authorityId")}.items() if v is not None}
            for s in rows_as_dicts(cur, "SELECT * FROM instance_subjects WHERE instanceId=? ORDER BY id", (iid,))
        ]

        classifications = [
            {"classificationNumber": cl["classificationNumber"], "classificationTypeId": cl["classificationTypeId"]}
            for cl in rows_as_dicts(cur, "SELECT * FROM instance_classifications WHERE instanceId=? ORDER BY id", (iid,))
        ]

        languages = [
            r["languageCode"]
            for r in rows_as_dicts(cur, "SELECT * FROM instance_languages WHERE instanceId=? ORDER BY id", (iid,))
        ]

        identifiers = [
            {"value": i["value"], "identifierTypeId": i["identifierTypeId"]}
            for i in rows_as_dicts(cur, "SELECT * FROM instance_identifiers WHERE instanceId=? ORDER BY id", (iid,))
        ]

        series = [
            {k: v for k, v in {"value": s["value"], "authorityId": s.get("authorityId")}.items() if v is not None}
            for s in rows_as_dicts(cur, "SELECT * FROM instance_series WHERE instanceId=? ORDER BY id", (iid,))
        ]

        alt_titles = [
            {k: v for k, v in {
                "alternativeTitle": a["alternativeTitle"],
                "alternativeTitleTypeId": a.get("alternativeTitleTypeId"),
                "authorityId": a.get("authorityId"),
            }.items() if v is not None}
            for a in rows_as_dicts(cur, "SELECT * FROM instance_alternative_titles WHERE instanceId=? ORDER BY id", (iid,))
        ]

        publications = [
            {k: v for k, v in {
                "publisher": p.get("publisher"),
                "place": p.get("place"),
                "dateOfPublication": p.get("dateOfPublication"),
                "role": p.get("role"),
            }.items() if v is not None}
            for p in rows_as_dicts(cur, "SELECT * FROM instance_publications WHERE instanceId=? ORDER BY id", (iid,))
        ]

        editions = [
            r["value"]
            for r in rows_as_dicts(cur, "SELECT * FROM instance_editions WHERE instanceId=? ORDER BY id", (iid,))
        ]

        format_ids = [
            r["formatId"]
            for r in rows_as_dicts(cur, "SELECT * FROM instance_format_ids WHERE instanceId=? ORDER BY id", (iid,))
        ]

        # Build dates object (only if any field is set)
        dates = None
        if inst.get("dateTypeId") or inst.get("date1") or inst.get("date2"):
            dates = {k: v for k, v in {
                "dateTypeId": inst.get("dateTypeId"),
                "date1":      inst.get("date1"),
                "date2":      inst.get("date2"),
            }.items() if v is not None}

        obj = {"id": inst["id"]}
        obj["source"]       = inst.get("source")
        obj["title"]        = inst.get("title")
        obj["indexTitle"]   = inst.get("indexTitle")
        if alt_titles:   obj["alternativeTitles"]  = alt_titles
        if editions:     obj["editions"]           = editions
        if series:       obj["series"]             = series
        if identifiers:  obj["identifiers"]        = identifiers
        if contributors: obj["contributors"]       = contributors
        if subjects:     obj["subjects"]           = subjects
        if classifications: obj["classifications"] = classifications
        if publications: obj["publication"]        = publications
        if dates:        obj["dates"]              = dates
        obj["instanceTypeId"]    = inst.get("instanceTypeId")
        if format_ids:   obj["instanceFormatIds"]  = format_ids
        if languages:    obj["languages"]          = languages
        obj["discoverySuppress"] = bool(inst["discoverySuppress"])
        if inst.get("statusId"): obj["statusId"]   = inst["statusId"]

        result.append(obj)

    return result


def export_holdings(cur):
    holdings = rows_as_dicts(cur, "SELECT * FROM holdings ORDER BY rowid")
    result = []
    for h in holdings:
        obj = {"id": h["id"]}
        obj["discoverySuppress"]    = bool(h["discoverySuppress"])
        if h.get("holdingsTypeId"):      obj["holdingsTypeId"]      = h["holdingsTypeId"]
        obj["instanceId"]               = h["instanceId"]
        if h.get("permanentLocationId"): obj["permanentLocationId"] = h["permanentLocationId"]
        if h.get("callNumberTypeId"):    obj["callNumberTypeId"]    = h["callNumberTypeId"]
        if h.get("callNumber"):          obj["callNumber"]          = h["callNumber"]
        if h.get("callNumberPrefix"):    obj["callNumberPrefix"]    = h["callNumberPrefix"]
        if h.get("callNumberSuffix"):    obj["callNumberSuffix"]    = h["callNumberSuffix"]
        if h.get("copyNumber"):          obj["copyNumber"]          = h["copyNumber"]
        if h.get("shelvingTitle"):       obj["shelvingTitle"]       = h["shelvingTitle"]
        result.append(obj)
    return result


def export_items(cur):
    items = rows_as_dicts(cur, "SELECT * FROM items ORDER BY rowid")
    result = []
    for it in items:
        enc = {}
        if it.get("effectiveCallNumberSuffix"): enc["suffix"]     = it["effectiveCallNumberSuffix"]
        if it.get("effectiveCallNumber"):       enc["callNumber"] = it["effectiveCallNumber"]
        if it.get("effectiveCallNumberPrefix"): enc["prefix"]     = it["effectiveCallNumberPrefix"]
        if it.get("effectiveCallNumberTypeId"): enc["typeId"]     = it["effectiveCallNumberTypeId"]

        status = {}
        if it.get("status_name"): status["name"] = it["status_name"]
        if it.get("status_date"): status["date"] = it["status_date"]

        obj = {"id": it["id"]}
        obj["holdingsRecordId"] = it["holdingsRecordId"]
        obj["instanceId"]       = it["instanceId"]
        if it.get("itemLevelCallNumber"):       obj["itemLevelCallNumber"]       = it["itemLevelCallNumber"]
        if it.get("itemLevelCallNumberTypeId"): obj["itemLevelCallNumberTypeId"] = it["itemLevelCallNumberTypeId"]
        if enc:    obj["effectiveCallNumberComponents"] = enc
        if status: obj["status"]              = status
        if it.get("materialTypeId"):   obj["materialTypeId"]   = it["materialTypeId"]
        if it.get("effectiveLocationId"): obj["effectiveLocationId"] = it["effectiveLocationId"]
        obj["discoverySuppress"] = bool(it["discoverySuppress"])
        result.append(obj)
    return result


def main():
    if not DB_PATH.exists():
        print(f"ERROR: {DB_PATH} not found. Run import.py first.")
        raise SystemExit(1)

    con = sqlite3.connect(DB_PATH)
    cur = con.cursor()

    instances = export_instances(cur)
    holdings  = export_holdings(cur)
    items     = export_items(cur)

    con.close()

    (DATA_DIR / "instances.json").write_text(json.dumps(instances, indent=2, ensure_ascii=False) + "\n")
    (DATA_DIR / "holdings.json").write_text(json.dumps(holdings,  indent=2, ensure_ascii=False) + "\n")
    (DATA_DIR / "items.json").write_text(json.dumps(items,        indent=2, ensure_ascii=False) + "\n")

    print(f"Exported {len(instances)} instances, {len(holdings)} holdings, {len(items)} items")

    # Integrity check
    orphan_items    = [it["id"] for it in items if not any(h["id"] == it["holdingsRecordId"] for h in holdings)]
    orphan_holdings = [h["id"]  for h in holdings if not any(i["id"] == h["instanceId"] for i in instances)]
    if orphan_items:
        print(f"WARNING: {len(orphan_items)} items have no matching holding: {orphan_items[:3]}")
    if orphan_holdings:
        print(f"WARNING: {len(orphan_holdings)} holdings have no matching instance: {orphan_holdings[:3]}")
    if not orphan_items and not orphan_holdings:
        print("Integrity OK")


if __name__ == "__main__":
    main()
