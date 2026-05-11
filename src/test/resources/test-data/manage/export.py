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
    instances = rows_as_dicts(cur, "SELECT * FROM instances ORDER BY id")
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

        subjects = []
        for s in rows_as_dicts(cur, "SELECT * FROM instance_subjects WHERE instanceId=? ORDER BY id", (iid,)):
            o = {k: v for k, v in {
                "value": s["value"],
                "authorityId": s.get("authorityId"),
                "sourceId": s.get("sourceId"),
                "typeId": s.get("typeId"),
            }.items() if v is not None}
            subjects.append(o)

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

        statistical_code_ids = [
            r["statisticalCodeId"]
            for r in rows_as_dicts(cur, "SELECT * FROM instance_statistical_code_ids WHERE instanceId=? ORDER BY id", (iid,))
        ]

        nature_of_content_term_ids = [
            r["termId"]
            for r in rows_as_dicts(cur, "SELECT * FROM instance_nature_of_content_term_ids WHERE instanceId=? ORDER BY id", (iid,))
        ]

        tag_list = [
            r["tagValue"]
            for r in rows_as_dicts(cur, "SELECT * FROM instance_tags WHERE instanceId=? ORDER BY id", (iid,))
        ]

        administrative_notes = [
            r["note"]
            for r in rows_as_dicts(cur, "SELECT * FROM instance_administrative_notes WHERE instanceId=? ORDER BY id", (iid,))
        ]

        notes = [
            {k: v for k, v in {"note": n["note"], "staffOnly": bool(n["staffOnly"])}.items() if v is not None}
            for n in rows_as_dicts(cur, "SELECT * FROM instance_notes WHERE instanceId=? ORDER BY id", (iid,))
        ]

        electronic_access = []
        for ea in rows_as_dicts(cur, "SELECT * FROM instance_electronic_access WHERE instanceId=? ORDER BY id", (iid,)):
            o = {k: v for k, v in {
                "uri": ea.get("uri"),
                "linkText": ea.get("linkText"),
                "materialsSpecification": ea.get("materialsSpecification"),
                "publicNote": ea.get("publicNote"),
                "relationshipId": ea.get("relationshipId"),
            }.items() if v is not None}
            electronic_access.append(o)

        meta_rows = rows_as_dicts(cur, "SELECT * FROM instance_metadata WHERE instanceId=?", (iid,))
        metadata = None
        if meta_rows:
            m = meta_rows[0]
            metadata = {k: v for k, v in {
                "createdDate": m.get("createdDate"),
                "createdByUserId": m.get("createdByUserId"),
                "updatedDate": m.get("updatedDate"),
                "updatedByUserId": m.get("updatedByUserId"),
            }.items() if v is not None}
            if not metadata:
                metadata = None

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
        if inst.get("hrid"):             obj["hrid"]             = inst["hrid"]
        if inst.get("modeOfIssuanceId"): obj["modeOfIssuanceId"] = inst["modeOfIssuanceId"]
        if inst.get("isBoundWith"):      obj["isBoundWith"]      = bool(inst["isBoundWith"])
        if inst.get("shared"):           obj["shared"]           = bool(inst["shared"])
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
        if format_ids:              obj["instanceFormatIds"]        = format_ids
        if statistical_code_ids:    obj["statisticalCodeIds"]       = statistical_code_ids
        if nature_of_content_term_ids: obj["natureOfContentTermIds"] = nature_of_content_term_ids
        if languages:               obj["languages"]               = languages
        if tag_list:                obj["tags"]                    = {"tagList": tag_list}
        if administrative_notes:    obj["administrativeNotes"]     = administrative_notes
        if notes:                   obj["notes"]                   = notes
        if electronic_access:       obj["electronicAccess"]        = electronic_access
        obj["discoverySuppress"] = bool(inst["discoverySuppress"])
        obj["staffSuppress"]     = bool(inst["staffSuppress"])
        if inst.get("statusId"): obj["statusId"]   = inst["statusId"]
        if metadata:             obj["metadata"]   = metadata
        if inst.get("_comment"): obj["_comment"]   = inst["_comment"]

        result.append(obj)

    return result


