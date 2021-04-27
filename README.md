# mod-search

Copyright (C) 2020 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

FOLIO search project.

## Building

### Running integration tests

The module uses [Testcontainers](https://www.testcontainers.org/) to run Elasticsearch in embedded mode.
It is required to have Docker installed and available on the host where the tests are executed.

## Multi-language search support

Each tenant is allowed to pick up to **5** languages from pre-installed list for multi-language indexes (e.g. title, contributors, etc.).
This can be done via following API:
`POST /search/config/languages`
```javascript
{
  "code":"eng"
}
```

The `code` here is an ISO-639-2/B three-letter code. Here is the list of pre-installed languages analyzers:
- ara
- ger
- eng
- spa
- fre
- heb
- ita
- jpn
- kor
- rus
- swe
- chi

### Adding new languages via REST

It is allowed to add new languages via rest endpoint `/search/config/languages`.
**Please note, when you add a new language, a whole reindex is required in order to
apply new configuration**.

### Defining initial languages via ENV variable

It is possible to define initial languages via `INITIAL_LANGUAGES` env variable.
These languages will be added on tenant init and applied to index. Example usage:
`INITIAL_LANGUAGES=eng,fre,kor,chi,spa`. If the variable is not defined, only
`eng` code is added.

## Deploying

## Environment variables:

| Name                   | Default value             | Description                                                       |
| :----------------------| :------------------------:|:------------------------------------------------------------------|
| DB_HOST                | postgres                  | Postgres hostname                                                 |
| DB_PORT                | 5432                      | Postgres port                                                     |
| DB_USERNAME            | folio_admin               | Postgres username                                                 |
| DB_PASSWORD            | -                         | Postgres username password                                        |
| DB_DATABASE            | okapi_modules             | Postgres database name                                            |
| ~~ELASTICSEARCH_HOST~~ | elasticsearch             | (DEPRECATED, use ELASTICSEARCH_URL) Elasticsearch hostname        |
| ~~ELASTICSEARCH_POR~~  | 9200                      | (DEPRECATED, use ELASTICSEARCH_URL) Elasticsearch port            |
| ELASTICSEARCH_URL      | http://elasticsearch:9200 | Elasticsearch URL                                                 |
| ELASTICSEARCH_USERNAME | -                         | Elasticsearch username (not required for dev envs)                |
| ELASTICSEARCH_PASSWORD | -                         | Elasticsearch password (not required for dev envs)                |
| KAFKA_HOST             | kafka                     | Kafka broker hostname                                             |
| KAFKA_PORT             | 9092                      | Kafka broker port                                                 |
| INITIAL_LANGUAGES      | eng                       | Comma separated list of languages for multilang fields see [Multi-lang search support](#multi-language-search-support) |
| SYSTEM_USER_PASSWORD   | -                         | Password for `mod-search` system user (not required for dev envs) |
| OKAPI_URL              | -                         | OKAPI URL used to login system user, required                     |

The module uses system user to communicate with other modules from Kafka consumers.
For production deployments you MUST specify the password for this system user via env variable:
`SYSTEM_USER_PASSWORD=<password>`.

### Configuring connection to elasticsearch

In order to configure connection to elasticsearch you have to provide following env variables:
* `ELASTICSEARCH_URL` - URL to elasticsearch master node (e.g. http(s)://elasticsearch:9200);
* `ELASTICSEARCH_USERNAME` - username of the user to connect to elasticsearch;
* `ELASTICSEARCH_PASSWORD` - password for the user (see
  [official guide](https://www.elastic.co/guide/en/elasticsearch/reference/current/configuring-security.html)
  for more details about ES security).

## Indexing

### Recreating Elasticsearch index

Sometimes we need to recreate Elasticsearch index, for example when a breaking change introduced to ES index
structure (mapping).

Here are the steps how to do it:
1. Remove ES index:
```http
   DELETE [ES_HOST]/instance_[tenant_name]
```
2. Create index and mapping:
```http
POST [OKAPI_URL]/search/index/indices

x-okapi-tenant: [tenant]
x-okapi-token: [JWT_TOKEN]
content-type: application/json

{
  "resourceName": "instance"
}
```
3. Run reindex operation:
```http
POST [OKAPI_URL]/search/index/inventory/reindex

x-okapi-tenant: [tenant]
x-okapi-token: [JWT_TOKEN]
```

### Monitoring reindex process

There is no end-to-end monitoring implemented yet, however it is possible to monitor it partially. In order to check
how many records published to Kafka topic use inventory API:
```http
GET [OKAPI_URL]/instance-storage/reindex/[reindex job id]
```
_reindex job id_ - id returned by `/search/index/inventory/reindex` endpoint.

In order to estimate total records that actually added to the index, you can send a "match all" search query and check
`totalRecords`, e.g. `GET /search/instances?query=id="*"`.


## Supported search types

### Searching and filtering

The main endpoint that provides search capabilities is `GET /search/instances`. It consumes following
request parameters:

| Name          | Required             | Description                                                                     |
| :-------------| :-------------------:|:--------------------------------------------------------------------------------|
| query         | Yes                  | A CQL query to execute                                                          |
| limit         | No (default to 100)  | Maximum number of records to fetch                                              |
| offset        | No (default to 0)    | Instructs to skip first N records that matches the query                        |
| expandAll     | No (default to false)| If false than only basic instance properties returned, otherwise all properties |

We use CQL query for search queries, see [documentation](https://github.com/folio-org/raml-module-builder#cql-contextual-query-language)
for more details.

In mod-search there are two main types of searchable fields:
1. _Full text_ capable fields (aka. multi-lang fields) - analyzed and preprocessed fields;
2. _Term_ fields (keywords, bool, date fields, etc.) - non-analyzed fields.

Depending on field type, CQL operators will be handled in different ways or not supported at all.
Here is table of supported operators.

| Operator| Full text usage               | Term field usage             | Description |
| :------:| :----------------------------:|:----------------------------:|---------------------------------------------------------------------------------|
| `all`   | `title` `all` `"semantic web"`| N/A                          | Matches a resource that has both `semantic` and `web` in the `title` field                     |
| `any`   | `title` `any` `"semantic web"`| N/A                          | Matches a resource that has either of/both `semantic` or `web` in the `title` field                       |
| `=`     | `title = "semantic web"`      | `hrid = "hr10"`              | Has the same effect as `all` for FT fields and is the same as `==` for term fields             |
| `==`    | `title == "semantic web"`     | `hrid == "hr10"`             | Phrase match for FT fields (i.e. matches resources that contains both `semantic` and `web` exactly in the same order), exact match for term fields|
| `<>`    | `title <> "semantic web"`     | `hrid <> "hr10"`             | Matches resources that are not equal to a term                                  |
| `<`, `>`| N/A                           | `createdDate > "2020-12-12"` | Matches resources that has the property greater/less than the limit             |
| `<`, `>`| N/A                           | `createdDate <= "2020-12-12"`| Matches resources that has the property greater or eq/less or eq than the limit |
| `*`     | `title="mode* europe*"`       | `hrid = "hr10*"`             | Allow to search by wildcard, _**NOT recommended to use for FT fields because has low performance, use full text capabilities instead**_ |

Here is a table with supported search options.

#### Instance search options

| Option                                    | Type      |Example                                            | Description                  |
| :-----------------------------------------|:---------:| :-------------------------------------------------|:------------------------------|
| `keyword`                                 | full text | `keyword all "web semantic"`                      | An alias for: `title`, `alternativeTitles`, `indexTitle`, `series`, `identifiers.value`, `contributors.name` |
| `id`                                      | term      | `id=="1234567"`                                   | Matches instance with the id |
| `hrid`                                    | term      | `hrid=="hr1*0"`                                   | Matches instances with given HRID |
| `source`                                  | term      | `source=="MARC"`                                  | Matches instances with given source (FOLIO/MARC) |
| `title`                                   | full text | `title all "semantic web"`                        | Matches instances with the given title, searches against `title`, `alternativeTitles`, `indexTitle`, `series` fields |
| `alternativeTitles.alternativeTitle`      | full text | `alternativeTitles.alternativeTitle all "semantic web"` | Matches instances with the given alternative title |
| `indexTitle`                              | full text | `indexTitle all "semantic web"`                   | Matches instances with the given index title |
| `series`                                  | full text | `series all "series"`                             | Matches instance with given series value |
| `identifiers.value`                       | term      | `identifiers.value = "1023*"`                     | Matches instances with the given identifier value |
| `identifiers.identifierTypeId`            | term      | `identifiers.identifierTypeId=="123" identifiers.value = "1023*"` | Matches instances that have an identifier if type `123` with value `1023*`|
| `contributors`                            | full text | `contributors all "John"`                         | Matches instances that have a `John` contributor |
| `contributors.primary`                    | term      | `contributors all "John" and contributors.primary==true` | Matches instances that have a primary `John` contributor |
| `subjects`                                | full text | `subjects all "Chemistry"`                        | Matches instances that have a `Chemistry` subject |
| `instanceTypeId`                          | term      | `instanceTypeId == "123"`                         | Matches instances with the `123` type |
| `instanceFormatId`                        | term      | `instanceFormatId == "123"`                       | Matches instances with the `123` format id |
| `languages`                               | term      | `languages == "eng"`                              | Matches instances that have `eng` language |
| `metadata.createdDate`                    | term      | `metadata.createdDate > "2020-12-12"`             | Matches instances that were created after  `2020-12-12`|
| `metadata.updatedDate`                    | term      | `metadata.updatedDate > "2020-12-12"`             | Matches instances that were updated after  `2020-12-12`|
| `modeOfIssuanceId`                        | term      | `modeOfIssuanceId=="123"`                         | Matches instances that have `123` mode of issuance |
| `natureOfContentTermIds`                  | term      | `natureOfContentTermIds=="123"`                   | Matches instances that have `123` nature of content |
| `publisher`                               | full text | `publisher all "Publisher of Ukraine"`            | Matches instances that have `Publisher of Ukraine` publisher |
| `instanceTags`                            | term      | `instanceTags=="important"`                       | Matches instances that have `important` tag |
| `classifications.classificationNumber`    | term      | `classifications.classificationNumber=="cl1"`     | Matches instances that have `cl1` classification number |
| `electronicAccess`                        | full text | `electronicAccess any "resource"`                 | An alias for all `electronicAccess` fields - `uri`, `linkText`, `materialsSpecification`, `publicNote`|
| `electronicAccess.uri`                    | term      | `electronicAccess.uri="http://folio.org*"`        | Search by electronic access URI|
| `electronicAccess.linkText`               | full text | `electronicAccess.linkText="Folio website"`       | Search by electronic access link text |
| `electronicAccess.materialsSpecification` | full text | `electronicAccess.materialsSpecification="book"`  | Search by electronic access material specification |
| `electronicAccess.publicNote`             | full text | `electronicAccess.publicNote="a rare book"`       | Search by electronic access public note |
| `staffSuppress`                           | term      | `staffSuppress==true`                             | Matches instances that are staff suppressed |
| `discoverySuppress`                       | term      | `discoverySuppress==true`                         | Matches instances that are suppressed from discovery|
| `publicNotes`                             | full text | `publicNotes all "public note"`                   | Matches instances that have a public note (i.e. `note.staffOnly` is `false`) |
| `isbn`                                    | term      | `isbn="1234*943"`                                 | Matches instances that have an ISBN  identifier with the given value |
| `issn`                                    | term      | `issn="1234*943"`                                 | Matches instances that have an ISSN  identifier with the given value |


#### Holdings-records search options

| Option                        | Type |Example                                    | Description                  |
| :-----------------------------|:----:| :-----------------------------------------|:------------------------------|
| `holdings.id`                 | term | `holdings.id=="1234567"`                  | Matches instances that have a holding with the id |
| `holdings.permanentLocationId`| term | `holdings.permanentLocationId=="123765"`  | Matches instances that have holdings with given permanentLocationId |
| `holdings.discoverySuppress`  | term | `holdings.discoverySuppress==true`        | Matches instances that have holdings suppressed/not suppressed from discovery |
| `holdings.hrid`               | term | `holdings.hrid=="hr10*3"`                 | Matches instances that have a holding with given HRID |
| `holdingTags`                 | term | `holdingTags=="important"`                | Matches instances that have holdings with given tags |
| `holdingsFullCallNumbers`     | term | `holdingsFullCallNumbers="cn*434"`        | Matches instances that have holdings with given call number string (prefix + call number + suffix) |


#### Items search options

| Option                      | Type |Example                              | Description                  |
| :---------------------------|:----:| :-----------------------------------|:------------------------------|
| `items.id`                  | term | `items.id=="1234567"`               | Matches instances that have an item with the id |
| `items.hrid`                | term | `items.hrid=="it001"`               | Matches instances that have an item with the HRID |
| `items.barcode`             | term | `items.barcode=="10011"`            | Matches instances that have an item with the barcode |
| `items.effectiveLocationId` | term | `items.effectiveLocationId=="1212"` | Matches instances that have items with the effective location |
| `items.status.name`         | term | `items.status.name=="Available"`    | Matches instances that have items with given status |
| `items.materialTypeId`      | term | `items.materialTypeId="23434"`      | Matches instances that have items with given material type |
| `items.discoverySuppress`   | term | `items.discoverySuppress=true`      | Matches instances that have items suppressed/not suppressed from discovery |
| `itemsFullCallNumbers`      | term | `itemsFullCallNumbers="cn*434"`     | Matches instances that have items with given call number string (prefix + call number + suffix) |
| `itemTags`                  | term | `itemTags="important"`              | Matches instances that have items with given tag |


### Sorting results

The default sorting is by relevancy. The `sortBy` clause is used to define sorting, for example:
```
title all "semantic web" sortBy title/sort.descending - sort by title in descending order
```

Another supported sort options:
* title
* items.status.name
* contributors
