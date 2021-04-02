## 1.2.0 2021-04-02

* Adds holdings.fullCallNumber search option (MSEARCH-81)
* Introduces ELASTICSEARCH_URL environment variable (MSEARCH-82)
* Throws more explicit error when tenant is not initialized yet (MSEARCH-72)
* Implements filtering by updated/created date (MSEARCH-30)
* Fixes issue when permissions file can not be read (MSEARCH-85)

## 1.1.0 2021-03-26

* Adds item material type facet and filter (MSEARCH-44)
* Adds item discovery suppress facet and filter (MSEARCH-33)
* Bumps elasticsearch-client version to 7.12.0
* Supports holdings HRID search (MSEARCH-26)
* Supports item effective call number search (MSEARCH-12)
* Adds item and holding tag facet and filter (MSEARCH-79)
* Provides `search v0.4`
* Fixes disjunction filters that affected related facet (MSEARCH-80)

## 1.0.0 2021-03-18

* Supports instance level search queries:
  * Instance UUID (MSEARCH-25)
  * Series, identifiers(all), electronicAccess (MSEARCH-1)
  * Title(all), alternativeTitles, indexTitle, contributors (MSEARCH-15)
  * Subjects (MSEARCH-19)
  * HRID (MSEARCH-2)
  * Publisher (MSEARCH-18)
  * Classifications (MSEARCH-20)
  * Notes, publicNotes (MSEARCH-21)
  * ISBN(normalized), ISSN (MSEARCH-24)
* Supports instance level filters/facets:
  * Suppress from discovery, staff suppress, languages, tags, source (MSEARCH-4)
* Allows sort instances by title and contributor (MSEARCH-40)
* Supports item level search queries:
  * Barcode (MSEARCH-31)
* Supports item level filters/facets:
  * Effective location (MSEARCH-55)
  * Status (MSEARCH-63)
* Supports holdings level filters/facets:
  * Suppress from discovery (MSEARCH-34);
  * Permanent locations (MSEARCH-56)
* Allows configuration of supported language analyzers for a tenant (MSEARCH-9)
* Can reindex all existing data (MSEARCH-42)
* Provides `indices v0.2` API
* Provides `search v0.3` API
* Provides `search-config v0.1` API
* Requires `instance-storage v7.5`
* Requires `login v7.0`
* Requires `permissions v5.3`
* Requires `users v15.3`
* Requires `inventory-view v1.0`
* Requires `instance-reindex v0.1`
