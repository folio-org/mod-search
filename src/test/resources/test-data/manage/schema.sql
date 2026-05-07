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
  -- dates object (single, not array)
  dateTypeId        TEXT,
  date1             TEXT,
  date2             TEXT
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
  copyNumber          TEXT,
  shelvingTitle       TEXT,
  discoverySuppress   INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS items (
  id                          TEXT PRIMARY KEY,
  holdingsRecordId            TEXT REFERENCES holdings(id),
  instanceId                  TEXT REFERENCES instances(id),
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
  discoverySuppress           INTEGER DEFAULT 0
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
  authorityId TEXT
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

-- Convenience views
CREATE VIEW IF NOT EXISTS v_instance_full AS
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
  (SELECT json_group_array(json_object('value', s.value, 'authorityId', s.authorityId))
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
  rit.name AS instanceTypeName
FROM instances i
LEFT JOIN ref_instance_types rit ON rit.id = i.instanceTypeId;

CREATE VIEW IF NOT EXISTS v_holdings_full AS
SELECT
  h.*,
  rl.name  AS permanentLocationName,
  rht.name AS holdingsTypeName,
  rct.name AS callNumberTypeName,
  i.title  AS instanceTitle
FROM holdings h
LEFT JOIN ref_locations      rl  ON rl.id  = h.permanentLocationId
LEFT JOIN ref_holdings_types rht ON rht.id = h.holdingsTypeId
LEFT JOIN ref_call_number_types rct ON rct.id = h.callNumberTypeId
LEFT JOIN instances          i   ON i.id   = h.instanceId;

CREATE VIEW IF NOT EXISTS v_items_full AS
SELECT
  it.*,
  rl.name   AS effectiveLocationName,
  rmt.name  AS materialTypeName,
  rct.name  AS effectiveCallNumberTypeName,
  h.callNumber AS holdingsCallNumber,
  i.title   AS instanceTitle
FROM items it
LEFT JOIN ref_locations         rl  ON rl.id  = it.effectiveLocationId
LEFT JOIN ref_material_types    rmt ON rmt.id = it.materialTypeId
LEFT JOIN ref_call_number_types rct ON rct.id = it.effectiveCallNumberTypeId
LEFT JOIN holdings              h   ON h.id   = it.holdingsRecordId
LEFT JOIN instances             i   ON i.id   = it.instanceId;
