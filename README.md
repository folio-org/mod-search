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
