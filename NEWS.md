## 1.6.2 2021-04-03
* MSEARCH-268 Increment the minor version of spring-kafka
* MSEARCH-323 Make populating of intermediate shelf-keys configurable
* MSEARCH-308 Add documentation for call-number browsing
* MSEARCH-308 Optimize call-number browsing
* MSEARCH-301 Implement browsing by Dewey Decimal and Other schema numbers

## 1.6.1 2021-03-21
* MSEARCH-308 Optimize call-number browsing
* MSEARCH-301 Implement browsing by Dewey Decimal and Other schema numbers
* FAT-1150 Update module descriptors to correctly delete tenant
* MSEARCH-303 Remove unused search fields

## 1.6.0 2021-02-18
* MSEARCH-186 publish isBoundWith in search results
* MSEARCH-272 Include Item identifier in Identifier (all) search
* MSEARCH-253 Implement Call Number browse
* MSEARCH-287 Create a Subject heading/thesaurus facet
* MSEARCH-288 Update Authority Search Options - Keyword
* MSEARCH-271 Authority record search - Mapping saft* fields to heading types
* MSEARCH-294 Support search aliases for the facets
* MSEARCH-295 Rename search fields in Authority mode
* MSEARCH-292 Update Authority Search Options - Corporate/Conference Name & Personal Name
* MSEARCH-193 Statistical code facet for instances, holdings and items
* MSEARCH-276 Subject browsing: Support delete events to prevent values with 0 instances
* MSEARCH-256 Implement Authority headings browse
* MSEARCH-273 folio-spring-base v3 update
* MSEARCH-184 Implement retrieving holdings ids as json stream
* MSEARCH-217 Authority record search - Name/title search option
* MSEARCH-208 Authority record search - filters/facets
* MSEARCH-185 Implement cql.allRecords=1 search option
* MSEARCH-181 Implement search for null, empty or non-exiting values
* MSEARCH-175 Item - include circulation notes search in item notes searches
* MSEARCH-195 Add search API for Authority’s Record
* MSEARCH-202 Holdings - filter/facet by holdings source
* MSEARCH-212 Authority record search - Determine Authorized/Reference
* MSEARCH-213 Authority record search - Personal name search option
* MSEARCH-214 Authority record search - Corporate/conference name search option
* MSEARCH-237 Authority record search - Authority UUID search option
* MSEARCH-238 Authority record search - Keyword search option
* MSEARCH-229 Authority record search - Uniform title search option
* MSEARCH-196 Refactor /reindex API to support Authority Records
* MSEARCH-230 Authority record search - Subject search option
* MSEARCH-232 Authority record search - Genre search option
* MSEARCH-211 Authority record search - Determine Heading/Reference
* MSEARCH-209 Authority record search - sorting
* MSEARCH-231 Authority record search - Children's subject headings search option
* MSEARCH-177 Don't skip resources if reference data has been failed to load
* MSEARCH-236 Authority record search - Identifiers search option
* MSEARCH-260 Authority record search - Mapping additional fields to headings
* MSEARCH-210 Authority record search - Determine type of heading
* MSEARCH-219 Filter holdings\items by Date created
* MSEARCH-220 Filter holdings\items by Date updated
* MSEARCH-264 Implement Subject browse - preceding entries and placeholder for missing match
* MSEARCH-275 Highlight browse result using Boolean value

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
