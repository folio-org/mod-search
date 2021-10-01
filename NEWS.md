## 1.5.0 2021-10-01
* Add contributorNameTypeId field for contributors (MSEARCH-194)
* Support search by all fields (MSEARCH-182)
* Support normalized search for call numbers (MSEARCH-169)
* Add Identifiers accessionNumber search option for items (MSEARCH-153)
* Search by item's circulation notes search (MSEARCH-158)
* Forbid to add language = 'src' for tenant configuration (MSEARCH-162)
* Add Identifiers (all) search option for holding and items (MSEARCH-152/153)
* Support search by series title (MSEARCH-150)
* Support search by uniform title (MSEARCH-148)
* Handle gracefully message processing failures (MSEARCH-155)
* Support full text search for notes of linked holding-records (MSEARCH-117)
* Remove multi-language support from the contributors fields (MSEARCH-134 )

## 1.4.0 2021-06-18

* Adds ASCII folding token filter (MSEARCH-67)
* Allows using different Korean language analyzer (MSEARCH-89)
* Supports remove and index requests as a single bulk request. (MSEARCH-94)
* Allows custom prefix when creating index name (MSEARCH-95)
* Adds documentation of query syntax (MSEARCH-102)
* Adds Kafka TLS environment variables for consumers (MSEARCH-105)
* Support holdings' keyword search within electronic access fields (MSEARCH-109)
* Supports items keyword search within electronic access fields (MSEARCH-110)
* Adds ability to recreate index before starting reindex (MSEARCH-121)
* Simplifies resource specification for search (MSEARCH-122)
* Retrieves facets values for selected facets (MSEARCH-125)
* Optimizes indexing process for field processors (MSEARCH-126)
* Adds list of specified plugins required for on-premise Elasticsearch (MSEARCH-127)
* Makes consumer group name different per environment (MSEARCH-129)

## 1.3.0 2021-04-22

* Makes sure ES index exists before indexing resources (MSEARCH-112)
* Fixes cache for index exists method (MSEARCH-101)
* Fixes sorting for long titles (MSEARCH-99)
* Upgrades spring-boot-starter version to `2.4.4` (MSEARCH-98)
* Upgrades spring-cloud:feign version to `3.0.1` (MSEARCH-98)
* Removes `system_user` table (MSEARCH-97)
* Uses in-memory cache to store system user details (MSEARCH-97)
* Requires `OKAPI_URL` env variable (MSEARCH-97)
* Introduces phrase match for full text fields (MSEARCH-92)
* Improves full text search by `all` operator to match specification (MSEARCH-91)
* Handles `DELETE` events for resources (MSEARCH-90)
* Increases Kafka retry period to 20 seconds (MSEARCH-86)
* Implements `GET/DELETE` `/_/tenant` endpoints (MSEARCH-45)
* Implements sorting by item status (MSEARCH-41)
* Provides `search v0.5`

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
