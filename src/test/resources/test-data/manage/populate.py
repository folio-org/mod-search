#!/usr/bin/env python3
"""
populate.py — Fill missing fields in instances/holdings/items JSON with realistic data.

Deterministic: uses each record's UUID as a seed so output is stable across runs.
Run from any directory.

Usage:
    python manage/populate.py
"""

import hashlib
import json
import re
import random
from pathlib import Path

MANAGE_DIR = Path(__file__).parent
DATA_DIR   = MANAGE_DIR.parent

# ---------------------------------------------------------------------------
# Ref UUIDs (subset of _REF_NAMES from import.py)
# ---------------------------------------------------------------------------
INSTANCE_TYPE_TEXT     = "6312d172-f0cf-40f6-b27d-9fa8feaf332f"
INSTANCE_TYPE_POOL     = [
    ("6312d172-f0cf-40f6-b27d-9fa8feaf332f", 60),   # text
    ("535e3160-763a-42f9-b0c0-d8ed7df6e2a2", 10),   # still image
    ("225faa14-f9bf-4ecd-990d-69433c912434",  8),   # two-dimensional moving image
    ("497b5090-3da2-486c-b57f-de5bb3c2e26d",  6),   # notated music
    ("3be24c14-3551-4180-9292-26a786649c8b",  5),   # performed music
    ("df5dddff-9c30-4507-8b82-119ff972d4d7",  4),   # computer dataset
    ("a2c91e87-6bab-44d6-8adb-1fd02481fc4f",  4),   # other
    ("9bce18bd-45bf-4949-8fa8-63163e4b7d7f",  3),   # sounds
]

FORMAT_ID_POOL = [
    "7f9c4ac0-fa3d-43b7-b978-3bf0be38c4da",
    "89f6d4f0-9cd2-4015-828d-331dc3adb47a",
    "e57e36a3-80ff-46a6-ac2f-5c8bd79bc2bb",
    "25a81102-a2a9-4576-85ff-133ebcbcef2c",
]

STATUS_ID_POOL = [
    "9634a5ab-9228-4703-baf2-4d12ebc77d56",  # Available
    "1117f093-0bfd-4324-aa3f-96c77f43b2bf",  # Not Available
    "54cb0be0-2b5b-4da5-a687-32dec54b016a",  # Temporary Location
]

CONTRIBUTOR_NAME_TYPE_PERSONAL  = "2b94c631-fca9-4892-a730-03ee529ffe2a"
CONTRIBUTOR_NAME_TYPE_CORPORATE = "2e48e713-17f3-4c13-a9f8-23845bb210aa"
CONTRIBUTOR_TYPE_AUTHOR         = "6e09d47d-95e2-4d8a-831b-f777b8ef6d81"
CONTRIBUTOR_TYPE_EDITOR         = "9deb29d1-3e71-4951-9413-a80adac703d0"

MATERIAL_TYPE_POOL = [
    ("1a54b431-2e4f-452d-9cae-9cee66c9a892", 55),   # book
    ("d9acad2f-2aac-4b48-9097-e6ab85906b25", 15),   # text
    ("615b8413-82d5-4203-aa6e-e37984cb5ac3", 10),   # electronic resource
    ("5ee11d91-f7e8-481d-b079-65d708582ccc",  5),   # dvd
    ("fd6c6515-d470-4561-9c32-3e3290d4ca98",  5),   # microform
    ("dd0bf600-dbd9-44ab-9ff2-e2a61a6539f1",  5),   # sound recording
    ("30b3e36a-d3b2-415e-98c2-47fbdf878862",  5),   # video recording
]

HOLDINGS_TYPE_POOL = [
    ("0c422f92-0f4d-4d32-8cbe-390ebc33a3e5", 50),   # Physical
    ("03c9c400-b9e3-4a07-ac0e-05ab470233ed", 20),   # Monograph
    ("996f93e2-5b5e-4cf2-9168-33ced1f95eed", 10),   # Electronic
    ("e6da6c98-6dd0-41bc-8b4b-cfd4bbd9c3ae", 10),   # Serial
    ("dc35d0ae-e877-488b-8e97-6e41444e6d0a",  5),   # Multi-part monograph
    ("d02bb1e2-fa7f-4354-a9f4-1ca9b81510a2",  5),   # Periodical
]

CALL_NUMBER_TYPE_LC    = "95467209-6d7b-468b-94df-0f5d7ad2747d"
CALL_NUMBER_TYPE_DEWEY = "03dd64d0-5626-4ecd-8ece-4531e0069f35"
CALL_NUMBER_TYPE_POOL  = [
    ("95467209-6d7b-468b-94df-0f5d7ad2747d", 60),   # LC
    ("03dd64d0-5626-4ecd-8ece-4531e0069f35", 25),   # Dewey
    ("054d460d-d6b9-4469-9e37-7a78a2266655", 10),   # NLM
    ("6caca63e-5651-4db6-9247-3205156e9699",  5),   # Other
]

