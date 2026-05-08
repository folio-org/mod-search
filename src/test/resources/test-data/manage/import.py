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

MANAGE_DIR    = Path(__file__).parent
DATA_DIR      = MANAGE_DIR.parent
DB_PATH       = MANAGE_DIR / "test-data.db"
SCHEMA        = MANAGE_DIR / "schema.sql"
# Hardcoded reference data (id → name) for all lookup tables.
# Sourced from FOLIO reference-data + realistic invented names for test-only UUIDs.
_REF_NAMES: dict[str, dict[str, str]] = {
    "ref_material_types": {
        "1a54b431-2e4f-452d-9cae-9cee66c9a892": "book",
        "3d413322-1dee-431b-bd73-b1e399063260": "map",
        "5ee11d91-f7e8-481d-b079-65d708582ccc": "dvd",
        "615b8413-82d5-4203-aa6e-e37984cb5ac3": "electronic resource",
        "c898029e-9a02-4b61-bedb-6956cff21bc2": "audio recording",
        "d9acad2f-2aac-4b48-9097-e6ab85906b25": "text",
        "dd0bf600-dbd9-44ab-9ff2-e2a61a6539f1": "sound recording",
        "fd6c6515-d470-4561-9c32-3e3290d4ca98": "microform",
        "71fbd940-1027-40a6-8a48-49b44d795e46": "unspecified",
        "30b3e36a-d3b2-415e-98c2-47fbdf878862": "video recording",
    },
    "ref_instance_types": {
        "24da24dd-03ae-4e34-bad6-c79e342baeb9": "computer file",
        "2022aa2e-bdde-4dc4-90bc-115e8894b8b3": "cartographic three-dimensional form",
        "225faa14-f9bf-4ecd-990d-69433c912434": "two-dimensional moving image",
        "3363cdb1-e644-446c-82a4-dc3a1d4395b9": "cartographic dataset",
        "3be24c14-3551-4180-9292-26a786649c8b": "performed music",
        "3e3039b7-fda0-4ac4-885a-022d457cb99c": "three-dimensional moving image",
        "408f82f0-e612-4977-96a1-02076229e312": "cartographic tactile image",
        "497b5090-3da2-486c-b57f-de5bb3c2e26d": "notated music",
        "526aa04d-9289-4511-8866-349299592c18": "cartographic image",
        "535e3160-763a-42f9-b0c0-d8ed7df6e2a2": "still image",
        "6312d172-f0cf-40f6-b27d-9fa8feaf332f": "text",
        "80c0c134-0240-4b63-99d0-6ca755d5f433": "cartographic moving image",
        "82689e16-629d-47f7-94b5-d89736cf11f2": "tactile three-dimensional form",
        "8105bd44-e7bd-487e-a8f2-b804a361d92f": "tactile text",
        "9bce18bd-45bf-4949-8fa8-63163e4b7d7f": "sounds",
        "a2c91e87-6bab-44d6-8adb-1fd02481fc4f": "other",
        "a67e00fd-dcce-42a9-9e75-fd654ec31e89": "tactile notated music",
        "c1e95c2b-4efc-48cf-9e71-edb622cf0c22": "three-dimensional form",
        "c0000001-0000-4000-8000-000000000000": "mixed material",
        "c0000002-0000-4000-8000-000000000000": "projected medium",
        "c208544b-9e28-44fa-a13c-f4093d72f798": "computer program",
        "c7f7446f-4642-4d97-88c9-55bae2ad6c7f": "spoken word",
        "de9e38bb-89a8-43ee-922f-c973b122cbb3": "object",
        "df5dddff-9c30-4507-8b82-119ff972d4d7": "computer dataset",
        "e2a278fb-565a-4296-a7c5-8eb63d259522": "tactile notated movement",
        "e5136fa2-1f19-4581-b005-6e007a940ca8": "cartographic tactile three-dimensional form",
        "e6a278fb-565a-4296-a7c5-8eb63d259522": "tactile notated movement",
        "ebe264b5-69aa-4b7c-a230-3b53337f6440": "notated movement",
        "efe2e89b-0525-4535-aa9b-3ff1a131189e": "tactile image",
        "fbe264b5-69aa-4b7c-a230-3b53337f6440": "notated movement",
        "30fffe0e-e985-4144-b2e2-1e8179bdb41f": "unspecified",
    },
    "ref_holdings_types": {
        "0c422f92-0f4d-4d32-8cbe-390ebc33a3e5": "Physical",
        "03c9c400-b9e3-4a07-ac0e-05ab470233ed": "Monograph",
        "996f93e2-5b5e-4cf2-9168-33ced1f95eed": "Electronic",
        "d02bb1e2-fa7f-4354-a9f4-1ca9b81510a2": "Periodical",
        "dc35d0ae-e877-488b-8e97-6e41444e6d0a": "Multi-part monograph",
        "e6da6c98-6dd0-41bc-8b4b-cfd4bbd9c3ae": "Serial",
        "eb003b9d-86f2-4bdf-9f8e-28851122617d": "Bound with",
    },
    "ref_call_number_types": {
        "03dd64d0-5626-4ecd-8ece-4531e0069f35": "Dewey Decimal classification",
        "054d460d-d6b9-4469-9e37-7a78a2266655": "National Library of Medicine classification",
        "0b5d15ad-7f08-45f1-8504-7bab31a2b4e5": "NLM",
        "28927d76-e097-4f63-8510-e56f2b7a3ad0": "Shelving control number",
        "512173a7-bd09-490e-b773-17d83f2b63fe": "LC Modified",
        "530b84ea-c8b3-4a90-a2cd-2e4a7bb5f18e": "Local free-text",
        "5ba6b62e-6858-490a-8102-5b1369873835": "Title",
        "6b368b19-01af-4a44-a0d3-09ec5d1e3e19": "SUDOC",
        "6b368b19-0d18-4689-8d15-cf904e15b3f0": "Custom",
        "6caca63e-5651-4db6-9247-3205156e9699": "Other scheme",
        "827a2b64-cbf5-4296-8545-130876e4dfc0": "Source specified in subfield $2",
        "828ae637-dfa3-4265-a1af-5279c436edff": "MOYS",
        "95467209-6d7b-468b-94df-0f5d7ad2747d": "Library of Congress classification",
        "cbc422b0-1d17-4d43-9cc0-6c89b2efd014": "Genre/Form",
        "cd70562c-dd0b-42f6-aa80-ce803d24d4a1": "Shelved separately",
        "cf74a451-41ad-49aa-aa2b-21d2d2f2e235": "Accession number",
        "cf74a451-5b9e-4a58-8c5c-2eff9fb4db67": "Sequential barcode",
        "d644be8f-deb5-4c4d-8c9e-2291b7c0f46f": "UDC",
        "fc388041-6cd0-4806-8a74-ebe3b9ab4c6e": "Superintendent of Documents classification",
    },
    "ref_contributor_name_types": {
        "0ad0a89a-741d-4f1a-85a6-ada214751013": "Family name",
        "1f857623-89ca-4f0b-ab56-5c30f706df3e": "Jurisdiction name",
        "2b94c631-fca9-4892-a730-03ee529ffe2a": "Personal name",
        "2e48e713-17f3-4c13-a9f8-23845bb210aa": "Corporate name",
        "9fb7f83e-260e-479f-9539-dfd9a628b858": "Name in script",
        "e2ef4075-310a-4447-a231-712bf10cc985": "Unknown",
        "e8b311a6-3b21-43f2-a269-dd9310cb2d0a": "Meeting name",
    },
    "ref_contributor_types": {
        "2a165833-1673-493f-934b-f3d3c8fcb299": "Editor",
        "3ae36e29-e38f-457c-8fcf-1974a6cb63d3": "Illustrator",
        "653ffe66-aa3f-4f1c-a090-c42c4011ef40": "Translator",
        "28de45ae-f0ca-46fe-9f89-283313b3255b": "Abridger",
        "6e09d47d-95e2-4d8a-831b-f777b8ef6d81": "Author",
        "7aac64ab-7f2a-4019-9705-e07133e3ad1a": "Creator",
        "9deb29d1-3e71-4951-9413-a80adac703d0": "Editor",
        "3322b734-ce38-4cd4-815d-8983352837cc": "Translator",
        "3add6049-0b63-4fec-9892-e3867e7358e2": "Illustrator",
        "901d01e5-66b1-48f0-99f9-b5e92e3d2d15": "Composer",
        "a60314d4-c3c6-4e29-92fa-86cc6ace4d56": "Publisher",
        "246858e3-4022-4991-9f1c-50901ccc1438": "Performer",
        "1aae8ca3-4ddd-4549-a769-116b75f3c773": "Photographer",
        "f9395f3d-cd46-413e-9504-8756c54f38a2": "Proofreader",
    },
    "ref_identifier_types": {
        "3fb87c8e-d0d2-4c3a-821a-b481f32f48a9": "Control number identifier",
        "7e591197-f335-4afb-bc6d-a6d76ca3bace": "System control number",
        "82fb97e1-f460-4099-9ac8-97518341ed1a": "Linking number",
        "8261054f-be78-422d-bd51-4ed9f33c3422": "ISBN",
        "913300b2-03ed-469a-8179-c1092c991227": "ISSN",
        "c3c651c7-96b4-416c-a1af-17146ce0a409": "Fingerprint identifier",
        "c858e4f2-2b6b-4385-842b-60732ee14abb": "LCCN",
        "1795ea23-6856-48a5-a772-f356e16a8a6c": "UPC",
        "439bfbae-75bc-4f74-9fc7-b2a2d47ce3ef": "OCLC",
        "39554f54-d0bb-4f0a-89a4-e422f6136316": "DOI",
        "eb7b2717-f149-4fec-81a3-deefb8f5ee6b": "URN",
        "5d164f4b-0b15-4e42-ae75-cfcf85318ad9": "Control number",
        "5130aed5-1095-4fb6-8f6f-caa3d6cc7aae": "Local identifier",
        "7f907515-a1bf-4513-8a38-92e1a07c539d": "ASIN",
        "b5d8cdc4-9441-487c-90cf-0c7ec97728eb": "Publisher or distributor number",
        "3187432f-9434-40a8-8782-35a111a1491e": "BNB",
    },
    "ref_alternative_title_types": {
        "09964ad1-7aed-49b8-8223-a4c105e3ef87": "Running title",
        "0fe58901-183e-4678-a3aa-0b4751174ba8": "No type specified",
        "2584943f-36ad-4037-a7fa-3bdebb09f452": "Other title",
        "2ca8538d-a2fd-4e60-b967-1cb220101e22": "Added title page title",
        "30512027-cdc9-4c79-af75-1565b3bd888d": "Key title",
        "35bbe7f2-1a49-11ed-861d-0242ac120002": "Variant title",
        "432ca81a-fe4d-4249-bfd3-53388725647d": "Caption title",
        "4bb300a4-04c9-414b-bfbc-9c032f74b7b2": "Parallel title",
        "5c364ce4-c8fd-4891-a28d-bb91c9bcdbfb": "Cover title",
        "781c04a4-f41e-4ab0-9118-6836e93de3c8": "Distinctive title",
        "a8b45056-2223-43ca-8514-4dd88ece984b": "Portion of title",
        "ab26d2e4-1a4a-11ed-861d-0242ac120002": "Former title",
        "dae08d04-8c4e-4ab2-b6bb-99edbf252231": "Spine title",
        "9d968396-0cce-4e9f-8867-c4d04c01f535": "Uniform Title",
    },
    "ref_locations": {
        "0d106980-1789-42ac-b355-a6c7a74ddea3": "Annex Stacks",
        "184aae84-a5bf-4c6a-85ba-4a7c73026cd5": "Online",
        "4fdca025-1629-4688-aeb7-9c5fe5c73549": "Reference Room",
        "53cf956f-c1df-410b-8bea-27f712cca7c0": "Annex",
        "65b6c2e9-8a7b-4a10-9b5d-ba1cf0313cd7": "Special Collections",
        "758258bc-ecc1-41b8-abca-f7b610822ffd": "ORWIG ETHNO CD",
        "765b4c3b-485c-4ce4-a117-f99c01ac49fe": "Periodicals",
        "81f1ab2c-83c5-4a90-a8b7-c8c8179c0697": "Reserve Desk",
        "b241764c-1466-4e1d-a028-1a3684a5da87": "Popular Reading Collection",
        "b777f3a4-4372-4792-a87d-8e8f177eab10": "Rare Books Room",
        "cdd60388-0c75-4969-b3c5-2d04621ed26f": "Map Library",
        "ce23dfa1-17e8-4a1f-ad6b-34ce6ab352c2": "Media Center",
        "f1a49577-5096-4771-a8a0-d07d642241eb": "Oversize Stacks",
        "f34d27c6-a8eb-461b-acd6-5dea81771e70": "SECOND FLOOR",
        "fcd64ce1-6995-48f0-840e-89ffa2288371": "Main Library",
    },
}