def export_holdings(cur):
    holdings = rows_as_dicts(cur, "SELECT * FROM holdings ORDER BY id")
    result = []
    for h in holdings:
        hid = h["id"]

        statistical_code_ids = [
            r["statisticalCodeId"]
            for r in rows_as_dicts(cur, "SELECT * FROM holdings_statistical_code_ids WHERE holdingsId=? ORDER BY id", (hid,))
        ]

        former_ids = [
            r["formerId"]
            for r in rows_as_dicts(cur, "SELECT * FROM holdings_former_ids WHERE holdingsId=? ORDER BY id", (hid,))
        ]

        tag_list = [
            r["tagValue"]
            for r in rows_as_dicts(cur, "SELECT * FROM holdings_tags WHERE holdingsId=? ORDER BY id", (hid,))
        ]

        administrative_notes = [
            r["note"]
            for r in rows_as_dicts(cur, "SELECT * FROM holdings_administrative_notes WHERE holdingsId=? ORDER BY id", (hid,))
        ]

        notes = [
            {k: v for k, v in {"note": n["note"], "staffOnly": bool(n["staffOnly"])}.items() if v is not None}
            for n in rows_as_dicts(cur, "SELECT * FROM holdings_notes WHERE holdingsId=? ORDER BY id", (hid,))
        ]

        electronic_access = []
        for ea in rows_as_dicts(cur, "SELECT * FROM holdings_electronic_access WHERE holdingsId=? ORDER BY id", (hid,)):
            o = {k: v for k, v in {
                "uri": ea.get("uri"),
                "linkText": ea.get("linkText"),
                "materialsSpecification": ea.get("materialsSpecification"),
                "publicNote": ea.get("publicNote"),
                "relationshipId": ea.get("relationshipId"),
            }.items() if v is not None}
            electronic_access.append(o)

        meta_rows = rows_as_dicts(cur, "SELECT * FROM holdings_metadata WHERE holdingsId=?", (hid,))
        metadata = None
        if meta_rows:
            m = meta_rows[0]
            metadata = {k: v for k, v in {
                "createdDate": m.get("createdDate"),
                "createdByUserId": m.get("createdByUserId"),
                "updatedDate": m.get("updatedDate"),
                "updatedByUserId": m.get("updatedByUserId"),
            }.items() if v is not None}
            if not metadata:
                metadata = None

        obj = {"id": hid}
        obj["discoverySuppress"]    = bool(h["discoverySuppress"])
        if h.get("holdingsTypeId"):      obj["holdingsTypeId"]      = h["holdingsTypeId"]
        obj["instanceId"]               = h["instanceId"]
        if h.get("permanentLocationId"): obj["permanentLocationId"] = h["permanentLocationId"]
        if h.get("callNumberTypeId"):    obj["callNumberTypeId"]    = h["callNumberTypeId"]
        if h.get("callNumber"):          obj["callNumber"]          = h["callNumber"]
        if h.get("callNumberPrefix"):    obj["callNumberPrefix"]    = h["callNumberPrefix"]
        if h.get("callNumberSuffix"):    obj["callNumberSuffix"]    = h["callNumberSuffix"]
        if h.get("hrid"):                obj["hrid"]                = h["hrid"]
        if h.get("sourceId"):            obj["sourceId"]            = h["sourceId"]
        if h.get("copyNumber"):          obj["copyNumber"]          = h["copyNumber"]
        if h.get("shelvingTitle"):       obj["shelvingTitle"]       = h["shelvingTitle"]
        if h.get("temporaryLocationId"): obj["temporaryLocationId"] = h["temporaryLocationId"]
        if h.get("illPolicy"):           obj["illPolicy"]           = h["illPolicy"]
        if former_ids:              obj["formerIds"]           = former_ids
        if statistical_code_ids:    obj["statisticalCodeIds"]  = statistical_code_ids
        if tag_list:                obj["tags"]                = {"tagList": tag_list}
        if administrative_notes:    obj["administrativeNotes"] = administrative_notes
        if notes:                   obj["notes"]               = notes
        if electronic_access:       obj["electronicAccess"]    = electronic_access
        if metadata:                obj["metadata"]            = metadata
        result.append(obj)
    return result


