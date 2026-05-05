# Design: Optimize Integration Tests to Reduce Build Time (MSEARCH-1036)

## Problem

Building `mod-search` including all tests takes ~15 minutes, dominated by integration tests.
Two root causes drive this:

1. **Spring context restarts**: `BaseIntegrationTest` carries `@DirtiesContext(classMode = AFTER_CLASS)`,
   which destroys and rebuilds the full Spring application context after every test class.
   With ~59 IT classes, this means 59 full Spring Boot startups (bean wiring, OpenSearch index
   template registration, Kafka consumer setup, etc.) even though all Testcontainers stay alive.

2. **Repeated tenant setup/teardown**: Every IT class calls `setUpTenant` in `@BeforeAll` and
   `removeTenant` in `@AfterAll`. Each setup creates an OpenSearch index, sends Kafka messages,
   and polls until records are indexed. With 59 classes this happens 59 times.

## Objective

Reduce IT execution time by sharing the Spring application context and consolidating tenant
setup across related tests.

---

## Design

### Part 1 — Remove `@DirtiesContext` from `BaseIntegrationTest`

**Change**: Remove `@DirtiesContext(classMode = AFTER_CLASS)` from `BaseIntegrationTest`.

Spring's test context cache already shares application contexts across test classes that carry
identical `@SpringBootTest` configuration. With `@DirtiesContext` gone, the context is created
once per JVM run rather than once per class.

**Consequence for `ElasticSearchContainerExtension`**: The `afterAll` callback currently calls
`System.clearProperty("spring.opensearch.uris")`. With a shared context the URL is already baked
into the context — clearing the property has no effect on a running context but can confuse
context cache key resolution. The `afterAll` implementation should be simplified: it can keep
the property set (or simply be a no-op) because the static `CONTAINER` remains alive for the
whole JVM run and `beforeAll` will always set the property before any new context starts.

**Risk**: tests that modify shared Spring-managed state (Spring beans, caches) across class
boundaries. Audit of the existing test classes shows no such mutations:
- Feature flags (`enableFeature`) are per-tenant and removed when `removeTenant` is called.
- `setEnvProperty("folio-test")` in `BaseIntegrationTest.@BeforeAll` is idempotent and
  always sets the same value.
- `OpensearchCompressionIT` carries its own `@SpringBootTest(properties = {…})`, so Spring
  keeps it in a separate cached context — it is unaffected.

---

### Part 2 — Consolidate non-consortium search/browse tests into `SearchBrowseIT`

A new parent class `SearchBrowseIT extends BaseIntegrationTest` is created at
`src/test/java/org/folio/api/SearchBrowseIT.java`.

It owns the lifecycle of one shared `TENANT_ID` tenant:

```
@BeforeAll setUpSharedTenant()
  enableTenant(TENANT_ID)
  enableFeature(SEARCH_ALL_FIELDS)

  // Instance records
  load: semantic-web instance           → covers SearchInstance / SearchHoldings / SearchItem
  load: sort instances                  → covers SortInstance / SortInstanceByTitle
  load: filter instances (with items/holdings) → covers SearchInstanceFilter / SortItem
  load: empty-value instances           → covers SearchByEmptyValues
  load: call-number instances           → covers BrowseCallNumber
  load: classification instances        → covers BrowseClassification
  load: contributor instances           → covers BrowseContributor
  load: subject instances               → covers BrowseSubject

  // Authority records
  load: authority records               → covers SearchAuthority / SearchAuthorityFilter /
                                          SortAuthority / BrowseAuthority

  // Linked-data records
  load: linked-data instances           → covers SearchLinkedDataInstance
  load: linked-data works               → covers SearchLinkedDataWork
  load: linked-data hubs                → covers SearchLinkedDataHub

  // Wait for each resource type to reach its expected total count, using
  // checkThatEventsFromKafkaAreIndexed(TENANT_ID, <searchPath>, <totalCount>, ...)
  // called once per resource type after all records of that type are submitted.

@AfterAll cleanUpSharedTenant()
  removeTenant(TENANT_ID)
```

Each former standalone IT class becomes a `@Nested` inner class inside `SearchBrowseIT`.
The nested class contains only the test methods (the `@BeforeAll`/`@AfterAll` from the
original class are deleted — the outer class already manages setup and teardown).

**Naming convention for nested classes**: drop the `IT` suffix and keep the descriptive
name, e.g. `SearchInstanceIT` → `@Nested class SearchInstance`.

#### Full list of classes migrated into `SearchBrowseIT`

| Former file | `@Nested` class name |
|---|---|
| `SearchInstanceIT` | `SearchInstance` |
| `SearchHoldingsIT` | `SearchHoldings` |
| `SearchItemIT` | `SearchItem` |
| `SearchByAllFieldsIT` | `SearchByAllFields` |
| `SearchByEmptyValuesIT` | `SearchByEmptyValues` |
| `SearchInstanceFilterIT` | `SearchInstanceFilter` |
| `SortInstanceIT` | `SortInstance` |
| `SortInstanceByTitleIT` | `SortInstanceByTitle` |
| `SortItemIT` | `SortItem` |
| `BrowseCallNumberIT` | `BrowseCallNumber` |
| `BrowseClassificationIT` | `BrowseClassification` |
| `BrowseContributorIT` | `BrowseContributor` |
| `BrowseSubjectIT` | `BrowseSubject` |
| `SearchAuthorityIT` | `SearchAuthority` |
| `SearchAuthorityFilterIT` | `SearchAuthorityFilter` |
| `SortAuthorityIT` | `SortAuthority` |
| `BrowseAuthorityIT` | `BrowseAuthority` |
| `SearchLinkedDataInstanceIT` | `SearchLinkedDataInstance` |
| `SearchLinkedDataWorkIT` | `SearchLinkedDataWork` |
| `SearchLinkedDataHubIT` | `SearchLinkedDataHub` |

