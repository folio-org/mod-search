-- Lookup tables (UUID → human name, pre-populated on import, not exported)
CREATE TABLE IF NOT EXISTS ref_locations           (id TEXT PRIMARY KEY, name TEXT);
CREATE TABLE IF NOT EXISTS ref_material_types      (id TEXT PRIMARY KEY, name TEXT);
CREATE TABLE IF NOT EXISTS ref_call_number_types   (id TEXT PRIMARY KEY, name TEXT);
CREATE TABLE IF NOT EXISTS ref_instance_types      (id TEXT PRIMARY KEY, name TEXT);
CREATE TABLE IF NOT EXISTS ref_holdings_types      (id TEXT PRIMARY KEY, name TEXT);
CREATE TABLE IF NOT EXISTS ref_contributor_name_types  (id TEXT PRIMARY KEY, name TEXT);
CREATE TABLE IF NOT EXISTS ref_contributor_types       (id TEXT PRIMARY KEY, name TEXT);
CREATE TABLE IF NOT EXISTS ref_identifier_types    (id TEXT PRIMARY KEY, name TEXT);
CREATE TABLE IF NOT EXISTS ref_alternative_title_types (id TEXT PRIMARY KEY, name TEXT);
-- Authority-specific ref tables
CREATE TABLE IF NOT EXISTS ref_authority_identifier_types (id TEXT PRIMARY KEY, name TEXT);
CREATE TABLE IF NOT EXISTS ref_authority_note_types       (id TEXT PRIMARY KEY, name TEXT);
CREATE TABLE IF NOT EXISTS ref_authority_source_files     (id TEXT PRIMARY KEY, name TEXT);

-- Core tables
CREATE TABLE IF NOT EXISTS instances (
  id                TEXT PRIMARY KEY,
  title             TEXT,
  indexTitle        TEXT,
  source            TEXT,
  instanceTypeId    TEXT REFERENCES ref_instance_types(id),
  statusId          TEXT,
  discoverySuppress INTEGER DEFAULT 0,
  staffSuppress     INTEGER DEFAULT 0,
  hrid              TEXT,
  modeOfIssuanceId  TEXT,
  isBoundWith       INTEGER DEFAULT 0,
  shared            INTEGER DEFAULT 0,
  -- dates object (single, not array)
  dateTypeId        TEXT,
  date1             TEXT,
  date2             TEXT,
  _comment          TEXT
);

CREATE TABLE IF NOT EXISTS holdings (
  id                  TEXT PRIMARY KEY,
  instanceId          TEXT REFERENCES instances(id),
  callNumber          TEXT,
  callNumberPrefix    TEXT,
  callNumberSuffix    TEXT,
  callNumberTypeId    TEXT REFERENCES ref_call_number_types(id),
  permanentLocationId TEXT REFERENCES ref_locations(id),
  holdingsTypeId      TEXT REFERENCES ref_holdings_types(id),
  hrid                TEXT,
  sourceId            TEXT,
  copyNumber          TEXT,
  shelvingTitle       TEXT,
  discoverySuppress   INTEGER DEFAULT 0,
  temporaryLocationId TEXT REFERENCES ref_locations(id),
  illPolicy           TEXT
);

CREATE TABLE IF NOT EXISTS items (
  id                          TEXT PRIMARY KEY,
  holdingsRecordId            TEXT REFERENCES holdings(id),
  instanceId                  TEXT REFERENCES instances(id),
  hrid                        TEXT,
  barcode                     TEXT,
  accessionNumber             TEXT,
  itemIdentifier              TEXT,
  itemLevelCallNumber         TEXT,
  itemLevelCallNumberTypeId   TEXT REFERENCES ref_call_number_types(id),
  effectiveLocationId         TEXT REFERENCES ref_locations(id),
  -- effectiveCallNumberComponents (single object)
  effectiveCallNumber         TEXT,
  effectiveCallNumberPrefix   TEXT,
  effectiveCallNumberSuffix   TEXT,
  effectiveCallNumberTypeId   TEXT REFERENCES ref_call_number_types(id),
  materialTypeId              TEXT REFERENCES ref_material_types(id),
  -- status (single object)
  status_name                 TEXT,
  status_date                 TEXT,
  discoverySuppress           INTEGER DEFAULT 0,
  yearCaption                 TEXT
);

