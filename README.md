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

The module uses system user to communicate with other modules from Kafka consumers.
For production deployments you MUST specify the password for this system user via env variable:
`SYSTEM_USER_PASSWORD=<password>`.

### Configuring connection to elasticsearch

In order to configure connection to elasticsearch you have to provide following env variables:
* `ELASTICSEARCH_HOST` - host name of the elasticsearch master node (e.g. elasticsearch);
* `ELASTICSEARCH_PORT` - REST port of the elasticsearch master node (9200 by default);
* `ELASTICSEARCH_USERNAME` - username of the user to connect to elasticsearch;
* `ELASTICSEARCH_PASSWORD` - password for the user (see
  [official guide](https://www.elastic.co/guide/en/elasticsearch/reference/current/configuring-security.html)
  for more details about ES security).