def load_ref_names(con):
    """Seed reference lookup tables with hardcoded FOLIO names."""
    total = 0
    for table, entries in _REF_NAMES.items():
        for id_val, name in entries.items():
            con.execute(
                f"INSERT OR IGNORE INTO {table} (id, name) VALUES (?, ?)",
                (id_val, name),
            )
            con.execute(
                f"UPDATE {table} SET name=? WHERE id=? AND (name IS NULL OR name = id)",
                (name, id_val),
            )
            total += 1
    print(f"  Seeded {total} ref names")


def upsert_ref(cur, table, id_val):
    """Insert a UUID into a lookup table; abort if it has no known name."""
    if not id_val:
        return
    if id_val not in _REF_NAMES.get(table, {}):
        raise ValueError(
            f"Unknown ref ID {id_val!r} for table '{table}'. "
            f"Add it to _REF_NAMES['{table}'] in import.py."
        )
    cur.execute(
        f"INSERT OR IGNORE INTO {table} (id, name) VALUES (?, ?)",
        (id_val, _REF_NAMES[table][id_val]),
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
                dateTypeId, date1, date2, _comment)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
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
                r.get("_comment"),
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
        upsert_ref(cur, "ref_locations",         r.get("temporaryLocationId"))
        upsert_ref(cur, "ref_call_number_types",  r.get("callNumberTypeId"))
        upsert_ref(cur, "ref_holdings_types",     r.get("holdingsTypeId"))
        cur.execute(
            """INSERT OR REPLACE INTO holdings
               (id, instanceId, callNumber, callNumberPrefix, callNumberSuffix,
                callNumberTypeId, permanentLocationId, holdingsTypeId,
                hrid, sourceId,
                copyNumber, shelvingTitle, discoverySuppress,
                temporaryLocationId, illPolicy)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
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
                r.get("temporaryLocationId"),
                r.get("illPolicy"),
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
                materialTypeId, status_name, status_date, discoverySuppress,
                yearCaption)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
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
                r.get("yearCaption"),
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

    # Populate ref table names from mod-inventory-storage reference-data
    load_ref_names(con)
    con.commit()

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