LOCATION_POOL = [
    ("fcd64ce1-6995-48f0-840e-89ffa2288371", 30),   # Main Library
    ("53cf956f-c1df-410b-8bea-27f712cca7c0", 20),   # Annex
    ("184aae84-a5bf-4c6a-85ba-4a7c73026cd5", 10),   # Online
    ("f34d27c6-a8eb-461b-acd6-5dea81771e70", 10),   # SECOND FLOOR
    ("b241764c-1466-4e1d-a028-1a3684a5da87",  8),   # Popular Reading Collection
    ("758258bc-ecc1-41b8-abca-f7b610822ffd",  6),   # ORWIG ETHNO CD
    ("65b6c2e9-8a7b-4a10-9b5d-ba1cf0313cd7",  5),   # Special Collections
    ("0d106980-1789-42ac-b355-a6c7a74ddea3",  4),   # Annex Stacks
    ("4fdca025-1629-4688-aeb7-9c5fe5c73549",  4),   # Reference Room
    ("81f1ab2c-83c5-4a90-a8b7-c8c8179c0697",  3),   # Reserve Desk
]

# ---------------------------------------------------------------------------
# Realistic content pools
# ---------------------------------------------------------------------------
AUTHORS = [
    "Smith, John A.", "Johnson, Mary L.", "Williams, Robert T.", "Brown, Patricia K.",
    "Jones, Michael D.", "Garcia, Linda R.", "Miller, David S.", "Davis, Barbara E.",
    "Wilson, James F.", "Anderson, Susan G.", "Taylor, Thomas H.", "Thomas, Dorothy C.",
    "Hernandez, Carlos M.", "Moore, Nancy W.", "Martin, Sandra J.", "Jackson, Mark B.",
    "Thompson, Betty N.", "White, Daniel P.", "Lopez, Margaret Y.", "Lee, Christopher Z.",
    "Harris, Elizabeth A.", "Clark, Kenneth L.", "Lewis, Sharon M.", "Robinson, Paul D.",
    "Walker, Donna E.", "Young, Steven F.", "Allen, Carol G.", "King, Jason H.",
    "Wright, Lisa I.", "Scott, Matthew J.", "Torres, Angela K.", "Nguyen, Kevin L.",
    "Hill, Michelle M.", "Flores, Brian N.", "Green, Deborah O.", "Adams, Timothy P.",
    "Nelson, Virginia Q.", "Baker, Raymond R.", "Hall, Katherine S.", "Rivera, Anthony T.",
    "Campbell, Melissa U.", "Mitchell, Gregory V.", "Carter, Julie W.", "Roberts, Alan X.",
    "Gomez, Rachel Y.", "Phillips, Frank Z.", "Evans, Christine A.", "Turner, Lawrence B.",
    "Diaz, Amanda C.", "Parker, George D.",
]

PUBLISHERS = [
    ("MIT Press", "Cambridge, Mass."),
    ("Oxford University Press", "Oxford"),
    ("Cambridge University Press", "Cambridge"),
    ("Springer", "New York"),
    ("Wiley", "Hoboken, N.J."),
    ("Elsevier", "Amsterdam"),
    ("Routledge", "London"),
    ("Palgrave Macmillan", "Basingstoke"),
    ("Harvard University Press", "Cambridge, Mass."),
    ("Princeton University Press", "Princeton"),
    ("University of Chicago Press", "Chicago"),
    ("Stanford University Press", "Stanford, Calif."),
    ("Yale University Press", "New Haven"),
    ("Columbia University Press", "New York"),
    ("Sage Publications", "Thousand Oaks, Calif."),
    ("Johns Hopkins University Press", "Baltimore"),
    ("Duke University Press", "Durham"),
    ("University of Michigan Press", "Ann Arbor"),
    ("Penn State University Press", "University Park, Pa."),
    ("Penguin Books", "London"),
]

SUBJECTS_POOL = [
    "Information technology", "Computer science", "Machine learning", "Data analysis",
    "Environmental policy", "Urban planning", "Economic development", "Social sciences",
    "History of science", "Philosophy of mind", "Political theory", "Public administration",
    "Molecular biology", "Genetics", "Neuroscience", "Clinical psychology",
    "Architectural design", "Urban sociology", "Cultural anthropology", "Education reform",
    "International relations", "Global economics", "Climate change", "Renewable energy",
    "Literature and society", "Media studies", "Communication theory", "Digital humanities",
    "Library science", "Information management", "Knowledge organization", "Metadata",
    "Artificial intelligence", "Natural language processing", "Computer vision",
    "Quantum computing", "Cryptography", "Network security", "Database systems",
    "Statistics", "Applied mathematics", "Operations research", "Game theory",
]