def export_items(cur):
    items = rows_as_dicts(cur, "SELECT * FROM items ORDER BY id")
    result = []
    for it in items:
        iid = it["id"]

        enc = {}
        if it.get("effectiveCallNumberSuffix"): enc["suffix"]     = it["effectiveCallNumberSuffix"]
        if it.get("effectiveCallNumber"):       enc["callNumber"] = it["effectiveCallNumber"]
        if it.get("effectiveCallNumberPrefix"): enc["prefix"]     = it["effectiveCallNumberPrefix"]
        if it.get("effectiveCallNumberTypeId"): enc["typeId"]     = it["effectiveCallNumberTypeId"]

        status = {}
        if it.get("status_name"): status["name"] = it["status_name"]
        if it.get("status_date"): status["date"] = it["status_date"]

        former_ids = [
            r["formerId"]
            for r in rows_as_dicts(cur, "SELECT * FROM items_former_ids WHERE itemId=? ORDER BY id", (iid,))
        ]

        statistical_code_ids = [
            r["statisticalCodeId"]
            for r in rows_as_dicts(cur, "SELECT * FROM items_statistical_code_ids WHERE itemId=? ORDER BY id", (iid,))
        ]

        tag_list = [
            r["tagValue"]
            for r in rows_as_dicts(cur, "SELECT * FROM items_tags WHERE itemId=? ORDER BY id", (iid,))
        ]

        administrative_notes = [
            r["note"]
            for r in rows_as_dicts(cur, "SELECT * FROM items_administrative_notes WHERE itemId=? ORDER BY id", (iid,))
        ]

        notes = [
            {k: v for k, v in {"note": n["note"], "staffOnly": bool(n["staffOnly"])}.items() if v is not None}
            for n in rows_as_dicts(cur, "SELECT * FROM items_notes WHERE itemId=? ORDER BY id", (iid,))
        ]

        circulation_notes = [
            {k: v for k, v in {"note": cn["note"], "staffOnly": bool(cn["staffOnly"])}.items() if v is not None}
            for cn in rows_as_dicts(cur, "SELECT * FROM items_circulation_notes WHERE itemId=? ORDER BY id", (iid,))
        ]

        electronic_access = []
        for ea in rows_as_dicts(cur, "SELECT * FROM items_electronic_access WHERE itemId=? ORDER BY id", (iid,)):
            o = {k: v for k, v in {
                "uri": ea.get("uri"),
                "linkText": ea.get("linkText"),
                "materialsSpecification": ea.get("materialsSpecification"),
                "publicNote": ea.get("publicNote"),
                "relationshipId": ea.get("relationshipId"),
            }.items() if v is not None}
            electronic_access.append(o)

        meta_rows = rows_as_dicts(cur, "SELECT * FROM items_metadata WHERE itemId=?", (iid,))
        metadata = None
        if meta_rows:
            m = meta_rows[0]
            metadata = {k: v for k, v in {
                "createdDate": m.get("createdDate"),
                "createdByUserId": m.get("createdByUserId"),
                "updatedDate": m.get("updatedDate"),
                "updatedByUserId": m.get("updatedByUserId"),
            }.items() if v is not None}
            if not metadata:
                metadata = None

        obj = {"id": iid}
        obj["holdingsRecordId"] = it["holdingsRecordId"]
        obj["instanceId"]       = it["instanceId"]
        if it.get("hrid"):            obj["hrid"]            = it["hrid"]
        if it.get("barcode"):         obj["barcode"]         = it["barcode"]
        if it.get("accessionNumber"): obj["accessionNumber"] = it["accessionNumber"]
        if it.get("itemIdentifier"):  obj["itemIdentifier"]  = it["itemIdentifier"]
        if it.get("itemLevelCallNumber"):       obj["itemLevelCallNumber"]       = it["itemLevelCallNumber"]
        if it.get("itemLevelCallNumberTypeId"): obj["itemLevelCallNumberTypeId"] = it["itemLevelCallNumberTypeId"]
        if enc:    obj["effectiveCallNumberComponents"] = enc
        if status: obj["status"]              = status
        if it.get("materialTypeId"):   obj["materialTypeId"]   = it["materialTypeId"]
        if it.get("effectiveLocationId"): obj["effectiveLocationId"] = it["effectiveLocationId"]
        if former_ids:           obj["formerIds"]           = former_ids
        if statistical_code_ids: obj["statisticalCodeIds"]  = statistical_code_ids
        if tag_list:             obj["tags"]                = {"tagList": tag_list}
        if administrative_notes: obj["administrativeNotes"] = administrative_notes
        if notes:                obj["notes"]               = notes
        if circulation_notes:    obj["circulationNotes"]    = circulation_notes
        if electronic_access:    obj["electronicAccess"]    = electronic_access
        obj["discoverySuppress"] = bool(it["discoverySuppress"])
        if it.get("yearCaption"):        obj["yearCaption"]         = it["yearCaption"]
        if metadata:             obj["metadata"]            = metadata
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