-- Junction tables for instance arrays
CREATE TABLE IF NOT EXISTS instance_contributors (
  id                   INTEGER PRIMARY KEY AUTOINCREMENT,
  instanceId           TEXT REFERENCES instances(id),
  name                 TEXT,
  contributorNameTypeId TEXT REFERENCES ref_contributor_name_types(id),
  contributorTypeId    TEXT REFERENCES ref_contributor_types(id),
  contributorTypeText  TEXT,
  authorityId          TEXT,
  isPrimary            INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS instance_subjects (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  instanceId  TEXT REFERENCES instances(id),
  value       TEXT,
  authorityId TEXT,
  sourceId    TEXT,
  typeId      TEXT
);

CREATE TABLE IF NOT EXISTS instance_classifications (
  id                   INTEGER PRIMARY KEY AUTOINCREMENT,
  instanceId           TEXT REFERENCES instances(id),
  classificationNumber TEXT,
  classificationTypeId TEXT
);

CREATE TABLE IF NOT EXISTS instance_languages (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  instanceId   TEXT REFERENCES instances(id),
  languageCode TEXT
);

CREATE TABLE IF NOT EXISTS instance_identifiers (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  instanceId       TEXT REFERENCES instances(id),
  value            TEXT,
  identifierTypeId TEXT REFERENCES ref_identifier_types(id)
);

CREATE TABLE IF NOT EXISTS instance_series (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  instanceId  TEXT REFERENCES instances(id),
  value       TEXT,
  authorityId TEXT
);

CREATE TABLE IF NOT EXISTS instance_alternative_titles (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  instanceId             TEXT REFERENCES instances(id),
  alternativeTitle       TEXT,
  alternativeTitleTypeId TEXT REFERENCES ref_alternative_title_types(id),
  authorityId            TEXT
);

CREATE TABLE IF NOT EXISTS instance_publications (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  instanceId        TEXT REFERENCES instances(id),
  publisher         TEXT,
  place             TEXT,
  dateOfPublication TEXT,
  role              TEXT
);

CREATE TABLE IF NOT EXISTS instance_editions (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  instanceId TEXT REFERENCES instances(id),
  value      TEXT
);

CREATE TABLE IF NOT EXISTS instance_format_ids (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  instanceId TEXT REFERENCES instances(id),
  formatId   TEXT
);

CREATE TABLE IF NOT EXISTS instance_statistical_code_ids (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  instanceId        TEXT REFERENCES instances(id),
  statisticalCodeId TEXT
);

CREATE TABLE IF NOT EXISTS instance_nature_of_content_term_ids (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  instanceId TEXT REFERENCES instances(id),
  termId     TEXT
);

CREATE TABLE IF NOT EXISTS instance_tags (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  instanceId TEXT REFERENCES instances(id),
  tagValue   TEXT
);

CREATE TABLE IF NOT EXISTS instance_administrative_notes (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  instanceId TEXT REFERENCES instances(id),
  note       TEXT
);

CREATE TABLE IF NOT EXISTS instance_notes (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  instanceId TEXT REFERENCES instances(id),
  note       TEXT,
  staffOnly  INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS instance_electronic_access (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  instanceId             TEXT REFERENCES instances(id),
  uri                    TEXT,
  linkText               TEXT,
  materialsSpecification TEXT,
  publicNote             TEXT,
  relationshipId         TEXT
);

CREATE TABLE IF NOT EXISTS instance_metadata (
  instanceId      TEXT PRIMARY KEY REFERENCES instances(id),
  createdDate     TEXT,
  createdByUserId TEXT,
  updatedDate     TEXT,
  updatedByUserId TEXT
);

-- Junction tables for holdings arrays
CREATE TABLE IF NOT EXISTS holdings_statistical_code_ids (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  holdingsId        TEXT REFERENCES holdings(id),
  statisticalCodeId TEXT
);

CREATE TABLE IF NOT EXISTS holdings_former_ids (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  holdingsId TEXT REFERENCES holdings(id),
  formerId   TEXT
);

CREATE TABLE IF NOT EXISTS holdings_tags (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  holdingsId TEXT REFERENCES holdings(id),
  tagValue   TEXT
);

CREATE TABLE IF NOT EXISTS holdings_administrative_notes (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  holdingsId TEXT REFERENCES holdings(id),
  note       TEXT
);

CREATE TABLE IF NOT EXISTS holdings_notes (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  holdingsId TEXT REFERENCES holdings(id),
  note       TEXT,
  staffOnly  INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS holdings_electronic_access (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  holdingsId             TEXT REFERENCES holdings(id),
  uri                    TEXT,
  linkText               TEXT,
  materialsSpecification TEXT,
  publicNote             TEXT,
  relationshipId         TEXT
);

CREATE TABLE IF NOT EXISTS holdings_metadata (
  holdingsId      TEXT PRIMARY KEY REFERENCES holdings(id),
  createdDate     TEXT,
  createdByUserId TEXT,
  updatedDate     TEXT,
  updatedByUserId TEXT
);

-- Junction tables for items arrays
CREATE TABLE IF NOT EXISTS items_former_ids (
  id       INTEGER PRIMARY KEY AUTOINCREMENT,
  itemId   TEXT REFERENCES items(id),
  formerId TEXT
);

CREATE TABLE IF NOT EXISTS items_statistical_code_ids (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  itemId            TEXT REFERENCES items(id),
  statisticalCodeId TEXT
);

CREATE TABLE IF NOT EXISTS items_tags (
  id       INTEGER PRIMARY KEY AUTOINCREMENT,
  itemId   TEXT REFERENCES items(id),
  tagValue TEXT
);

CREATE TABLE IF NOT EXISTS items_administrative_notes (
  id     INTEGER PRIMARY KEY AUTOINCREMENT,
  itemId TEXT REFERENCES items(id),
  note   TEXT
);

CREATE TABLE IF NOT EXISTS items_notes (
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  itemId    TEXT REFERENCES items(id),
  note      TEXT,
  staffOnly INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS items_circulation_notes (
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  itemId    TEXT REFERENCES items(id),
  note      TEXT,
  staffOnly INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS items_electronic_access (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  itemId                 TEXT REFERENCES items(id),
  uri                    TEXT,
  linkText               TEXT,
  materialsSpecification TEXT,
  publicNote             TEXT,
  relationshipId         TEXT
);

CREATE TABLE IF NOT EXISTS items_metadata (
  itemId          TEXT PRIMARY KEY REFERENCES items(id),
  createdDate     TEXT,
  createdByUserId TEXT,
  updatedDate     TEXT,
  updatedByUserId TEXT
);

-- Authority tables
CREATE TABLE IF NOT EXISTS authorities (
  id              TEXT PRIMARY KEY,
  heading         TEXT,   -- primary heading value
  headingType     TEXT,   -- e.g. 'personalName', 'corporateName', 'uniformTitle', ...
  subjectHeadings TEXT,
  naturalId       TEXT,
  source          TEXT,
  sourceFileId    TEXT REFERENCES ref_authority_source_files(id),
  -- metadata fields (inlined — authority metadata has no separate reference data)
  createdDate     TEXT,
  createdByUserId TEXT,
  updatedDate     TEXT,
  updatedByUserId TEXT
);

-- "See From Tracing" headings (one row per value per authority)
CREATE TABLE IF NOT EXISTS authority_sft_headings (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  authorityId TEXT REFERENCES authorities(id),
  headingType TEXT,   -- same type vocabulary as authorities.headingType
  value       TEXT
);

-- "See Also From Tracing" headings (one row per value per authority)
CREATE TABLE IF NOT EXISTS authority_saft_headings (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  authorityId TEXT REFERENCES authorities(id),
  headingType TEXT,
  value       TEXT
);

CREATE TABLE IF NOT EXISTS authority_identifiers (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  authorityId      TEXT REFERENCES authorities(id),
  identifierTypeId TEXT REFERENCES ref_authority_identifier_types(id),
  value            TEXT
);

CREATE TABLE IF NOT EXISTS authority_notes (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  authorityId TEXT REFERENCES authorities(id),
  noteTypeId  TEXT REFERENCES ref_authority_note_types(id),
  value       TEXT
);

-- Convenience views
DROP VIEW IF EXISTS v_instance_full;
CREATE VIEW v_instance_full AS
SELECT
  i.*,
  (SELECT json_group_array(json_object(
      'name', c.name,
      'contributorNameTypeId', c.contributorNameTypeId,
      'contributorTypeId', c.contributorTypeId,
      'contributorTypeText', c.contributorTypeText,
      'authorityId', c.authorityId,
      'isPrimary', c.isPrimary))
   FROM instance_contributors c WHERE c.instanceId = i.id) AS contributors,
  (SELECT json_group_array(json_object('value', s.value, 'authorityId', s.authorityId, 'sourceId', s.sourceId, 'typeId', s.typeId))
   FROM instance_subjects s WHERE s.instanceId = i.id) AS subjects,
  (SELECT json_group_array(json_object('languageCode', l.languageCode))
   FROM instance_languages l WHERE l.instanceId = i.id) AS languages,
  (SELECT json_group_array(json_object('value', id2.value, 'identifierTypeId', id2.identifierTypeId))
   FROM instance_identifiers id2 WHERE id2.instanceId = i.id) AS identifiers,
  (SELECT json_group_array(json_object('classificationNumber', cl.classificationNumber, 'classificationTypeId', cl.classificationTypeId))
   FROM instance_classifications cl WHERE cl.instanceId = i.id) AS classifications,
  (SELECT json_group_array(json_object('value', se.value, 'authorityId', se.authorityId))
   FROM instance_series se WHERE se.instanceId = i.id) AS series,
  (SELECT json_group_array(json_object('alternativeTitle', at.alternativeTitle, 'alternativeTitleTypeId', at.alternativeTitleTypeId, 'authorityId', at.authorityId))
   FROM instance_alternative_titles at WHERE at.instanceId = i.id) AS alternativeTitles,
  (SELECT json_group_array(json_object('publisher', p.publisher, 'place', p.place, 'dateOfPublication', p.dateOfPublication, 'role', p.role))
   FROM instance_publications p WHERE p.instanceId = i.id) AS publications,
  (SELECT json_group_array(json_object('value', e.value))
   FROM instance_editions e WHERE e.instanceId = i.id) AS editions,
  (SELECT json_group_array(json_object('formatId', f.formatId))
   FROM instance_format_ids f WHERE f.instanceId = i.id) AS formatIds,
  (SELECT json_group_array(statisticalCodeId) FROM instance_statistical_code_ids sc WHERE sc.instanceId = i.id) AS statisticalCodeIds,
  (SELECT json_group_array(termId) FROM instance_nature_of_content_term_ids nc WHERE nc.instanceId = i.id) AS natureOfContentTermIds,
  (SELECT json_group_array(tagValue) FROM instance_tags tg WHERE tg.instanceId = i.id) AS tags,
  (SELECT json_group_array(note) FROM instance_administrative_notes an WHERE an.instanceId = i.id) AS administrativeNotes,
  (SELECT json_group_array(json_object('note', n.note, 'staffOnly', n.staffOnly)) FROM instance_notes n WHERE n.instanceId = i.id) AS notes,
  (SELECT json_group_array(json_object('uri', ea.uri, 'linkText', ea.linkText, 'materialsSpecification', ea.materialsSpecification, 'publicNote', ea.publicNote, 'relationshipId', ea.relationshipId)) FROM instance_electronic_access ea WHERE ea.instanceId = i.id) AS electronicAccess,
  json_object('createdDate', im.createdDate, 'createdByUserId', im.createdByUserId, 'updatedDate', im.updatedDate, 'updatedByUserId', im.updatedByUserId) AS metadata,
  rit.name AS instanceTypeName
FROM instances i
LEFT JOIN ref_instance_types rit ON rit.id = i.instanceTypeId
LEFT JOIN instance_metadata  im  ON im.instanceId = i.id;

DROP VIEW IF EXISTS v_holdings_full;
CREATE VIEW v_holdings_full AS
SELECT
  h.*,
  rl.name  AS permanentLocationName,
  rht.name AS holdingsTypeName,
  rct.name AS callNumberTypeName,
  i.title  AS instanceTitle,
  (SELECT json_group_array(statisticalCodeId) FROM holdings_statistical_code_ids sc WHERE sc.holdingsId = h.id) AS statisticalCodeIds,
  (SELECT json_group_array(formerId) FROM holdings_former_ids fi WHERE fi.holdingsId = h.id) AS formerIds,
  (SELECT json_group_array(tagValue) FROM holdings_tags tg WHERE tg.holdingsId = h.id) AS tags,
  (SELECT json_group_array(note) FROM holdings_administrative_notes an WHERE an.holdingsId = h.id) AS administrativeNotes,
  (SELECT json_group_array(json_object('note', n.note, 'staffOnly', n.staffOnly)) FROM holdings_notes n WHERE n.holdingsId = h.id) AS notes,
  (SELECT json_group_array(json_object('uri', ea.uri, 'linkText', ea.linkText, 'materialsSpecification', ea.materialsSpecification, 'publicNote', ea.publicNote, 'relationshipId', ea.relationshipId)) FROM holdings_electronic_access ea WHERE ea.holdingsId = h.id) AS electronicAccess,
  json_object('createdDate', hm.createdDate, 'createdByUserId', hm.createdByUserId, 'updatedDate', hm.updatedDate, 'updatedByUserId', hm.updatedByUserId) AS metadata,
  rtl.name AS temporaryLocationName
FROM holdings h
LEFT JOIN ref_locations      rl  ON rl.id  = h.permanentLocationId
LEFT JOIN ref_locations      rtl ON rtl.id = h.temporaryLocationId
LEFT JOIN ref_holdings_types rht ON rht.id = h.holdingsTypeId
LEFT JOIN ref_call_number_types rct ON rct.id = h.callNumberTypeId
LEFT JOIN holdings_metadata  hm  ON hm.holdingsId = h.id
LEFT JOIN instances          i   ON i.id   = h.instanceId;

DROP VIEW IF EXISTS v_items_full;
CREATE VIEW v_items_full AS
SELECT
  it.*,
  rl.name   AS effectiveLocationName,
  rmt.name  AS materialTypeName,
  rct.name  AS effectiveCallNumberTypeName,
  h.callNumber AS holdingsCallNumber,
  i.title   AS instanceTitle,
  (SELECT json_group_array(formerId) FROM items_former_ids fi WHERE fi.itemId = it.id) AS formerIds,
  (SELECT json_group_array(statisticalCodeId) FROM items_statistical_code_ids sc WHERE sc.itemId = it.id) AS statisticalCodeIds,
  (SELECT json_group_array(tagValue) FROM items_tags tg WHERE tg.itemId = it.id) AS tags,
  (SELECT json_group_array(note) FROM items_administrative_notes an WHERE an.itemId = it.id) AS administrativeNotes,
  (SELECT json_group_array(json_object('note', n.note, 'staffOnly', n.staffOnly)) FROM items_notes n WHERE n.itemId = it.id) AS notes,
  (SELECT json_group_array(json_object('note', cn.note, 'staffOnly', cn.staffOnly)) FROM items_circulation_notes cn WHERE cn.itemId = it.id) AS circulationNotes,
  (SELECT json_group_array(json_object('uri', ea.uri, 'linkText', ea.linkText, 'materialsSpecification', ea.materialsSpecification, 'publicNote', ea.publicNote, 'relationshipId', ea.relationshipId)) FROM items_electronic_access ea WHERE ea.itemId = it.id) AS electronicAccess,
  json_object('createdDate', itm.createdDate, 'createdByUserId', itm.createdByUserId, 'updatedDate', itm.updatedDate, 'updatedByUserId', itm.updatedByUserId) AS metadata
FROM items it
LEFT JOIN ref_locations         rl  ON rl.id  = it.effectiveLocationId
LEFT JOIN ref_material_types    rmt ON rmt.id = it.materialTypeId
LEFT JOIN ref_call_number_types rct ON rct.id = it.effectiveCallNumberTypeId
LEFT JOIN items_metadata        itm ON itm.itemId = it.id
LEFT JOIN holdings              h   ON h.id   = it.holdingsRecordId
LEFT JOIN instances             i   ON i.id   = it.instanceId;

DROP VIEW IF EXISTS v_authority_full;
CREATE VIEW v_authority_full AS
SELECT
  a.*,
  rsf.name AS sourceFileName,
  (SELECT json_group_array(json_object('headingType', sft.headingType, 'value', sft.value))
   FROM authority_sft_headings sft WHERE sft.authorityId = a.id) AS sftHeadings,
  (SELECT json_group_array(json_object('headingType', saft.headingType, 'value', saft.value))
   FROM authority_saft_headings saft WHERE saft.authorityId = a.id) AS saftHeadings,
  (SELECT json_group_array(json_object('identifierTypeId', ai.identifierTypeId, 'identifierTypeName', rait.name, 'value', ai.value))
   FROM authority_identifiers ai
   LEFT JOIN ref_authority_identifier_types rait ON rait.id = ai.identifierTypeId
   WHERE ai.authorityId = a.id) AS identifiers,
  (SELECT json_group_array(json_object('noteTypeId', an.noteTypeId, 'noteTypeName', rant.name, 'value', an.value))
   FROM authority_notes an
   LEFT JOIN ref_authority_note_types rant ON rant.id = an.noteTypeId
   WHERE an.authorityId = a.id) AS notes
FROM authorities a
LEFT JOIN ref_authority_source_files rsf ON rsf.id = a.sourceFileId;