SERIES_POOL = [
    "Studies in information science",
    "Advances in computer science",
    "Monographs in library and information science",
    "Interdisciplinary studies in knowledge management",
    "Research in applied linguistics",
    "Topics in modern biology",
    "Contemporary issues in education",
    "International studies in political economy",
    "Perspectives on urban and regional development",
    "Cambridge studies in publishing and printing history",
    "MIT Press series in cognitive science",
    "Contributions to economics",
    "Lecture notes in computer science",
    "Texts in applied mathematics",
    "Springer series in solid-state sciences",
]

# Statistical code UUIDs — stored as-is, no ref-table validation
STATISTICAL_CODE_POOL = [
    "6899291f-0d18-4f8d-a269-42706a5d0e27",  # ARL
    "b5968c9e-cddc-4576-99e3-8e60aed8b0dd",  # IPEDS-eBk
    "9d8abbe2-1a94-4866-8731-4d12ac09f7a8",  # IPEDS-Per
    "e10796e0-a594-47b7-b748-3a81b69b3d9b",  # IPEDS-AV
    "b6b46869-f3c1-4370-b603-29774a1e42b1",  # IPEDS-Map
    "f47b773a-bd5f-4246-ac1e-fa4adcd0dcdf",  # IPEDS-Other
    "c4073462-6144-4b69-a543-dd131e241799",  # govt-doc
    "d9acad2f-d9ac-4b48-9097-e6ab85000001",  # music-score
]

# Nature-of-content term UUIDs (FOLIO standard)
NATURE_OF_CONTENT_POOL = [
    "aef4d369-1571-419c-8cf8-7e596f6f535c",  # bibliography
    "44cd89f3-2e76-469f-a955-cc57cb9e0395",  # catalog
    "9f0a2cf0-7a9b-45a2-a403-f68d2850d07c",  # textbook
    "2fec23f6-6bbc-4fa2-a736-b64a8c06a47a",  # biography
    "526aa04d-9289-4511-8866-349299592c18",  # conference publication
    "0abeaebb-4166-483d-9f62-3bdf6aae7a93",  # research report
    "9ef56f0c-fa7b-44dc-ab7c-15a8e7a0ab2b",  # primary source
    "a1d70ca5-67aa-4824-b3af-55c8cf47a427",  # legal article
]

TAG_POOL = [
    "new-addition", "recommended", "withdrawn-review", "high-demand",
    "preservation-needed", "digitization-candidate", "ebook-available",
    "review-needed", "reserve", "special-order",
]

INSTANCE_NOTE_POOL = [
    "Includes bibliographical references.",
    "Includes bibliographical references and index.",
    "Also available online.",
    "Originally presented as the author's thesis.",
    "Translation of the 3rd German edition.",
    "Revised edition.",
    "Title from cover.",
    "Cataloged from PDF version of text.",
    "Description based on print version record.",
    "Companion volume to work published by same publisher.",
    "Text in English; abstract also in French.",
    "Previously published under title: Introduction to the subject.",
]

HOLDINGS_NOTE_POOL = [
    "Copy acquired through donation.",
    "Purchased from special collections fund.",
    "Transferred from branch library.",
    "Formerly in reference collection.",
    "Available for 3-hour in-house use only.",
    "Gift of the library association.",
    "Received as part of standing order.",
    "Bound with: companion volume.",
]

HOLDINGS_ADMIN_NOTE_POOL = [
    "Last inventory check: 2024-01.",
    "Condition reviewed — acceptable.",
    "Acquired via consortium purchase.",
    "Duplicate copy retained per policy.",
]

ILL_POLICY_POOL = [
    "Will lend",
    "Will lend hard copy only",
    "Will not lend",
    "Copy only",
    "Unknown lending policy",
    "Limited lending policy",
]

CIRCULATION_NOTE_POOL = [
    "Handle with care — fragile spine.",
    "Patron requested special packaging.",
    "Returned with minor damage to cover.",
    "Renew at circulation desk only.",
    "Three-day loan; no renewal allowed.",
    "Library use only during renovation period.",
    "Must be used in supervised reading room.",
    "Patron notified of outstanding fine.",
    "Check for loose inserts before shelving.",
    "Item last returned with loose pages.",
]

YEAR_CAPTION_POOL = [
    "2023",
    "2022",
    "2021",
    "2020",
    "v.1 (2010)",
    "v.2 (2012)",
    "v.1-3 (2015-2018)",
    "v.4-6 (2019-2022)",
    "no.1-12 (2020)",
    "Annual: 2019",
    "2018-2020",
    "2016",
]

ITEM_NOTE_POOL = [
    "Binding repaired.",
    "Cover worn.",
    "Pages slightly yellowed.",
    "Bookplate on inside front cover.",
    "Previous owner's name on title page.",
    "Stamps on pages.",
    "Slight water damage on top edge.",
    "Spine faded.",
    "Missing index pages.",
    "Disk attached to inside back cover.",
]

ITEM_ADMIN_NOTE_POOL = [
    "Weeding review due 2026.",
    "Condition reviewed and acceptable.",
    "Duplicate copy retained per policy.",
    "Acquired via interlibrary transfer.",
    "Item requires repair before circulation.",
]

