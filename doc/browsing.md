## Overview

### Query Syntax

Browsing can be performed in two directions - forward and backward. Browsing around combines results from these two
queries.

| Direction         | Query                               | Description                                                                                                              |
|:------------------|:------------------------------------|:-------------------------------------------------------------------------------------------------------------------------|
| forward           | `callNumber > F`                    | Request with that query will return all records after the specified anchor in ascending alphabetical order               |
| forward_including | `callNumber >= F`                   | Request with that query will return all records after the specified anchor in ascending alphabetical order including it  |
| backward          | `callNumber < F`                    | Request with that query will return all records before the specified anchor in ascending alphabetical order              |
| backward          | `callNumber < F`                    | Request with that query will return all records before the specified anchor in ascending alphabetical order including it |
| around            | `callNumber < F or callNumber > F`  | Request with that query will return all records around the anchor                                                        |
| around_including  | `callNumber < F or callNumber >= F` | Request with that query will return all records around the anchor including it                                           |

_where the `callNumber` is the name of field for browsing, `F` is the anchor value_

### Call-Number Browsing

[Call Number Browse API](https://s3.amazonaws.com/foliodocs/api/mod-search/s/mod-search.html#operation/browseInstancesByCallNumber)

#### Approach

_Numeric representation of `effectiveShelvingOrder` is used to narrow down the number of results in response. It
increases the response time significantly because by default there is no other way to sort instances in the index by
effective shelving key. This can be explained by the structure of the instance record. For Elasticsearch it contains all
fields from the instance and corresponding items\holding as inner arrays. This results in fields
like `effectiveShelvingOrder` to store multiple values. For correct browsing one of the values must be chosen every time
depending on the user input. Script-based sorting solves this problem because the right value from the array can be
chosen by the binary search algorithm from the core `Collections` class. Sorting of items before indexing operation
reduces the overall complexity of script sorting._

The implemented solution contains two parts:

1) instance resource index preparation contains such steps as:

- Sorting inner items by `effectiveShelvingOrder`. It's allows to have a prepared sorted list of shelf keys for browsing
- Calculating the long representation of the shelf key per each item using the
  following [algorithm](#string-to-number-algorithm-description)

2) Search query and result processing consist of the following steps:

- Creating the [exists query](https://www.elastic.co/guide/en/elasticsearch/reference/7.13/query-dsl-exists-query.html)
  that is used to browse only on instances that contain `effectiveShelvingOrder` field values
- Creating a painless script
  for [Script Based sorting](https://www.elastic.co/guide/en/elasticsearch/reference/7.13/sort-search-results.html#script-based-sorting)
  depending on the direction for browsing (for `around` and `around-including` two queries are sent in the one `msearch`
  request)
- Processing received search hits from Elasticsearch by the `mod-search` service:
  - All records before are populated by the shelf-key value that can be faced between the results (for example, if one
    instance contains two items with call-numbers `A11` and `A12` and the next - `B12`, `B13` only first values
    from `effectiveShelvingOrder` arrays will be found by Elasticsearch script. The `A12` value must be populated in
    search results too because this is a valid call-number value);
  - Adjacent records with the same `effectiveShelvingOrder` are collapsed together according to the acceptance criteria
    of the story
  - If it is the browsing around or around including - the anchor value is highlighted with the boolean flag
    - `isAnchor` (This can be disabled by passing query parameter - `highlightMatch=false`)
  - Extra records exceeding the requested limit are removed (for browsing around and around including limit is
    calculated from the query parameters - `size` and `precedingRecordsCount`)

#### String to number algorithm

This algorithm helps in the runtime to reduce the number of records for Script-Based Sorting for Elasticsearch. It
consists of the following parts:

- Cleaning the input value by removing the invalid characters (only A-Z letters, digits, dot, slash and space are
  allowed)
- For each value the unique integer value is generated:
  - only 10 first characters can be used, then the long value will be overflowed
  - space is equal to 0, `.` is 1, `/` is 2, `0` is 3 and `A` is 14, 'Z' is 39
  - Each value is calculated by following formula (`{integer value} * 39 ^ (10 - {character position}`)
