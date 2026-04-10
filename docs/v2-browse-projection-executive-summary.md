# V2 Browse Projection Executive Summary

## Problem

The current V2 browse projection rebuild path queries OpenSearch for all documents matching a touched browse ID and rebuilds the browse document from those hits. That query is hard-capped at 10,000 hits in [V2BrowseProjectionRepository.java](/Users/okolawole/git/folio/mod-search/src/main/java/org/folio/search/repository/V2BrowseProjectionRepository.java).

For high-cardinality browse values, this produces silently incorrect browse documents:

- counts are truncated
- tenant and shared membership can be incomplete
- no failure is raised, so the error is hard to detect

## Decision Framing

We want a solution that is:

- exact for very large browse values
- operationally simple
- scalable as browse cardinality grows
- compatible with the current V2 indexing architecture

## Recommended Option

Adopt a two-step strategy:

1. Replace the capped OpenSearch fetch with exact streaming rebuilds from OpenSearch.
2. If rebuild cost becomes too high in production, move to relation-backed incremental materialization.

This separates the immediate correctness fix from the larger architectural optimization.

## Option Summary

### Option A: Stream exact matches from OpenSearch and aggregate in application code

Replace the current `size(...)` query with a scroll or `search_after` based stream of all matching records for the touched browse IDs. Aggregate incrementally in Java and write the final browse documents as today.

Why this is the recommended first step:

- fixes correctness immediately
- minimal change to current V2 design
- works for contributors, subjects, classifications, and call numbers
- preserves existing browse document shape
- avoids the complexity of large server-side aggregation queries

Primary drawback:

- rebuild cost is still proportional to the number of matching records for the touched browse ID

### Option B: Store normalized browse relations and aggregate at query time

Persist relation rows such as `(browse_id, instance_id, tenant_id, shared, type_id, location_id)` and let queries compute counts and grouping on demand.

Benefits:

- source data stays normalized and exact
- avoids precomputing summary structures during writes
- simple write semantics

Drawbacks:

- read-time aggregation becomes more expensive
- browse latency may degrade for large values
- call number queries remain more complex because they require grouped instance sets

Best when:

- write simplicity is more important than read performance
- browse queries are infrequent or tightly constrained

### Option C: Incremental materialization from normalized relations

Persist normalized relation rows as the source of truth, then update browse documents incrementally when records change. The browse index becomes a materialized view rather than the source of truth.

Benefits:

- exact regardless of browse cardinality
- cost is proportional to the changed record, not the popularity of the browse value
- rebuilds remain possible from normalized data
- browse reads stay fast because the summary document still exists

Drawbacks:

- highest implementation complexity
- requires reliable diffing of old versus new browse relations
- requires idempotent update handling for retries and duplicate events

Best when:

- very hot browse IDs are common
- rebuild cost from OpenSearch becomes operationally expensive
- the team is willing to accept more write-path complexity

## Why Not Lead With Query-Side OpenSearch Aggregations

OpenSearch aggregations can solve part of the problem for some browse types, but they are not the best primary approach here.

Issues:

- nested and reverse-nested aggregations increase query complexity
- representative metadata still has to be recovered per browse ID
- call number browse requires exact grouped `instanceId` sets, which is awkward to express efficiently in aggregations
- paging large bucket spaces is more complex than streaming matching documents

This makes aggregation-first design harder to reason about and harder to operate than streaming exact hits.

## Recommendation

Near term:

- implement exact streaming rebuilds from OpenSearch
- remove the 10,000-hit correctness risk
- add metrics for scanned hit count and rebuild duration

Long term:

- if hot browse values make rebuild cost too high, introduce normalized relation storage as the source of truth
- optionally materialize browse documents incrementally for fast reads

## Executive Conclusion

The best immediate move is to keep the current V2 browse projection model but make it exact by streaming all matching records instead of capping results. If that still proves too expensive for very popular browse values, the strategic destination is normalized relation storage with incremental materialization, not larger capped queries or more elaborate OpenSearch aggregations.