LC_CLASS_PREFIXES = [
    "QA", "Q", "Z", "HM", "HV", "HD", "P", "PN", "PS", "PR", "LB",
    "JZ", "GE", "QH", "QP", "RC", "RJ", "S", "TA", "TK", "TR",
]

ITEM_STATUSES = [
    ("Available", 60),
    ("Checked out", 12),
    ("In transit", 5),
    ("Awaiting pickup", 4),
    ("In process", 4),
    ("Missing", 4),
    ("On order", 4),
    ("Restricted", 3),
    ("Unavailable", 2),
    ("Withdrawn", 2),
]

PLACEHOLDER_TITLE_POOL = [
    "The Art of Scientific Inquiry",
    "Foundations of Modern Linguistics",
    "Urban Ecology and Sustainable Cities",
    "A History of Economic Thought",
    "The Psychology of Human Decision-Making",
    "Comparative Constitutional Law",
    "Introduction to Cognitive Science",
    "Environmental Policy and Governance",
    "The Sociology of Work and Organizations",
    "Cultural Anthropology: A Global Perspective",
    "Advanced Topics in Molecular Biology",
    "Information Systems in Healthcare",
    "Political Economy of Development",
    "The Ethics of Artificial Intelligence",
    "Media Studies and Public Discourse",
    "Principles of Macroeconomics",
    "Theories of International Relations",
    "Philosophy of Mind and Consciousness",
    "Statistical Methods in Social Research",
    "Architectural History of the Modern Era",
    "Behavioral Finance and Investment",
    "The Science of Climate Change",
    "Legal Theory and Jurisprudence",
    "Computational Methods in Bioinformatics",
    "Public Health and Epidemiology",
    "The Dynamics of Social Movements",
    "Applied Ethics in Engineering",
    "Language Acquisition and Development",
    "Geopolitics in the Twenty-First Century",
    "Organizational Behavior and Leadership",
    "Advances in Renewable Energy Systems",
    "Philosophy of Science: An Introduction",
    "The History of Art and Visual Culture",
    "Criminology and Criminal Justice",
    "Quantum Mechanics: Concepts and Applications",
    "Narrative Theory and Literary Criticism",
    "Global Supply Chain Management",
    "The Neuroscience of Learning and Memory",
    "Democratic Theory and Practice",
    "Ecology of Freshwater Systems",
    "Human Rights in International Law",
    "Data Structures and Algorithm Design",
    "Postcolonial Studies: Theory and Practice",
    "Gender and Society: Feminist Perspectives",
    "Monetary Theory and Central Banking",
    "Classics of Western Philosophy",
    "Structural Analysis in Civil Engineering",
    "Discourse Analysis and Communication",
    "Migration, Identity, and Belonging",
    "The Digital Transformation of Libraries",
    "Evolutionary Biology: Mechanisms and Patterns",
    "Taxation and Fiscal Policy",
    "Sociology of Education and Schooling",
    "Ethics in Medical Research",
    "Game Theory and Strategic Behavior",
    "The History of Science and Technology",
    "Semantics and Pragmatics in Linguistics",
    "Development Economics: Theory and Policy",
    "Bioethics: Principles and Cases",
    "Regional Planning and Land Use",
    "Cognitive Behavioral Approaches in Therapy",
    "The Rise of Global Financial Markets",
    "Foundations of Quantum Computing",
    "Heritage Conservation and Museum Studies",
]


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def rng(record_id: str, salt: str = "") -> random.Random:
    """Return a deterministic Random instance seeded from the record UUID."""
    digest = hashlib.sha256((record_id + salt).encode()).digest()
    seed = int.from_bytes(digest[:8], "little")
    return random.Random(seed)


def weighted_choice(rnd: random.Random, pool: list) -> str:
    items, weights = zip(*pool)
    return rnd.choices(items, weights=weights, k=1)[0]


def strip_leading_article(title: str) -> str:
    return re.sub(r"^(A |An |The )", "", title, flags=re.IGNORECASE).strip()


def make_isbn(rnd: random.Random) -> str:
    digits = [rnd.randint(0, 9) for _ in range(12)]
    check = (10 - sum((3 if i % 2 else 1) * d for i, d in enumerate(digits))) % 10
    return "978-" + "".join(str(d) for d in digits[:3]) + "-" + \
           "".join(str(d) for d in digits[3:9]) + "-" + \
           "".join(str(d) for d in digits[9:]) + "-" + str(check)


def make_lc_call_number(rnd: random.Random) -> str:
    prefix = rnd.choice(LC_CLASS_PREFIXES)
    num    = rnd.randint(1, 9999)
    alpha  = rnd.choice(list("ABCDEFGHJKLMNPQRSTUVWXYZ"))
    year   = rnd.randint(1960, 2024)
    return f"{prefix}{num} .{alpha}{rnd.randint(10,99)} {year}"