The original IT files are deleted after their content is moved.

---

### Part 3 — Tests that remain as standalone IT classes

The following classes are **not** merged into `SearchBrowseIT` because they require
fundamentally different tenant configurations or Spring contexts:

| Class | Reason |
|---|---|
| `SearchInstanceConsortiumIT` | Needs CENTRAL + MEMBER tenants |
| `BrowseCallNumberConsortiumIT` | Needs CENTRAL + MEMBER tenants |
| `BrowseClassificationConsortiumIT` | Needs CENTRAL + MEMBER tenants |
| `BrowseContributorConsortiumIT` | Needs CENTRAL + MEMBER tenants |
| `BrowseSubjectConsortiumIT` | Needs CENTRAL + MEMBER tenants |
| `SearchLinkedDataInstanceConsortiumIT` | Needs CENTRAL tenant |
| `SearchLinkedDataWorkConsortiumIT` | Needs CENTRAL tenant |
| `ConsortiumSearch*IT` (6 classes) | Needs CENTRAL + MEMBER tenants |
| `IndexManagementIT` | Tests index lifecycle directly |
| `ConfigIT` | Tests configuration endpoints |
| `StreamResourceIdsIT` | Tests streaming response |
| `OpensearchCompressionIT` | Different `@SpringBootTest` properties |
| All `Indexing*IT` (11 classes in `org.folio.indexing`) | Tests Kafka ingestion, not search/browse API |
| All `reindex/*IT` (7 classes) | Tests reindex JDBC/Kafka flows |
| `KafkaMessageListenerIT` | Tests Kafka integration |
| `ReindexKafkaListenerIT` | Tests Kafka integration |

---

### Part 4 — Superset data design and non-interference

Because all records share one tenant, test data must be designed so test groups do not
interfere with each other:

1. **Browse tests depend on specific value ranges.** Call-number, subject, contributor, and
   classification browse tests anchor their queries around distinct value prefixes/ranges.
   No other test data uses those same prefixes. Examples: call-number browse data uses the
   ranges from the existing `CallNumberTestData` helper; the semantic-web instance and sort
   instances do not have call numbers that fall in those ranges (verify during implementation
   and adjust data constants if needed).

2. **All test groups use fixed, distinct record IDs.** IDs from one group (e.g., sort
   instances) will never collide with IDs from another group (e.g., filter instances) because
   each group already declares its own UUID constant arrays.

3. **Authority records are separate from instance records** in their own OpenSearch index and
   do not interfere with instance search/browse results.

---

### Part 5 — Assertion refactoring

Tests that currently assert on full-tenant counts must be updated to scope their queries.
The required changes fall into two patterns:

**Pattern A – setup-time matchers removed, replaced by in-test scoped queries:**
Applies to `SearchInstanceFilterIT` and `SortItemIT`, which pass matchers to `setUpTenant`
that check `sum($.instances..holdings.length())` across ALL records. With a shared tenant
these matchers fire after ALL instances are loaded and will count wrong.
Fix: remove the matchers from setup; instead, each test that needs to verify holdings/items
count queries by the specific instance IDs belonging to that test group.

```java
// Before (in setup)
var holdingsMatcher = jsonPath("sum($.instances..holdings.length())", is(7.0));
setUpTenant(List.of(holdingsMatcher, itemsMatcher), instances());

// After (in-test)
doSearchByInstances("id==(" + String.join(" OR ", Arrays.asList(IDS)) + ")")
    .andExpect(jsonPath("sum($.instances..holdings.length())", is(7.0)));
```

**Pattern B – browse window assertions tightened:**
If extra instances from other groups happen to share a browse value range, exact-list
assertions (`assertThat(result.getItems()).isEqualTo(expectedList)`) are converted to
`containsSubsequence` or `contains` checks that verify the expected items are present
and ordered correctly without requiring an exact-size list.

Apply Pattern B only where a test actually fails due to extra data in the window.
Prefer fixing the test data (Pattern A) over loosening assertions.

---

### Part 6 — `SearchByAllFieldsIT` special case

`SearchByAllFieldsIT` currently enables `SEARCH_ALL_FIELDS` before creating instances
because the feature changes how records are indexed. In the shared setup, `enableFeature`
is called once at the start of `setUpSharedTenant`, before any records are loaded.
All shared instances are therefore indexed with the feature enabled.

The existing non-all-fields search tests (`SearchInstanceIT`, etc.) query by specific
field names and are not affected by the all-fields index being present. Enabling the
feature globally does not break regular field-scoped searches.

---

## What stays unchanged

- `BaseConsortiumIntegrationTest` and all consortium IT classes — untouched in this task.
- All `Indexing*IT` and `reindex/*IT` tests — untouched.
- All unit tests (`*Test.java`) — untouched.

---

## Success criteria

1. All existing tests pass after the refactoring.
2. Total CI build time reduced measurably (target: from ~15 min toward ~8 min).
3. No new test data or assertions introduce fragility (i.e., tests that pass locally fail
   in CI due to race conditions or timing).
4. The original IT files are deleted; no dead code remains.
