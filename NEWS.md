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