def make_dewey_call_number(rnd: random.Random) -> str:
    main = rnd.randint(0, 999)
    dec  = rnd.randint(0, 99)
    return f"{main:03d}.{dec:02d}"


def make_call_number(rnd: random.Random, cn_type_id: str) -> str:
    if cn_type_id == CALL_NUMBER_TYPE_DEWEY:
        return make_dewey_call_number(rnd)
    return make_lc_call_number(rnd)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def make_barcode(rnd: random.Random) -> str:
    """Generate a realistic 14-digit library barcode (prefix 3900)."""
    suffix = rnd.randint(0, 10 ** 10 - 1)
    return f"3900{suffix:010d}"


def make_lccn(rnd: random.Random) -> str:
    """Generate a realistic Library of Congress Control Number."""
    year = rnd.randint(1960, 2023)
    num = rnd.randint(100000, 999999)
    return f"{year}-{num}"


def make_oclc(rnd: random.Random) -> str:
    """Generate a realistic OCLC control number."""
    return f"(OCoLC){rnd.randint(1000000, 99999999)}"


def make_hrid(prefix: str, idx: int) -> str:
    """FOLIO-style HRID, e.g. in00000000001."""
    return f"{prefix}{idx + 1:011d}"


# ---------------------------------------------------------------------------
# Populate instances
# ---------------------------------------------------------------------------
_PLACEHOLDER_RE = re.compile(r"^(Instance \d+|Resource\d+)$")


def _assign_placeholder_titles(records: list) -> dict:
    """Return {record_id: title} for all placeholder records, with no duplicates.

    Uses a deterministic shuffle seeded by the sorted list of placeholder IDs
    so the mapping is stable across re-runs with the same set of records.
    """
    placeholder_ids = [
        r["id"] for r in records
        if _PLACEHOLDER_RE.match(r.get("title", ""))
        or _PLACEHOLDER_RE.match(r.get("indexTitle", ""))
    ]
    if not placeholder_ids:
        return {}

    seed_bytes = hashlib.sha256("|".join(sorted(placeholder_ids)).encode()).digest()
    pool_rng = random.Random(int.from_bytes(seed_bytes[:8], "little"))

    pool = list(PLACEHOLDER_TITLE_POOL)
    i = 2
    while len(pool) < len(placeholder_ids):
        pool += [f"{t} (Vol. {i})" for t in PLACEHOLDER_TITLE_POOL]
        i += 1

    pool_rng.shuffle(pool)
    return dict(zip(placeholder_ids, pool))


def _find_duplicate_title_replacements(records: list) -> dict:
    """Return {record_id: new_unique_title} for every non-first duplicate occurrence.

    Keeps the first record in each duplicate group, replaces the rest with
    unique titles drawn from pool entries not already in use.
    """
    from collections import defaultdict

    title_to_ids: dict = defaultdict(list)
    for r in records:
        title_to_ids[r.get("title", "")].append(r["id"])

    needs_new = []
    for ids in title_to_ids.values():
        if len(ids) > 1:
            needs_new.extend(ids[1:])  # keep first, replace rest

    if not needs_new:
        return {}

    used_titles = {r.get("title", "") for r in records}
    available = [t for t in PLACEHOLDER_TITLE_POOL if t not in used_titles]
    i = 2
    while len(available) < len(needs_new):
        available += [f"{t}, Volume {i}" for t in PLACEHOLDER_TITLE_POOL
                      if f"{t}, Volume {i}" not in used_titles]
        i += 1

    seed_bytes = hashlib.sha256("|".join(sorted(needs_new)).encode()).digest()
    pool_rng = random.Random(int.from_bytes(seed_bytes[:8], "little"))
    pool_rng.shuffle(available)

    return dict(zip(needs_new, available))


_FAKE_INSTANCE_TYPE_IDS = {
    "c0000001-0000-4000-8000-000000000000",
    "c0000002-0000-4000-8000-000000000000",
}

_DISCOVERY_SUPPRESS_INSTANCE_RATE = 0.12
_DISCOVERY_SUPPRESS_HOLDINGS_RATE = 0.10

