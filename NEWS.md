## v3.1.0 In progress
### Breaking changes
* Description ([ISSUE_NUMBER](https://issues.folio.org/browse/ISSUE_NUMBER))

### New APIs versions
* Provides `API_NAME vX.Y`
* Requires `API_NAME vX.Y`

### Features
* Update module descriptor with environment variables ([MSEARCH-635](https://issues.folio.org/browse/MSEARCH-635))
* Add filter to ignore hard-delete authority events ([MSEARCH-617](https://issues.folio.org/browse/MSEARCH-617))
* Update LccnProcessor to populate lccn field with only "LCCN" ([MSEARCH-630](https://issues.folio.org/browse/MSEARCH-630))
* Make maximum offset for additional elasticsearch request on browse configurable ([MSEARCH-641](https://issues.folio.org/browse/MSEARCH-641))
* Make system user usage optional ([MSEARCH-631](https://issues.folio.org/browse/MSEARCH-631))


### Bug fixes
* Fix secure setup of system users by default ([MSEARCH-608](https://issues.folio.org/browse/MSEARCH-608))
* Fix result filtering for items.effectiveLocationId ([MSEARCH-615](https://issues.folio.org/browse/MSEARCH-615))
* Ignore authority shadow copies while indexing ([MSEARCH-638](https://issues.folio.org/browse/MSEARCH-638))
* Return result only for desired cn type on browse ([MSEARCH-605](https://issues.folio.org/browse/MSEARCH-605))
* Fix clearing following filters in FacetService: items.effectiveLocationId, holdings.tenantId ([MSEARCH-620](https://issues.folio.org/browse/MSEARCH-620))
* Fix shared filter for subjects/contributors ([MSEARCH-639](https://issues.folio.org/browse/MSEARCH-639))
* Fix shadow instance's subjects/contributors indexing ([MSEARCH-647](https://issues.folio.org/browse/MSEARCH-647))

### Tech Dept
* Description ([ISSUE_NUMBER](https://issues.folio.org/browse/ISSUE_NUMBER))

### Dependencies
* Bump `folio-spring-support` from `7.2.0` to `7.2.1`

---

## v3.0.0 2023-10-13
### New APIs versions
* Remove required `instance-authority-links`
* Requires `alternative-title-types v1.0`
* Requires `identifier-types v1.0`
* Requires `call-number-types v1.0`
* Provides `indices v0.6`
* Provides `search v1.2`
* Provides `browse v1.2`

### Features
* Extend call-numbers browse endpoint to support filtering by types ([MSEARCH-514](https://issues.folio.org/browse/MSEARCH-514))
* Endpoint POST /search/resources/jobs have to be able to use long queries ([MSEARCH-520](https://issues.folio.org/browse/MSEARCH-520))
* Extend `reindex` endpoint with passing index settings ([MSEARCH-437](https://issues.folio.org/browse/MSEARCH-437))
* Create `PUT /search/index/settings` endpoint to update index settings  ([MSEARCH-436](https://issues.folio.org/browse/MSEARCH-436))
* Add browse option for NLM call number type  ([MSEARCH-527](https://issues.folio.org/browse/MSEARCH-527))
* Implement consortium index management  ([MSEARCH-531](https://issues.folio.org/browse/MSEARCH-531))
* Add shared, tenantId flags to mapping for consortium ([MSEARCH-532](https://issues.folio.org/browse/MSEARCH-532))
* Implement indexing for consortium tenants  ([MSEARCH-554](https://issues.folio.org/browse/MSEARCH-554))
* Implement Active Affiliation Context for Search in Consortia Mode  ([MSEARCH-533](https://issues.folio.org/browse/MSEARCH-533))
* Add tenantId/shared fields to contributors/subjects ([MSEARCH-551](https://issues.folio.org/browse/MSEARCH-551))
* Implement Active Affiliation Context for stream IDs in Consortia Mode ([MSEARCH-576](https://issues.folio.org/browse/MSEARCH-576))
* Restrict central tenant queries to only shared records ([MSEARCH-588](https://issues.folio.org/browse/MSEARCH-588))
* Implement Active Affiliation Context for browsing ([MSEARCH-580](https://issues.folio.org/browse/MSEARCH-580))
* Add new facets for shared/local flag and tenantId ([MSEARCH-534](https://issues.folio.org/browse/MSEARCH-534))
* Implement SuDoc call number type ([MSEARCH-569](https://issues.folio.org/browse/MSEARCH-569))
* Adjust GET /search/authorities to not include titles numberOfTitles ([MSEARCH-518](https://issues.folio.org/browse/MSEARCH-518))

### Bug fixes
* Fix bug when number of titles response is greater than real ([MSEARCH-526](https://issues.folio.org/browse/MSEARCH-526))
* Fix handling of invalid resource names in index management endpoints ([MSEARCH-540](https://issues.folio.org/browse/MSEARCH-540))
* Improve preceding records searching ([MSEARCH-552](https://issues.folio.org/browse/MSEARCH-552))
* Includes records with the same first 10 characters of the shelving order in preceding result ([MSEARCH-544](https://issues.folio.org/browse/MSEARCH-544))
* Fix searching by the first call number from browse result list ([MSEARCH-513](https://issues.folio.org/browse/MSEARCH-513))

### Tech Dept
* Change logic of linked titles count on authority search/browse ([MSEARCH-501](https://issues.folio.org/browse/MSEARCH-501))
* Kafka topic deletion by tenant id added when tenant disabled ([MSEARCH-541](https://issues.folio.org/browse/MSEARCH-541))
* Logging improvement ([MSEARCH-299](https://issues.folio.org/browse/MSEARCH-299))
* Allow Kafka Tenant Collection Topics ([MSEARCH-596](https://issues.folio.org/browse/MSEARCH-596))

### Dependencies
* Bump `folio-spring-support` from `6.1.0` to `7.2.0`
* Bump `folio-isbn-utils` from `1.5.0` to `1.6.0`
* Bump `folio-cql2pgjson` from `35.0.6` to `35.1.0`
* Bump `spring-boot-starter-parent` from `3.0.5` to `3.1.4`
* Bump `spring-kafka` from `3.0.5` to `3.0.11`
* Bump `opensearch` from `2.5.0` to `2.9.0`
* Bump `mapstruct` from `1.5.3.Final` to `1.5.5.Final`
* Bump `lombok` from `1.18.26` to `1.18.30`
* Bump `apache-commons-io` from `2.11.0` to `2.14.0`
* Bump `marc4j` from `2.9.2` to `2.9.5`
* Add `folio-service-tools` `3.1.0`

---

## v2.0.0 2023-02-16
### Breaking changes
* Migration to Java 17 ([MSEARCH-468](https://issues.folio.org/browse/MSEARCH-468))
* Migration to Spring Boot v3.0.2 ([MSEARCH-469](https://issues.folio.org/browse/MSEARCH-469))
* Align with subjects, series inventory-storage API changes ([MSEARCH-484](https://issues.folio.org/browse/MSEARCH-484))

### New APIs versions
* Provides `search v1.0`
* Provides `browse v1.0`
* Requires `instance-storage v10.0`
* Requires `inventory-view v2.0`

### Features
* Authority search/browse: add a field 'numberOfTitles' (number of linked instances) ([MSEARCH-433](https://issues.folio.org/browse/MSEARCH-433))
* Instance search: Add query search option that search instances by linked authority id ([MSEARCH-452](https://issues.folio.org/browse/MSEARCH-452))

### Bug fixes
* Fix call-number with same instance as anchor disappears ([MSEARCH-456](https://issues.folio.org/browse/MSEARCH-456))
* Fix instance keyword query search option when keyword contains special characters ([MSEARCH-466](https://issues.folio.org/browse/MSEARCH-466))

### Tech Dept
* Align logging configuration with common Folio solution ([MSEARCH-451](https://issues.folio.org/browse/MSEARCH-451))
* Stabilize contributors browse integration tests ([MSEARCH-479](https://issues.folio.org/browse/MSEARCH-479))
* Delegate system-user creation and Kafka topics creation to folio-service-tools library ([MSEARCH-487](https://issues.folio.org/browse/MSEARCH-487))

### Dependencies
* Bump `java` from `11` to `17`
* Bump `folio-spring-base` from `5.0.1` to `6.0.1`
* Bump `folio-isbn-utils` from `1.4.0` to `1.5.0`
* Bump `folio-cql2pgjson` from `35.0.0` to `35.0.6`
* Bump `spring-boot-starter-parent` from `2.7.4` to `3.0.2`
* Bump `spring-kafka` from `2.9.1` to `3.0.2`
* Bump `opensearch` from `2.3.0` to `2.5.0`
* Bump `mapstruct` from `1.5.2.Final` to `1.5.3.Final`
* Bump `lombok` from `1.18.22` to `1.18.26`
* Bump `testcontainers` from `1.17.5` to `1.17.6`
* Add `folio-service-tools` `3.0.0`

## 1.8.0 2022-10-27
* MSEARCH-349 Change Instance ids stream API to use jobs
* MSEARCH-350 Change Holdings ids stream API to use jobs
* MSEARCH-373 Add administrativeNotes search
* MSEARCH-382 500 Error When searching by incorrect date
* MSEARCH-385 Update module documentation
* MSEARCH-390 Stream ids job freeze IN\_PROGRESS if query is invalid
* MSEARCH-392 Stream ids job, relation doesn't exist while using multi tenant
* MSEARCH-396 Supports users interface versions 15.3 16.0
* MSEARCH-402 Add personal data disclosure form
* MSEARCH-410 Indexed the identifiers.identifierTypeId fields
* MSEARCH-416 Enable BROWSE_CN_INTERMEDIATE_VALUES and BROWSE_CN_INTERMEDIATE_REMOVE_DUPLICATES environment variables
* MSEARCH-418 Supports also instance-storage interface version 9.0
* MSEARCH-419 Different order when searching by keyword, holdingsPermanentLocation and sort by title desc
* MSEARCH-421 Add sourceFileId and naturalId to authority search/browse response
* MSEARCH-424 Create an Authority source facet
* MSEARCH-427 Add integration test option for Elasticsearch 8
* MSEARCH-430 Inventory Elastic-Search (Morning Glory). Keyword search throw unexpected results when the search only contains numbers and dashes.
* MSEARCH-435 Increase Kafka fetch size
* MSEARCH-442 Upgrade to folio-spring-base v5.0.0
* MSEARCH-439 Transferring items from one instance to another results in multiple matches when searching on barcode in Inventory
* MSEARCH-440 Enable GZIP and SMILE for client communication
* MSEARCH-441 Use default routing for indexing and searching
* MSEARCH-449 folio-spring-base v5.0.1 update

## 1.7.5 2022-09-02
* MSEARCH-414 Browse by call-numbers and shelvingOrder, add subfield for browsing

## 1.7.4 2022-08-17
* MSEARCH-401 Avoid using shelving order in call-number browsing

## 1.7.3 2022-08-10
* MSEARCH-405 Fix NullPointer while reindexing contributors

## 1.7.2 2022-08-09
* MSEARCH-405 Decrease index size by removing duplicates from _source

## 1.7.1 2022-07-25
* MSEARCH-393 Fix date query validation to support ISO date/time formats

## 1.7.0 2022-07-08
* MSEARCH-197 Add /authorities/ids API for Authority Records search
* MSEARCH-268 Increment the minor version of spring-kafka
* MSEARCH-282 Implement normalized search option for OCLC identifiers
* MSEARCH-297 Fix SearchAliases not working for fields with "searchTermProcessor"
* MSEARCH-301 Implement browsing by Dewey Decimal and Other schema numbers
* MSEARCH-302 Implement prev/next value for call-number browsing
* MSEARCH-306 Gracefully handle 500 error after the search reaches index.max_result_window
* MSEARCH-308 Add documentation for call-number browsing
* MSEARCH-308 Optimize call-number browsing
* MSEARCH-311 Create an Instance status facet
* MSEARCH-313 Add holdingsTypeId facet
* MSEARCH-315 Implement cursor parameters for browsing
* MSEARCH-317 Fix search by words with apostrophes
* MSEARCH-319 Implement search across fields using operator 'and'
* MSEARCH-320 Implement browsing by instance contributors
* MSEARCH-323 Make populating of intermediate shelf-keys configurable
* MSEARCH-324 Use match all query for 'keyword=*'
* MSEARCH-325 Item search/facet/sort options: Support 'item' references
* MSEARCH-325 Modify field in FacetQueryBuilder
* MSEARCH-331 Fix unsupported 'not' operator for search aliases
* MSEARCH-333 Optimize the query to retrieve subject counts
* MSEARCH-334 Fix the invalid search results for call-number with 2+ spaces
* MSEARCH-336 Fix item matching for browsing
* MSEARCH-337 Set request timeout for search queries
* MSEARCH-339 Call-Number browse: invalid search result call-number with ignored characters
* MSEARCH-341 ISSN search: Include Linking ISSN in search
* MSEARCH-342 Add default prev/next values for browsing forward/backward
* MSEARCH-342 Fix an issue for prev value when browsing around
* MSEARCH-344 Implement response fields per feature/endpoint
* MSEARCH-345 Fix optimization for call-number browsing
* MSEARCH-346 Revert non-title authority fields and add new headingTypeExt
* MSEARCH-352 Create a Contributor Name Type facet
* MSEARCH-357 Migration to Opensearch v2.0.0
* MSEARCH-365 Add support of 'string' modifier for '=='
* MSEARCH-368 Change headingTypeRef to bool isTitleHeadingRef
* MSEARCH-370 Add new reindexSupported field to resource description
* MSEARCH-371 Index contributors on reindex request
* MSEARCH-375 Update spring-base to v4.1.0
* MSEARCH-381 Delete contributors that have no links to instance records
* MSEARCH-382 Fix 500 Error when searching by incorrect date
* MSEARCH-388 Add filters to anchor-query for contributor browse

## 1.6.4 2022-04-07
* MSEARCH-336 Fix item matching for browsing by call-numbers
* MSEARCH-334 Fix the invalid search results for call-numbers with 2+ spaces

## 1.6.3 2022-04-05
* MSEARCH-331 Fix the"<>" operator for full-text queries
* MSEARCH-308 Fix the optimization of call-number browsing requests

## 1.6.2 2022-04-03
* MSEARCH-268 Increment the minor version of spring-kafka
* MSEARCH-323 Make populating of intermediate shelf-keys configurable
* MSEARCH-308 Add documentation for call-number browsing
* MSEARCH-308 Optimize call-number browsing
* MSEARCH-301 Implement browsing by Dewey Decimal and Other schema numbers

## 1.6.1 2022-03-21
* MSEARCH-308 Optimize call-number browsing
* MSEARCH-301 Implement browsing by Dewey Decimal and Other schema numbers
* FAT-1150 Update module descriptors to correctly delete tenant
* MSEARCH-303 Remove unused search fields

## 1.6.0 2022-02-18
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

## 1.5.4 2021-12-17
* Log4j vulnerability verification and correction (MSEARCH-255)

## 1.5.3 2021-11-18
* Added retry mechanism for streaming ids

## 1.5.2 2021-11-10
* Suppressed from discovery field added for instance/holdings/item (MSEARCH-223)
* Updated template for module descriptor

## 1.5.1 2021-10-25
* Fixed format filters (MSEARCH-199)

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

## 1.4.4 2021-12-17
* Log4j vulnerability verification and correction (MSEARCH-257)

## 1.4.3 2021-08-06
* Added  environment variable to support custom subscription pattern (MSEARCH-164)

## 1.4.2 2021-07-28
* Fixed "Failed to create index: Limit of total fields [250] has been exceeded" error (MSEARCH-160)

## 1.4.1 2021-07-20

* Supports full-text search for all instance notes (MSEARCH-116)
* Supports full-text search for public and all holding records notes (MSEARECH-117)
* Supports full-text search for public and all item notes (MSEARCH-118)
* Provides kafka topics names using ENV variable and tenant id (MSEARCH-132)
* Documents list of available facets (MSEARCH-133)
* Removes multi-language support from the contributors field (MSEARCH-134)
* Adds sorting adjustment by title using index title (MSEARCH-136)
* Handles gracefully Kafka message processing failures (MSEARCH-155)

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

## 1.3.1 2021-07-12

* Makes consumer group name different per environment (MSEARCH-129)
* Allows custom prefix when creating index name (MSEARCH-95)
* Provides kafka topics names created by pattern: `${env}.${tenantId}.inventory.(instance|item|holdings-record)`

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

## 1.2.1 2021-04-07

* Do not add resources if index does not exist (MSEARCH-86)
* Support DELETE event for items/holdings/instances (MSEARCH-90)
* Change multi-match operator from `OR` to `AND` (MSEARCH-91)

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
