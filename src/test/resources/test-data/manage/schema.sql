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