def populate_instances(records: list) -> list:
    placeholder_titles = _assign_placeholder_titles(records)
    dedup_titles       = _find_duplicate_title_replacements(records)

    # Fix 1: pre-compute contiguous HRIDs sorted by record ID
    sorted_ids = sorted(r["id"] for r in records)
    hrid_by_id = {rid: make_hrid("in", idx) for idx, rid in enumerate(sorted_ids)}

    out = []
    for idx, r in enumerate(records):
        r = dict(r)
        rid  = r["id"]
        rnd  = rng(rid)
        rnd2 = rng(rid, "b")

        # instanceTypeId — replace fake UUIDs with valid ones
        if not r.get("instanceTypeId") or r["instanceTypeId"] in _FAKE_INSTANCE_TYPE_IDS:
            r["instanceTypeId"] = weighted_choice(rnd, INSTANCE_TYPE_POOL)

        # Replace placeholder "Instance N" / "ResourceN" titles with unique realistic ones
        if rid in placeholder_titles:
            r["title"] = placeholder_titles[rid]
            r.pop("indexTitle", None)  # force recompute below

        # Replace duplicate titles (non-first occurrences)
        if rid in dedup_titles:
            r["title"] = dedup_titles[rid]
            r.pop("indexTitle", None)

        # Fix stale placeholder indexTitle left from a prior populate run
        if _PLACEHOLDER_RE.match(r.get("indexTitle", "")):
            r.pop("indexTitle", None)

        # indexTitle — strip leading article from title
        if not r.get("indexTitle"):
            r["indexTitle"] = strip_leading_article(r.get("title", ""))

        # languages
        if "languages" not in r:
            r["languages"] = ["eng"]

        # statusId
        if "statusId" not in r:
            r["statusId"] = rnd.choice(STATUS_ID_POOL)

        # instanceFormatIds
        if "instanceFormatIds" not in r:
            r["instanceFormatIds"] = [rnd.choice(FORMAT_ID_POOL)]

        # dates
        if "dates" not in r:
            year = rnd.randint(1950, 2023)
            r["dates"] = {
                "dateTypeId": "0750f52b-3bfc-458d-9307-e9afc8bcdffa",
                "date1": str(year),
                "date2": str(year + rnd.randint(0, 10)) if rnd.random() < 0.3 else None,
            }
            if r["dates"]["date2"] is None:
                del r["dates"]["date2"]

        # contributors — fill missing contributorNameTypeId / contributorTypeId
        existing_contribs = r.get("contributors", [])
        fixed_contribs = []
        for c in existing_contribs:
            c = dict(c)
            if "contributorNameTypeId" not in c:
                c["contributorNameTypeId"] = CONTRIBUTOR_NAME_TYPE_PERSONAL
            if "contributorTypeId" not in c:
                c["contributorTypeId"] = CONTRIBUTOR_TYPE_AUTHOR
            fixed_contribs.append(c)

        # add a primary contributor if none present
        if not fixed_contribs:
            author = rnd.choice(AUTHORS)
            is_editor = rnd.random() < 0.15
            fixed_contribs = [{
                "name": author,
                "contributorNameTypeId": CONTRIBUTOR_NAME_TYPE_PERSONAL,
                "contributorTypeId": CONTRIBUTOR_TYPE_EDITOR if is_editor else CONTRIBUTOR_TYPE_AUTHOR,
                "primary": True,
            }]
            if rnd.random() < 0.4:
                second = rnd.choice([a for a in AUTHORS if a != author])
                fixed_contribs.append({
                    "name": second,
                    "contributorNameTypeId": CONTRIBUTOR_NAME_TYPE_PERSONAL,
                    "contributorTypeId": CONTRIBUTOR_TYPE_AUTHOR,
                })

        r["contributors"] = fixed_contribs

        # publication
        if "publication" not in r:
            pub, place = rnd.choice(PUBLISHERS)
            year = r.get("dates", {}).get("date1", str(rnd.randint(1960, 2023)))
            r["publication"] = [{
                "publisher": pub,
                "place": place,
                "dateOfPublication": str(year)[:4],
                "role": "Publisher",
            }]

        # identifiers — add ISBN if none; add secondary LCCN/OCLC for ~40% of records
        if "identifiers" not in r:
            isbn = make_isbn(rnd2)
            r["identifiers"] = [{
                "value": isbn,
                "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422",  # ISBN
            }]
        # Augment with a secondary identifier for diversity
        if rnd2.random() < 0.40:
            existing_types = {i.get("identifierTypeId") for i in r["identifiers"]}
            candidate = rnd2.choice([
                ("c858e4f2-2b6b-4385-842b-60732ee14abb", make_lccn(rng(rid, "lccn"))),   # LCCN
                ("439bfbae-75bc-4f74-9fc7-b2a2d47ce3ef", make_oclc(rng(rid, "oclc"))),   # OCLC
                ("913300b2-03ed-469a-8179-c1092c991227",                                  # ISSN
                 f"{rng(rid,'issn').randint(1000,9999)}-{rng(rid,'issn2').randint(1000,9999)}"),
            ])
            type_id, value = candidate
            if type_id not in existing_types:
                r["identifiers"] = list(r["identifiers"]) + [{"value": value, "identifierTypeId": type_id}]

        # subjects
        if "subjects" not in r:
            n = rnd.randint(1, 3)
            chosen = rnd.sample(SUBJECTS_POOL, n)
            r["subjects"] = [{"value": s} for s in chosen]

        # classifications
        if "classifications" not in r:
            cn_type = rnd.choice([CALL_NUMBER_TYPE_LC, CALL_NUMBER_TYPE_DEWEY])
            cn      = make_call_number(rnd2, cn_type)
            r["classifications"] = [{
                "classificationNumber": cn,
                "classificationTypeId": "42471af9-7d25-4f3a-bf78-60d29dcf463b",
            }]

        # series
        if "series" not in r and rnd.random() < 0.35:
            r["series"] = [{"value": rnd.choice(SERIES_POOL)}]

        # hrid — always force contiguous assignment
        r["hrid"] = hrid_by_id[rid]

        # statisticalCodeIds
        if "statisticalCodeIds" not in r and rnd.random() < 0.20:
            n = rnd.choice([1, 1, 2])
            r["statisticalCodeIds"] = rnd.sample(STATISTICAL_CODE_POOL, n)

        # natureOfContentTermIds
        if "natureOfContentTermIds" not in r and rnd.random() < 0.45:
            n = rnd.choice([1, 1, 1, 2])
            r["natureOfContentTermIds"] = rnd.sample(NATURE_OF_CONTENT_POOL, n)

        # tags
        if "tags" not in r and rnd.random() < 0.15:
            n = rnd.choice([1, 1, 2])
            r["tags"] = {"tagList": rnd.sample(TAG_POOL, n)}

        # administrativeNotes
        if "administrativeNotes" not in r and rnd.random() < 0.08:
            r["administrativeNotes"] = [rnd.choice(INSTANCE_NOTE_POOL[:4])]

        # notes
        if "notes" not in r and rnd.random() < 0.25:
            n = rnd.choice([1, 1, 2])
            chosen = rnd.sample(INSTANCE_NOTE_POOL, n)
            r["notes"] = [{"note": t, "staffOnly": rnd.random() < 0.3} for t in chosen]

        # discoverySuppress — enforce ~12% suppressed using deterministic seed
        r["discoverySuppress"] = rng(rid, "suppress").random() < _DISCOVERY_SUPPRESS_INSTANCE_RATE

        out.append(r)
    return out


# ---------------------------------------------------------------------------
# Populate holdings
# ---------------------------------------------------------------------------
def populate_holdings(records: list) -> list:
    # Fix 1: pre-compute contiguous HRIDs sorted by record ID
    sorted_ids = sorted(r["id"] for r in records)
    hrid_by_id = {rid: make_hrid("ho", idx) for idx, rid in enumerate(sorted_ids)}

    out = []
    for idx, r in enumerate(records):
        r = dict(r)
        rid = r["id"]
        rnd = rng(rid)

        if not r.get("holdingsTypeId"):
            r["holdingsTypeId"] = weighted_choice(rnd, HOLDINGS_TYPE_POOL)

        if not r.get("permanentLocationId"):
            r["permanentLocationId"] = weighted_choice(rnd, LOCATION_POOL)

        if not r.get("callNumberTypeId"):
            r["callNumberTypeId"] = weighted_choice(rnd, CALL_NUMBER_TYPE_POOL)

        if not r.get("callNumber"):
            r["callNumber"] = make_call_number(rng(rid, "cn"), r["callNumberTypeId"])

        if "copyNumber" not in r and rnd.random() < 0.4:
            r["copyNumber"] = str(rnd.randint(1, 5))

        # hrid — always force contiguous assignment
        r["hrid"] = hrid_by_id[rid]

        # temporaryLocationId — ~20% of holdings
        if "temporaryLocationId" not in r and rnd.random() < 0.20:
            r["temporaryLocationId"] = weighted_choice(rng(rid, "tmploc"), LOCATION_POOL)

        # illPolicy — ~30% of holdings
        if "illPolicy" not in r and rnd.random() < 0.30:
            r["illPolicy"] = rnd.choice(ILL_POLICY_POOL)

        # callNumberPrefix / callNumberSuffix on more holdings
        if "callNumberPrefix" not in r and rnd.random() < 0.25:
            r["callNumberPrefix"] = rnd.choice(["v.", "pt.", "suppl.", "copy", "no."])
        if "callNumberSuffix" not in r and rnd.random() < 0.20:
            r["callNumberSuffix"] = str(rnd.randint(1, 5))

        # statisticalCodeIds
        if "statisticalCodeIds" not in r and rnd.random() < 0.15:
            r["statisticalCodeIds"] = [rnd.choice(STATISTICAL_CODE_POOL)]

        # tags
        if "tags" not in r and rnd.random() < 0.10:
            r["tags"] = {"tagList": [rnd.choice(TAG_POOL)]}

        # administrativeNotes
        if "administrativeNotes" not in r and rnd.random() < 0.06:
            r["administrativeNotes"] = [rnd.choice(HOLDINGS_ADMIN_NOTE_POOL)]

        # notes
        if "notes" not in r and rnd.random() < 0.20:
            note = rnd.choice(HOLDINGS_NOTE_POOL)
            r["notes"] = [{"note": note, "staffOnly": rnd.random() < 0.25}]

        # discoverySuppress — enforce ~10% suppressed using deterministic seed
        r["discoverySuppress"] = rng(rid, "suppress").random() < _DISCOVERY_SUPPRESS_HOLDINGS_RATE

        out.append(r)
    return out


# ---------------------------------------------------------------------------
# Populate items — index holdings by id for location lookup
# ---------------------------------------------------------------------------
def populate_items(records: list, holdings_by_id: dict) -> list:
    # Fix 1: pre-compute contiguous HRIDs sorted by record ID
    sorted_ids = sorted(r["id"] for r in records)
    hrid_by_id = {rid: make_hrid("it", idx) for idx, rid in enumerate(sorted_ids)}

    out = []
    for idx, r in enumerate(records):
        r = dict(r)
        rid = r["id"]
        rnd = rng(rid)

        if not r.get("materialTypeId"):
            r["materialTypeId"] = weighted_choice(rnd, MATERIAL_TYPE_POOL)

        if not r.get("effectiveLocationId"):
            h = holdings_by_id.get(r.get("holdingsRecordId", ""))
            if h and h.get("permanentLocationId"):
                r["effectiveLocationId"] = h["permanentLocationId"]
            else:
                r["effectiveLocationId"] = weighted_choice(rnd, LOCATION_POOL)

        if "status" not in r:
            status_name = weighted_choice(rnd, ITEM_STATUSES)
            r["status"] = {"name": status_name}

        # item-level call number on ~20 % of items (the rest inherit from holdings)
        if "itemLevelCallNumber" not in r and rnd.random() < 0.2:
            cn_type = weighted_choice(rnd, CALL_NUMBER_TYPE_POOL)
            r["itemLevelCallNumberTypeId"] = cn_type
            r["itemLevelCallNumber"] = make_call_number(rng(rid, "icn"), cn_type)

        # hrid — always force contiguous assignment
        r["hrid"] = hrid_by_id[rid]

        # barcode — unique, deterministic
        if not r.get("barcode"):
            r["barcode"] = make_barcode(rng(rid, "bc"))

        # statisticalCodeIds
        if "statisticalCodeIds" not in r and rnd.random() < 0.12:
            r["statisticalCodeIds"] = [rnd.choice(STATISTICAL_CODE_POOL)]

        # tags
        if "tags" not in r and rnd.random() < 0.10:
            r["tags"] = {"tagList": [rnd.choice(TAG_POOL)]}

        # administrativeNotes
        if "administrativeNotes" not in r and rnd.random() < 0.06:
            r["administrativeNotes"] = [rnd.choice(ITEM_ADMIN_NOTE_POOL)]

        # notes
        if "notes" not in r and rnd.random() < 0.20:
            note = rnd.choice(ITEM_NOTE_POOL)
            r["notes"] = [{"note": note, "staffOnly": rnd.random() < 0.2}]

        # circulationNotes — ~20% of items
        if "circulationNotes" not in r and rnd.random() < 0.20:
            note = rng(rid, "circ").choice(CIRCULATION_NOTE_POOL)
            r["circulationNotes"] = [{"note": note, "staffOnly": rng(rid, "circ2").random() < 0.3}]

        # yearCaption — ~25% of items
        if "yearCaption" not in r and rnd.random() < 0.25:
            r["yearCaption"] = rng(rid, "yrcap").choice(YEAR_CAPTION_POOL)

        # effectiveCallNumberComponents — derive for items missing it
        if "effectiveCallNumberComponents" not in r:
            h = holdings_by_id.get(r.get("holdingsRecordId", ""))
            if h:
                enc = {}
                if h.get("callNumber"):       enc["callNumber"] = h["callNumber"]
                if h.get("callNumberPrefix"): enc["prefix"]     = h["callNumberPrefix"]
                if h.get("callNumberSuffix"): enc["suffix"]     = h["callNumberSuffix"]
                if h.get("callNumberTypeId"): enc["typeId"]     = h["callNumberTypeId"]
                if enc:
                    r["effectiveCallNumberComponents"] = enc

        out.append(r)
    return out


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    inst_path  = DATA_DIR / "instances.json"
    hold_path  = DATA_DIR / "holdings.json"
    items_path = DATA_DIR / "items.json"

    instances = json.loads(inst_path.read_text())
    holdings  = json.loads(hold_path.read_text())
    items     = json.loads(items_path.read_text())

    holdings_by_id = {h["id"]: h for h in holdings}

    instances = populate_instances(instances)
    holdings  = populate_holdings(holdings)
    items     = populate_items(items, holdings_by_id)

    inst_path.write_text(json.dumps(instances, indent=2, ensure_ascii=False))
    hold_path.write_text(json.dumps(holdings, indent=2, ensure_ascii=False))
    items_path.write_text(json.dumps(items, indent=2, ensure_ascii=False))

    print(f"Populated {len(instances)} instances, {len(holdings)} holdings, {len(items)} items")


if __name__ == "__main__":
    main()
