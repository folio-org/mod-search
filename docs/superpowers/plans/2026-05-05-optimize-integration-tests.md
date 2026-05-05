# Integration Test Build-Time Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce the IT suite build time from ~15 minutes by removing the per-class Spring context restart and consolidating 20 non-consortium IT classes that share a single tenant into one `SearchBrowseIT` class with `@Nested` inner classes.

**Architecture:** Remove `@DirtiesContext(classMode = AFTER_CLASS)` from `BaseIntegrationTest` so the Spring Boot application context is created once per JVM run and shared across all IT classes. Create `SearchBrowseIT` as a thin orchestrator whose `@BeforeAll` loads all data once and `@AfterAll` tears it down; each of the 20 original IT files is made `abstract` (lifecycle removed, test methods kept), and `SearchBrowseIT` contains one `@Nested` inner class per original IT that simply `extends` it — e.g. `@Nested class SearchInstance extends SearchInstanceIT {}`. Original files are preserved, not deleted.

**Tech Stack:** Java 21, JUnit 5, Spring Boot Test, OpenSearch (via Testcontainers), Kafka, Mockito.

---

## Background: why this is safe

Testcontainers (`ElasticSearchContainerExtension`, `EnableKafka`, `EnablePostgres`) already use **static** fields and are shared across the whole JVM run. Only the Spring `ApplicationContext` is recreated on every `@DirtiesContext` class — pure overhead. Removing it causes Spring's context-cache to reuse the same context for all classes that share the same configuration key.

`OpensearchCompressionIT` carries its own `@SpringBootTest(properties = {...})` which changes the context key → it always gets its own cached context → **not affected** by this change.

---

## File map

| Action | File |
|--------|------|
| Modify | `src/test/java/org/folio/support/base/BaseIntegrationTest.java` |
| Modify | `src/test/java/org/folio/support/extension/impl/ElasticSearchContainerExtension.java` |
| Create | `src/test/java/org/folio/api/SearchBrowseIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/search/SearchInstanceIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/search/SearchHoldingsIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/search/SearchItemIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/search/SearchByAllFieldsIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/search/SortInstanceIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/search/SortInstanceByTitleIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/search/SearchByEmptyValuesIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/search/SearchInstanceFilterIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/search/SortItemIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/browse/BrowseCallNumberIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/browse/BrowseClassificationIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/browse/BrowseContributorIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/browse/BrowseSubjectIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/search/SearchAuthorityIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/search/SearchAuthorityFilterIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/search/SortAuthorityIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/browse/BrowseAuthorityIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/search/SearchLinkedDataInstanceIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/search/SearchLinkedDataWorkIT.java` |
| Modify → abstract (Tasks 3–10) | `src/test/java/org/folio/api/search/SearchLinkedDataHubIT.java` |

### Architecture note: abstract classes + `@Nested` subclasses

Each of the 20 original `*IT` files is modified to be an `abstract` class. This does two things:
1. JUnit no longer discovers it directly (no standalone execution).
2. Its test methods are inherited by the `@Nested` concrete subclass inside `SearchBrowseIT`.

The `@BeforeAll`/`@AfterAll` lifecycle methods are **removed** from every `*IT` class (the outer class `SearchBrowseIT` owns the lifecycle). Data factory methods (`instances()`, `authorities()`, etc.) and assertion helpers (`scoped()`) stay in the `*IT` class alongside the test methods.

Inside `SearchBrowseIT`, each nested stub looks like:
```java
@Nested class SearchInstance extends SearchInstanceIT {}
```
JUnit 5 runs `SearchInstance` as a nested test class; it inherits all `@Test` / `@ParameterizedTest` methods from `SearchInstanceIT`. The outer `setUpSharedTenant` runs once before any of the nested classes' tests execute.

**Annotation inheritance caution:** if a `*IT` class carries `@TestPropertySource` or `@SpringBootTest(properties=...)` that differ from `SearchBrowseIT`, the nested subclass would create a different Spring context cache key. Before making a class abstract, check it has no additional Spring configuration annotations beyond `@IntegrationTest`. If it does, remove them from the abstract class (they belong on `SearchBrowseIT` instead).

---

## Task 1: Remove `@DirtiesContext` and simplify `ElasticSearchContainerExtension`

**Files:**
- Modify: `src/test/java/org/folio/support/base/BaseIntegrationTest.java`
- Modify: `src/test/java/org/folio/support/extension/impl/ElasticSearchContainerExtension.java`

### Step 1.1 – Remove `@DirtiesContext` from `BaseIntegrationTest`

In `BaseIntegrationTest.java`, remove the import and the annotation:

```java
// REMOVE these two lines:
import org.springframework.test.annotation.DirtiesContext;
// and
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS;
// and the annotation itself (around line 106):
@DirtiesContext(classMode = AFTER_CLASS)
```

The class declaration becomes:

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@IntegrationTest
@ExtendWith({ElasticSearchContainerExtension.class, EnableKafka.class, EnablePostgres.class})
public abstract class BaseIntegrationTest {
```

### Step 1.2 – Make `ElasticSearchContainerExtension.afterAll` a no-op

Open `src/test/java/org/folio/support/extension/impl/ElasticSearchContainerExtension.java`.

Replace the current `afterAll` body (which calls `System.clearProperty("spring.opensearch.uris")`) with an empty implementation:

```java
@Override
public void afterAll(ExtensionContext context) {
  // no-op: container remains alive for the full JVM run;
  // the property must not be cleared because the shared Spring context still uses it.
}
```

### Step 1.3 – Verify the change compiles and existing ITs still run

Run one small IT to confirm the shared context loads without error:

```bash
cd /home/pavlo_smahin/IdeaProjects/mod-search
mvn -pl . -Dtest=OpensearchCompressionIT test -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`, test passes.

### Step 1.4 – Commit

```bash
git add src/test/java/org/folio/support/base/BaseIntegrationTest.java \
        src/test/java/org/folio/support/extension/impl/ElasticSearchContainerExtension.java
git commit -m "test: remove @DirtiesContext and make ElasticSearch container extension afterAll a no-op

Spring context is now shared across all ITs in the same JVM run.
Testcontainers were already shared; only the Spring context was being
restarted unnecessarily on every class.

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 2: Create the `SearchBrowseIT` outer skeleton

**Files:**
- Create: `src/test/java/org/folio/api/SearchBrowseIT.java`

This task creates the outer class with its shared `@BeforeAll`/`@AfterAll` and **empty** `@Nested` stubs. Subsequent tasks fill in each nested class. The outer class loads ALL test data for ALL 20 original ITs before any test method runs.

### Step 2.1 – Understand what the shared setup must do

The `@BeforeAll` must:
1. Call `enableTenant(TENANT_ID)` once.
2. Enable all 5 features: `SEARCH_ALL_FIELDS`, `BROWSE_CALL_NUMBERS`, `BROWSE_CONTRIBUTORS`, `BROWSE_SUBJECTS`, `BROWSE_CLASSIFICATIONS`.
3. Acquire sub-resource reindex locks for all 4 browse types *before* loading any instances, because the lock signals to the indexer that a consistent batch load is in progress.
4. Push all instance records (every group, all classes) to the inventory API without waiting between groups.
5. Perform the contributor-deletion mutation (`INSTANCES[0].setContributors(emptyList())`) while the lock is still held, because the resulting contributor count (13) must be established before the lock is released.
6. Release all 4 locks.
7. Push all authority records to Kafka.
8. Push all linked-data records (instances, works, hubs).
9. Wait for the main indexes to reach their final expected counts using `checkThatEventsFromKafkaAreIndexed`.
10. Wait for each sub-resource index to reach its expected count using Awaitility.

The `@AfterAll` calls `removeTenant(TENANT_ID)`.

### Step 2.2 – Compute total record counts

Fill in these counts by reading each nested class's data declarations AFTER you implement the nested classes in Tasks 3–10, then come back and update the constants here. A formula comment is provided for each.

```java
// At the top of SearchBrowseIT, add these constants once all nested classes exist:
// (update values after Tasks 3-10 are done)

// Total instances = semantic-web(1) + SortInstanceIT.INSTANCES.length + SortInstanceByTitleIT.INSTANCES.length
//   + SearchByEmptyValuesIT.INSTANCES.length
//   + BrowseCallNumberIT.INSTANCES.length + BrowseClassificationIT.INSTANCES.length
//   + BrowseContributorIT.INSTANCES.length + BrowseSubjectIT.INSTANCES.length
//   + SearchAuthorityIT.LINKED_INSTANCES.length
private static final int TOTAL_INSTANCES = 0; // FILL IN

// Total authorities = SearchAuthority sample(51) + SearchAuthorityFilterIT.AUTHORITIES.length
//   + SortAuthorityIT.AUTHORITIES.length + BrowseAuthorityIT.AUTHORITIES.length
private static final int TOTAL_AUTHORITIES = 0; // FILL IN

// Sub-resource counts — from the original BrowseXxxIT's await assertions:
private static final int EXPECTED_CALL_NUMBER_COUNT  = 100; // from BrowseCallNumberIT
private static final int EXPECTED_CONTRIBUTOR_COUNT  = 13;  // from BrowseContributorIT (after deletion)
private static final int EXPECTED_CLASSIFICATION_COUNT = 19; // from BrowseClassificationIT
private static final int EXPECTED_SUBJECT_COUNT      = 28;  // from BrowseSubjectIT
```

**Important:** the sub-resource index counts are for records contributed by the browse test instances only, but other test instances (semantic-web, sort, filter) **must not** add call-numbers, contributors, subjects, or classifications to their data — otherwise the expected counts above will be wrong. This is the *non-interference* invariant. Verify it empirically when running in Task 11 by comparing actual sub-resource counts against the expected values; adjust if necessary.

### Step 2.3 – Create `SearchBrowseIT.java`

```java
package org.folio.api;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CALL_NUMBERS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CLASSIFICATIONS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CONTRIBUTORS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_SUBJECTS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.SEARCH_ALL_FIELDS;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.authoritySearchPath;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;

import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = "folio.search-config.indexing.instance-children-index-enabled=true")
class SearchBrowseIT extends BaseIntegrationTest {

  // ─── record counts (fill in after Tasks 3-10) ───────────────────────────────
  private static final int TOTAL_INSTANCES       = 0; // FILL IN (sum of all nested class instance counts)
  private static final int TOTAL_AUTHORITIES     = 0; // FILL IN (sum of all nested class authority counts)
  private static final int EXPECTED_CALL_NUMBER_COUNT    = 100;
  private static final int EXPECTED_CONTRIBUTOR_COUNT    = 13;
  private static final int EXPECTED_CLASSIFICATION_COUNT = 19;
  private static final int EXPECTED_SUBJECT_COUNT        = 28;
  // ────────────────────────────────────────────────────────────────────────────

  @BeforeAll
  static void setUpSharedTenant(@Autowired SubResourcesLockRepository subResourcesLockRepository) {
    enableTenant(TENANT_ID);

    // Features that change how records are indexed must be enabled BEFORE loading data.
    enableFeature(SEARCH_ALL_FIELDS);
    enableFeature(BROWSE_CALL_NUMBERS);
    enableFeature(BROWSE_CONTRIBUTORS);
    enableFeature(BROWSE_SUBJECTS);
    enableFeature(BROWSE_CLASSIFICATIONS);

    // Acquire sub-resource locks before batch-loading instances.
    var cnLock = subResourcesLockRepository.lockSubResource(ReindexEntityType.CALL_NUMBER, TENANT_ID)
      .orElseThrow(() -> new IllegalStateException("Unable to lock CALL_NUMBER resource"));
    var contribLock = subResourcesLockRepository.lockSubResource(ReindexEntityType.CONTRIBUTOR, TENANT_ID)
      .orElseThrow(() -> new IllegalStateException("Unable to lock CONTRIBUTOR resource"));
    var classifLock = subResourcesLockRepository.lockSubResource(ReindexEntityType.CLASSIFICATION, TENANT_ID)
      .orElseThrow(() -> new IllegalStateException("Unable to lock CLASSIFICATION resource"));
    var subjLock = subResourcesLockRepository.lockSubResource(ReindexEntityType.SUBJECT, TENANT_ID)
      .orElseThrow(() -> new IllegalStateException("Unable to lock SUBJECT resource"));

    // ─── Push all instance groups (references to abstract *IT classes) ──────────
    // Semantic-web instance (Task 3)
    // inventoryApi.createInstance(TENANT_ID, SampleInstances.getSemanticWebAsMap());
    // Arrays.stream(SortInstanceIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i));       // Task 4
    // Arrays.stream(SortInstanceByTitleIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i)); // Task 4
    // Arrays.stream(SearchByEmptyValuesIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i)); // Task 5
    // Arrays.stream(BrowseCallNumberIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i));    // Task 7
    // Arrays.stream(BrowseClassificationIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i));// Task 7
    // Arrays.stream(BrowseContributorIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i));   // Task 8
    // BrowseContributorIT.INSTANCES[0].setContributors(Collections.emptyList()); inventoryApi.updateInstance(TENANT_ID, BrowseContributorIT.INSTANCES[0]); // Task 8
    // Arrays.stream(BrowseSubjectIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i));       // Task 8
    // (SearchInstanceFilterIT and SortItemIT use semantic-web — no extra instances)

    // ─── Contributor deletion (Task 8) ───────────────────────────────────────────────────
    // BrowseContributorIT.INSTANCES[0].setContributors(emptyList());
    // inventoryApi.updateInstance(TENANT_ID, BrowseContributorIT.INSTANCES[0]);

    // ─── Release sub-resource locks ──────────────────────────────────────────────────────
    subResourcesLockRepository.unlockSubResource(ReindexEntityType.CALL_NUMBER, cnLock, TENANT_ID);
    subResourcesLockRepository.unlockSubResource(ReindexEntityType.CONTRIBUTOR, contribLock, TENANT_ID);
    subResourcesLockRepository.unlockSubResource(ReindexEntityType.CLASSIFICATION, classifLock, TENANT_ID);
    subResourcesLockRepository.unlockSubResource(ReindexEntityType.SUBJECT, subjLock, TENANT_ID);

    // ─── Push authority records to Kafka (Tasks 9) ───────────────────────────────────────
    // (Authority lines added in Task 9)

    // ─── Push linked-data records (Task 10) ──────────────────────────────────────────────
    // (Linked-data lines added in Task 10)

    // ─── Wait for main resource indexes ──────────────────────────────────────────────────
    // Uncomment/fill in these waits as each Task populates TOTAL_INSTANCES / TOTAL_AUTHORITIES.
    // checkThatEventsFromKafkaAreIndexed(TENANT_ID, instanceSearchPath(), TOTAL_INSTANCES, emptyList());
    // checkThatEventsFromKafkaAreIndexed(TENANT_ID, authoritySearchPath(), TOTAL_AUTHORITIES, emptyList());

    // ─── Wait for sub-resource indexes ───────────────────────────────────────────────────
    // Uncomment after all browse instance data is loaded (Tasks 7-8):
    // await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
    //   assertThat(countIndexDocument(ResourceType.INSTANCE_CALL_NUMBER, TENANT_ID))
    //     .isEqualTo(EXPECTED_CALL_NUMBER_COUNT));
    // await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
    //   assertThat(countIndexDocument(ResourceType.INSTANCE_CONTRIBUTOR, TENANT_ID))
    //     .isEqualTo(EXPECTED_CONTRIBUTOR_COUNT));
    // await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
    //   assertThat(countIndexDocument(ResourceType.INSTANCE_CLASSIFICATION, TENANT_ID))
    //     .isEqualTo(EXPECTED_CLASSIFICATION_COUNT));
    // await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
    //   assertThat(countIndexDocument(ResourceType.INSTANCE_SUBJECT, TENANT_ID))
    //     .isEqualTo(EXPECTED_SUBJECT_COUNT));
  }

  @AfterAll
  static void cleanUpSharedTenant() {
    removeTenant(TENANT_ID);
  }

  // ─── Nested class stubs (wired in Tasks 3-10) ────────────────────────────────
  // Each stub is a one-liner that extends the corresponding abstract *IT class.
  // JUnit discovers and runs all @Test / @ParameterizedTest methods inherited from it.
  @Nested class SearchInstance extends SearchInstanceIT {}
  @Nested class SearchHoldings extends SearchHoldingsIT {}
  @Nested class SearchItem extends SearchItemIT {}
  @Nested class SearchByAllFields extends SearchByAllFieldsIT {}
  @Nested class SortInstance extends SortInstanceIT {}
  @Nested class SortInstanceByTitle extends SortInstanceByTitleIT {}
  @Nested class SearchByEmptyValues extends SearchByEmptyValuesIT {}
  @Nested class SearchInstanceFilter extends SearchInstanceFilterIT {}
  @Nested class SortItem extends SortItemIT {}
  @Nested class BrowseCallNumber extends BrowseCallNumberIT {}
  @Nested class BrowseClassification extends BrowseClassificationIT {}
  @Nested class BrowseContributor extends BrowseContributorIT {}
  @Nested class BrowseSubject extends BrowseSubjectIT {}
  @Nested class SearchAuthority extends SearchAuthorityIT {}
  @Nested class SearchAuthorityFilter extends SearchAuthorityFilterIT {}
  @Nested class SortAuthority extends SortAuthorityIT {}
  @Nested class BrowseAuthority extends BrowseAuthorityIT {}
  @Nested class SearchLinkedDataInstance extends SearchLinkedDataInstanceIT {}
  @Nested class SearchLinkedDataWork extends SearchLinkedDataWorkIT {}
  @Nested class SearchLinkedDataHub extends SearchLinkedDataHubIT {}
}
```

### Step 2.4 – Verify the class compiles

```bash
mvn -pl . test-compile -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

### Step 2.5 – Commit

```bash
git add src/test/java/org/folio/api/SearchBrowseIT.java
git commit -m "test: add SearchBrowseIT skeleton with shared tenant setup/teardown

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 3: Make semantic-web IT classes abstract and wire them in

**Files:**
- Modify: `src/test/java/org/folio/api/search/SearchInstanceIT.java`
- Modify: `src/test/java/org/folio/api/search/SearchHoldingsIT.java`
- Modify: `src/test/java/org/folio/api/search/SearchItemIT.java`
- Modify: `src/test/java/org/folio/api/search/SearchByAllFieldsIT.java`
- Modify: `src/test/java/org/folio/api/SearchBrowseIT.java`

All four classes load the same single semantic-web instance (`getSemanticWebAsMap()`). Their test methods query by specific semantic-web IDs and field values — safe in a shared tenant.

### Step 3.1 – Check for extra Spring annotations on each class

Open each of the four `*IT` files. Verify that none of them carry `@TestPropertySource`, `@ActiveProfiles`, or `@SpringBootTest(properties=...)` beyond what is already on `BaseIntegrationTest`. If any do, note those properties — they will need to be on `SearchBrowseIT` instead (which already has `@TestPropertySource(properties = "folio.search-config.indexing.instance-children-index-enabled=true")`).

### Step 3.2 – Make each class `abstract` and remove lifecycle methods

For each of the four files, apply this pattern (shown for `SearchInstanceIT`):

```java
// BEFORE:
@IntegrationTest
class SearchInstanceIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(Instance.class, getSemanticWebMatchers(), getSemanticWebAsMap());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant(TENANT_ID);
  }

  @Test
  void search_positive() { ... }
  // ... more test methods
}

// AFTER:
@IntegrationTest
abstract class SearchInstanceIT extends BaseIntegrationTest {
  // @BeforeAll and @AfterAll removed entirely

  @Test
  void search_positive() { ... }
  // ... all test methods unchanged
}
```

Repeat identically for `SearchHoldingsIT`, `SearchItemIT`, `SearchByAllFieldsIT`.

**`SearchByAllFieldsIT` note:** The original `@BeforeAll` calls `enableFeature(SEARCH_ALL_FIELDS)` before loading the instance. This feature is now enabled in the outer `setUpSharedTenant` before any data is pushed. Just remove the `@BeforeAll`; the test methods are unchanged.

### Step 3.3 – Add semantic-web instance loading to `setUpSharedTenant`

In `SearchBrowseIT.setUpSharedTenant`, inside the lock-held block (after lock acquisition, before unlock), add:

```java
// Semantic-web instance — used by SearchInstance, SearchHoldings, SearchItem, SearchByAllFields
inventoryApi.createInstance(TENANT_ID,
    org.folio.support.sample.SampleInstances.getSemanticWebAsMap());
```

### Step 3.4 – Verify the `@Nested` stubs already exist in `SearchBrowseIT`

The stubs were written in Task 2:
```java
@Nested class SearchInstance extends SearchInstanceIT {}
@Nested class SearchHoldings extends SearchHoldingsIT {}
@Nested class SearchItem extends SearchItemIT {}
@Nested class SearchByAllFields extends SearchByAllFieldsIT {}
```
No changes needed here — the stubs inherit test methods automatically.

### Step 3.5 – Compile and run

```bash
mvn -pl . test-compile -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS` (the abstract classes compile cleanly; `SearchBrowseIT` compiles with the stubs).

```bash
mvn -pl . -Dtest="SearchBrowseIT" test -q 2>&1 | tail -30
```
Expected: tests from all four nested classes run. They may fail with `totalRecords is 0` until the wait is activated in Task 11 — that is acceptable at this stage.

### Step 3.6 – Commit

```bash
git add src/test/java/org/folio/api/search/SearchInstanceIT.java \
        src/test/java/org/folio/api/search/SearchHoldingsIT.java \
        src/test/java/org/folio/api/search/SearchItemIT.java \
        src/test/java/org/folio/api/search/SearchByAllFieldsIT.java \
        src/test/java/org/folio/api/SearchBrowseIT.java
git commit -m "test: make SearchInstance/Holdings/Item/ByAllFields ITs abstract, wire into SearchBrowseIT

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 4: Make sort instance IT classes abstract — fix randomId and scope queries

**Files:**
- Modify: `src/test/java/org/folio/api/search/SortInstanceIT.java`
- Modify: `src/test/java/org/folio/api/search/SortInstanceByTitleIT.java`
- Modify: `src/test/java/org/folio/api/SearchBrowseIT.java`

**Problem:** Both classes use `randomId()` for instance IDs and query `cql.allRecords=1 sortBy ...` asserting `totalRecords is 5` or `totalRecords is 13`. With a shared tenant these counts are wrong. Fix: replace `randomId()` calls with fixed UUID constants in the `*IT` files, then scope every `allRecordsSortedBy(...)` query with an `id==(...)` prefix.

### Step 4.1 – Fix `SortInstanceIT`: replace `randomId()` with fixed UUIDs

In `SortInstanceIT.java`, the IDs are currently declared as:
```java
private static final String ID_ANIMAL_FARM     = randomId();
private static final String ID_ZERO_MINUS_TEN  = randomId();
private static final String ID_CALLING_ME_HOME = randomId();
private static final String ID_WALK_IN_MY_SOUL = randomId();
private static final String ID_STAR_WARS       = randomId();
```

Replace with fixed UUIDs and add an ID filter constant:

```java
static final String ID_ANIMAL_FARM     = "a0000001-sort-inst-0000-000000000001";
static final String ID_ZERO_MINUS_TEN  = "a0000002-sort-inst-0000-000000000002";
static final String ID_CALLING_ME_HOME = "a0000003-sort-inst-0000-000000000003";
static final String ID_WALK_IN_MY_SOUL = "a0000004-sort-inst-0000-000000000004";
static final String ID_STAR_WARS       = "a0000005-sort-inst-0000-000000000005";

static final String SORT_INSTANCE_ID_FILTER =
    "id==(%s OR %s OR %s OR %s OR %s)".formatted(
        ID_ANIMAL_FARM, ID_ZERO_MINUS_TEN, ID_CALLING_ME_HOME, ID_WALK_IN_MY_SOUL, ID_STAR_WARS);
```

Also expose the instances array so `setUpSharedTenant` can reference it:
```java
static final Instance[] INSTANCES = instances(); // already exists, just make package-accessible
```

### Step 4.2 – Scope sort queries in `SortInstanceIT`

Every call to `doSearchByInstances(allRecordsSortedBy(...))` in `SortInstanceIT` must be prefixed with the ID filter. The `allRecordsSortedBy(field, order)` helper produces `"cql.allRecords=1 sortBy field/sort.order"`. Replace each occurrence:

```java
// Before:
doSearchByInstances(allRecordsSortedBy("contributors", ASCENDING))
    .andExpect(jsonPath("totalRecords", is(5)))

// After:
doSearchByInstances(SORT_INSTANCE_ID_FILTER + " sortBy contributors/sort.ascending")
    .andExpect(jsonPath("totalRecords", is(5)))
```

Apply to **every test method** in `SortInstanceIT` that uses `allRecordsSortedBy` or `cql.allRecords=1`. The error-case test using `attemptSearchByInstances(allRecordsSortedBy("unknownSort", DESCENDING))` does not need scoping because it expects a 400 response and is independent of data.

### Step 4.3 – Make `SortInstanceIT` abstract and remove lifecycle

```java
// BEFORE:
@IntegrationTest
class SortInstanceIT extends BaseIntegrationTest {
  @BeforeAll static void prepare() { setUpTenant(instances()); }
  @AfterAll  static void cleanUp()  { removeTenant(TENANT_ID); }
  // ...
}

// AFTER:
@IntegrationTest
abstract class SortInstanceIT extends BaseIntegrationTest {
  // @BeforeAll and @AfterAll removed
  // all test methods unchanged, ID fields and SORT_INSTANCE_ID_FILTER added in 4.1/4.2
}
```

### Step 4.4 – Fix `SortInstanceByTitleIT`: replace `randomId()` with fixed UUIDs

In `SortInstanceByTitleIT.java`, instances are created via:
```java
private static Instance[] instances() {
  return TITLES.stream()
    .map(title -> new Instance().id(randomId()).title(title))
    .toArray(Instance[]::new);
}
```

Add a parallel `TITLE_IDS` list with 13 fixed UUIDs, change the factory to use them, and expose the array:

```java
static final List<String> TITLE_IDS = List.of(
    "b0000001-sort-titl-0000-000000000001",
    "b0000002-sort-titl-0000-000000000002",
    "b0000003-sort-titl-0000-000000000003",
    "b0000004-sort-titl-0000-000000000004",
    "b0000005-sort-titl-0000-000000000005",
    "b0000006-sort-titl-0000-000000000006",
    "b0000007-sort-titl-0000-000000000007",
    "b0000008-sort-titl-0000-000000000008",
    "b0000009-sort-titl-0000-000000000009",
    "b0000010-sort-titl-0000-000000000010",
    "b0000011-sort-titl-0000-000000000011",
    "b0000012-sort-titl-0000-000000000012",
    "b0000013-sort-titl-0000-000000000013"
);

static final String SORT_TITLE_ID_FILTER = "id==(" + String.join(" OR ", TITLE_IDS) + ")";

static final Instance[] INSTANCES = instances();

private static Instance[] instances() {
  var ids = TITLE_IDS.iterator();
  return TITLES.stream()
    .map(title -> new Instance().id(ids.next()).title(title))
    .toArray(Instance[]::new);
}
```

### Step 4.5 – Scope sort query in `SortInstanceByTitleIT`

```java
// Before:
doSearchByInstances("cql.allRecords=1 sortBy title")
    .andExpect(jsonPath("totalRecords", is(13)))
    .andExpect(jsonPath("instances[*].title", is(expectedTitleOrder)));

// After:
doSearchByInstances(SORT_TITLE_ID_FILTER + " sortBy title")
    .andExpect(jsonPath("totalRecords", is(13)))
    .andExpect(jsonPath("instances[*].title", is(expectedTitleOrder)));
```

### Step 4.6 – Make `SortInstanceByTitleIT` abstract and remove lifecycle

```java
// AFTER:
@IntegrationTest
abstract class SortInstanceByTitleIT extends BaseIntegrationTest {
  // @BeforeAll and @AfterAll removed
}
```

### Step 4.7 – Add instance loading to `setUpSharedTenant`

In `SearchBrowseIT.setUpSharedTenant` (inside the lock-held block):

```java
// SortInstance group (5 instances)
Arrays.stream(SortInstanceIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i));

// SortInstanceByTitle group (13 instances)
Arrays.stream(SortInstanceByTitleIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i));
```

Note: reference the `*IT` class directly (not the nested stub), since the `INSTANCES` field is on the abstract class.

### Step 4.8 – Compile and run

```bash
mvn -pl . test-compile -q 2>&1 | tail -10
mvn -pl . -Dtest="SearchBrowseIT#SortInstance*+SearchBrowseIT#SortInstanceByTitle*" test -q 2>&1 | tail -30
```

Expected: all tests pass.

### Step 4.9 – Commit

```bash
git add src/test/java/org/folio/api/search/SortInstanceIT.java \
        src/test/java/org/folio/api/search/SortInstanceByTitleIT.java \
        src/test/java/org/folio/api/SearchBrowseIT.java
git commit -m "test: make SortInstance/ByTitle ITs abstract; fix randomId, scope sort queries

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 5: Make `SearchByEmptyValuesIT` abstract — fix randomId and scope queries

**Files:**
- Modify: `src/test/java/org/folio/api/search/SearchByEmptyValuesIT.java`
- Modify: `src/test/java/org/folio/api/SearchBrowseIT.java`

**Problem:** Uses `randomId()` for two instance IDs; every query uses `cql.allRecords=1` with `totalRecords is 2` or `totalRecords is 0`. With shared data, `cql.allRecords=1` returns many more than 2 records.

### Step 5.1 – Add fixed IDs and ID filter to `SearchByEmptyValuesIT`

```java
static final String INSTANCE_ID_1 = "c0000001-emptyv-0000-000000000001";
static final String INSTANCE_ID_2 = "c0000002-emptyv-0000-000000000002";

private static final String ID_FILTER =
    "id==(%s OR %s)".formatted(INSTANCE_ID_1, INSTANCE_ID_2);

static final Instance[] INSTANCES = instances();
// instances() factory unchanged except using INSTANCE_ID_1 / INSTANCE_ID_2 instead of randomId()
```

### Step 5.2 – Add `scoped()` helper and update parameterized test

Add a private helper in `SearchByEmptyValuesIT`:

```java
private String scoped(String query) {
  return ID_FILTER + " AND (" + query + ")";
}
```

Change `doSearchByInstances(query + " sortBy title")` to `doSearchByInstances(scoped(query) + " sortBy title")`:

```java
@ParameterizedTest
@CsvSource({
  "cql.allRecords=1, title1;title2",
  "instanceTypeId=\"\", title1;title2",
  "isbn=\"\",",
  "cql.allRecords=1 NOT isbn=\"\", title1;title2",
  "cql.allRecords=1 NOT instanceTypeId=\"\",",
  "contributors.name==[], title2",
  "indexTitle=\"\" NOT indexTitle==\"\", title1",
  "cql.allRecords=1 NOT indexTitle=\"\", title2",
  "subjects.value==[], title1;title2",
})
void search_parameterized(String query, String titles) throws Exception {
  var expectedTitles = StringUtils.isNotEmpty(titles) ? asList(titles.split(";")) : null;
  doSearchByInstances(scoped(query) + " sortBy title")
    .andExpect(jsonPath("totalRecords", is(expectedTitles == null ? 0 : expectedTitles.size())))
    .andExpect(expectedTitles == null
               ? jsonPath("instances[*].title").doesNotExist()
               : jsonPath("instances[*].title", is(expectedTitles)));
}
```

### Step 5.3 – Make `SearchByEmptyValuesIT` abstract and remove lifecycle

```java
@IntegrationTest
abstract class SearchByEmptyValuesIT extends BaseIntegrationTest {
  // @BeforeAll and @AfterAll removed; test methods + helpers unchanged
}
```

### Step 5.4 – Add instance loading to `setUpSharedTenant`

```java
// SearchByEmptyValues group (2 instances)
Arrays.stream(SearchByEmptyValuesIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i));
```

### Step 5.5 – Compile and run

```bash
mvn -pl . -Dtest="SearchBrowseIT#SearchByEmptyValues*" test -q 2>&1 | tail -30
```

Expected: all parameterized test cases pass.

### Step 5.6 – Commit

```bash
git add src/test/java/org/folio/api/search/SearchByEmptyValuesIT.java \
        src/test/java/org/folio/api/SearchBrowseIT.java
git commit -m "test: make SearchByEmptyValuesIT abstract; fix randomId, scope all-records queries

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 6: Make filter and sort-item IT classes abstract (Pattern A refactoring)

**Files:**
- Modify: `src/test/java/org/folio/api/search/SearchInstanceFilterIT.java`
- Modify: `src/test/java/org/folio/api/search/SortItemIT.java`
- Modify: `src/test/java/org/folio/api/SearchBrowseIT.java`

**Problem:** Both classes pass `holdingsMatcher(N)` and `itemsMatcher(M)` to `setUpTenant`, which verify that exactly N holdings / M items are indexed. With a shared tenant these counts are wrong. Fix: remove setup-time matchers; test methods that need holdings/items counts already query by specific IDs so they remain correct.

### Step 6.1 – Remove setup matchers from `SearchInstanceFilterIT`

In `SearchInstanceFilterIT`, the `@BeforeAll` currently looks like:
```java
setUpTenant(Instance.class, holdingsMatcher(7), itemsMatcher(8), getSemanticWebAsMap());
```
The entire `@BeforeAll`/`@AfterAll` is removed (same as Tasks 3–5). The `holdingsMatcher`/`itemsMatcher` checks are dropped permanently from setup; the actual holdings/items queries in test methods are scoped by `getSemanticWebId()` and remain correct.

Make `SearchInstanceFilterIT` abstract:
```java
@IntegrationTest
abstract class SearchInstanceFilterIT extends BaseIntegrationTest {
  // @BeforeAll and @AfterAll removed; all test methods unchanged
}
```

### Step 6.2 – Verify `SearchInstanceFilterIT` test methods are already safe

Open each `@Test` method. Confirm that every `jsonPath("totalRecords", is(N))` assertion uses a query scoped by a specific holdingsId/itemId/instanceId, not by `cql.allRecords=1`. If any test uses `cql.allRecords=1` with a count assertion, add an ID scope using `id==(ID_1 OR ID_2 OR ...)` prefix.

### Step 6.3 – Remove setup matchers from `SortItemIT`

Same pattern. The `@BeforeAll` passes `holdingsMatcher(5)` and `itemsMatcher(21)`. Remove `@BeforeAll`/`@AfterAll`:

```java
@IntegrationTest
abstract class SortItemIT extends BaseIntegrationTest {
  // @BeforeAll and @AfterAll removed; all test methods unchanged
}
```

### Step 6.4 – Instance data note

Both `SearchInstanceFilterIT` and `SortItemIT` use the semantic-web instance, which is already loaded in Task 3. No additional loading lines needed in `setUpSharedTenant`.

### Step 6.5 – Compile and run

```bash
mvn -pl . -Dtest="SearchBrowseIT#SearchInstanceFilter*+SearchBrowseIT#SortItem*" test -q 2>&1 | tail -30
```

Expected: all tests pass.

### Step 6.6 – Commit

```bash
git add src/test/java/org/folio/api/search/SearchInstanceFilterIT.java \
        src/test/java/org/folio/api/search/SortItemIT.java
git commit -m "test: make SearchInstanceFilterIT and SortItemIT abstract; drop setup-time matchers

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 7: Make browse call-number and classification IT classes abstract

**Files:**
- Modify: `src/test/java/org/folio/api/browse/BrowseCallNumberIT.java`
- Modify: `src/test/java/org/folio/api/browse/BrowseClassificationIT.java`
- Modify: `src/test/java/org/folio/api/SearchBrowseIT.java`

Key points:
- Instance data must be loaded while sub-resource locks are held (outer `@BeforeAll` already does this).
- `@BeforeEach` methods that configure browse options (`updateLcConfig`, `updateSudocConfig`) **stay** inside the `*IT` class — they run before each test as intended.
- Browse test assertions use window queries (e.g., `browseByCallNumber("ABC >> 123", 5)`) scoped by value ranges — not affected by other test data as long as non-browse instances don't have call numbers in those ranges (verified by Appendix C invariant 1).

### Step 7.1 – Check `BrowseCallNumberIT` for extra Spring annotations

`BrowseCallNumberIT` may carry `@TestPropertySource(properties = "folio.search-config.indexing.instance-children-index-enabled=true")`. If it does, that same property is already on `SearchBrowseIT`, so it is redundant — **remove it from `BrowseCallNumberIT`** when making it abstract. Spring context key is the same.

### Step 7.2 – Make `BrowseCallNumberIT` abstract and remove lifecycle

```java
// BEFORE: class BrowseCallNumberIT extends BaseIntegrationTest
// AFTER:
@IntegrationTest
abstract class BrowseCallNumberIT extends BaseIntegrationTest {
  // @BeforeAll (lock acquisition, instance loading, unlock, wait) removed
  // @AfterAll removed
  // @BeforeEach updateLcConfig() and updateSudocConfig() KEPT unchanged
  // All @Test methods KEPT unchanged
  // All private static helper/data methods (instances(), etc.) KEPT unchanged
  // INSTANCES field: ensure it is package-accessible (not private):
  static final Instance[] INSTANCES = callNumberInstances();
}
```

Note: the `INSTANCES` field must be at least package-private (not `private`) so `setUpSharedTenant` in `SearchBrowseIT` can reference `BrowseCallNumberIT.INSTANCES`.

### Step 7.3 – Add `BrowseCallNumber` instance loading to `setUpSharedTenant`

In `SearchBrowseIT.setUpSharedTenant` inside the lock-held block:

```java
// BrowseCallNumber group (100 instances)
Arrays.stream(BrowseCallNumberIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i));
```

### Step 7.4 – Enable call-number sub-resource wait

After the unlock, uncomment:
```java
await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
    assertThat(countIndexDocument(ResourceType.INSTANCE_CALL_NUMBER, TENANT_ID))
        .isEqualTo(EXPECTED_CALL_NUMBER_COUNT)); // 100
```

### Step 7.5 – Make `BrowseClassificationIT` abstract

Same pattern as Steps 7.1–7.3. Find the expected classification count from `BrowseClassificationIT`'s original `await` assertion and confirm it matches `EXPECTED_CLASSIFICATION_COUNT = 19`. Add to `setUpSharedTenant`:
```java
// BrowseClassification group
Arrays.stream(BrowseClassificationIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i));
```
Uncomment classification wait.

### Step 7.6 – Compile and run

```bash
mvn -pl . -Dtest="SearchBrowseIT#BrowseCallNumber*+SearchBrowseIT#BrowseClassification*" test -q 2>&1 | tail -30
```

Expected: all tests pass.

### Step 7.7 – Commit

```bash
git add src/test/java/org/folio/api/browse/BrowseCallNumberIT.java \
        src/test/java/org/folio/api/browse/BrowseClassificationIT.java \
        src/test/java/org/folio/api/SearchBrowseIT.java
git commit -m "test: make BrowseCallNumber/Classification ITs abstract, wire into SearchBrowseIT

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 8: Make browse contributor and subject IT classes abstract

**Files:**
- Modify: `src/test/java/org/folio/api/browse/BrowseContributorIT.java`
- Modify: `src/test/java/org/folio/api/browse/BrowseSubjectIT.java`
- Modify: `src/test/java/org/folio/api/SearchBrowseIT.java`

### Step 8.1 – Make `BrowseContributorIT` abstract and expose data

```java
@IntegrationTest
abstract class BrowseContributorIT extends BaseIntegrationTest {
  // @BeforeAll removed (contained: lock, load instances, contributor mutation, unlock, wait)
  // @AfterAll removed
  // All @Test methods kept
  // Ensure INSTANCES is accessible:
  static final Instance[] INSTANCES = instances();
}
```

### Step 8.2 – Add contributor loading and mutation to `setUpSharedTenant`

The contributor deletion mutation must happen **while the sub-resource lock is still held**:

```java
// BrowseContributor group — inside lock-held block
Arrays.stream(BrowseContributorIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i));

// Contributor deletion: remove contributors from INSTANCES[0] so its contributor entry
// is deleted from the sub-resource index. Count expected = 13 (after deletion).
BrowseContributorIT.INSTANCES[0].setContributors(Collections.emptyList());
inventoryApi.updateInstance(TENANT_ID, BrowseContributorIT.INSTANCES[0]);
```

This block must appear before the `unlockSubResource` calls.

### Step 8.3 – Enable contributor sub-resource wait

```java
await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
    assertThat(countIndexDocument(ResourceType.INSTANCE_CONTRIBUTOR, TENANT_ID))
        .isEqualTo(EXPECTED_CONTRIBUTOR_COUNT)); // 13
```

### Step 8.4 – Make `BrowseSubjectIT` abstract

Same pattern. Add to `setUpSharedTenant`:
```java
// BrowseSubject group
Arrays.stream(BrowseSubjectIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i));
```
Uncomment subject wait:
```java
await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
    assertThat(countIndexDocument(ResourceType.INSTANCE_SUBJECT, TENANT_ID))
        .isEqualTo(EXPECTED_SUBJECT_COUNT)); // 28
```

### Step 8.5 – Compile and run

```bash
mvn -pl . -Dtest="SearchBrowseIT#BrowseContributor*+SearchBrowseIT#BrowseSubject*" test -q 2>&1 | tail -30
```

Expected: all tests pass.

### Step 8.6 – Commit

```bash
git add src/test/java/org/folio/api/browse/BrowseContributorIT.java \
        src/test/java/org/folio/api/browse/BrowseSubjectIT.java \
        src/test/java/org/folio/api/SearchBrowseIT.java
git commit -m "test: make BrowseContributor/Subject ITs abstract, wire into SearchBrowseIT

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 9: Make authority IT classes abstract — fix randomId and scope queries

**Files:**
- Modify: `src/test/java/org/folio/api/search/SearchAuthorityIT.java`
- Modify: `src/test/java/org/folio/api/search/SearchAuthorityFilterIT.java`
- Modify: `src/test/java/org/folio/api/search/SortAuthorityIT.java`
- Modify: `src/test/java/org/folio/api/browse/BrowseAuthorityIT.java`
- Modify: `src/test/java/org/folio/api/SearchBrowseIT.java`

### Step 9.1 – Understanding authority data loading

Authorities are sent to Kafka directly (not the REST inventory API):
```java
kafkaTemplate.send(inventoryAuthorityTopic(), rec.getId(),
    resourceEvent(null, AUTHORITY, rec));
```
In the shared setup, send **all authority records** to Kafka first (without per-group waiting), then wait once for `TOTAL_AUTHORITIES`.

### Step 9.2 – Fix `SearchAuthorityIT`: replace `randomId()` on linked instances

The 4 instances linked to the sample authority currently use `randomId()`. Replace with fixed constants and expose them:

```java
// In SearchAuthorityIT:
static final String LINKED_INSTANCE_ID_1 = "d0000001-auth-inst-0000-000000000001";
static final String LINKED_INSTANCE_ID_2 = "d0000002-auth-inst-0000-000000000002";
static final String LINKED_INSTANCE_ID_3 = "d0000003-auth-inst-0000-000000000003";
static final String LINKED_INSTANCE_ID_4 = "d0000004-auth-inst-0000-000000000004";

static final Instance[] LINKED_INSTANCES = linkedInstances();

private static Instance[] linkedInstances() {
  // Same logic as original SearchAuthorityIT.prepare() — but using fixed IDs above
  return new Instance[] {
    new Instance().id(LINKED_INSTANCE_ID_1)
      .subjects(List.of(new Subject().value(getAuthoritySampleHeading())))
      .contributors(emptyList()),
    // ... LINKED_INSTANCE_ID_2, _3, _4 with their original field values
  };
}
```

**Remove** `checkThatEventsFromKafkaAreIndexed(TENANT_ID, instanceSearchPath(), 4, emptyList())` from the original setup — in the shared setup, instance indexing is waited on with `TOTAL_INSTANCES`.

Make `SearchAuthorityIT` abstract and remove lifecycle:
```java
@IntegrationTest
abstract class SearchAuthorityIT extends BaseIntegrationTest {
  // @BeforeAll / @AfterAll removed; test methods unchanged
}
```

### Step 9.3 – Fix `SortAuthorityIT`: replace `randomId()` with fixed UUIDs and scope queries

```java
// In SortAuthorityIT:
static final String[] IDS = {
    "e0000001-sort-auth-0000-000000000001",
    "e0000002-sort-auth-0000-000000000002",
    "e0000003-sort-auth-0000-000000000003",
    "e0000004-sort-auth-0000-000000000004",
    "e0000005-sort-auth-0000-000000000005"
};

static final String SORT_AUTHORITY_ID_FILTER = "id==(" + String.join(" OR ", IDS) + ")";

static final Authority[] AUTHORITIES = authorities();
// authorities() factory unchanged except using IDS[i] instead of randomId()
```

Update every `doSearchByAuthorities(allRecordsSortedBy(field, order))` to use the ID filter:
```java
// Before:
doSearchByAuthorities(allRecordsSortedBy("headingRef", ASCENDING))
    .andExpect(jsonPath("totalRecords", is(RECORDS_COUNT)))

// After:
doSearchByAuthorities(SORT_AUTHORITY_ID_FILTER + " sortBy headingRef/sort.ascending")
    .andExpect(jsonPath("totalRecords", is(RECORDS_COUNT)))
```

Make `SortAuthorityIT` abstract and remove lifecycle.

### Step 9.4 – Scope `SearchAuthorityFilterIT` queries with ID filter

`SearchAuthorityFilterIT` has `RECORDS_COUNT = 15` with authorities already using fixed `IDS`. Add a `scoped()` helper and wrap all broad authority queries:

```java
// In SearchAuthorityFilterIT:
private static final String FILTER_AUTHORITY_ID_FILTER = "id==(" + String.join(" OR ", IDS) + ")";

private String scoped(String query) {
  return FILTER_AUTHORITY_ID_FILTER + " AND (" + query + ")";
}
```

Change every `doSearchByAuthorities(query)` / `doGet(recordFacetsPath(..., query, ...))` to `doSearchByAuthorities(scoped(query))` / `doGet(recordFacetsPath(..., scoped(query), ...))`.

Make `SearchAuthorityFilterIT` abstract and remove lifecycle.

### Step 9.5 – Make `BrowseAuthorityIT` abstract

`BrowseAuthorityIT` uses window/range queries — no count scoping needed. Make it abstract and remove lifecycle. Expose its data:

```java
@IntegrationTest
abstract class BrowseAuthorityIT extends BaseIntegrationTest {
  static final Authority[] AUTHORITIES = authorities(); // expose for setUpSharedTenant
  // @BeforeAll / @AfterAll removed; all @Test methods unchanged
}
```

### Step 9.6 – Add authority and instance loading to `setUpSharedTenant`

After the sub-resource unlock block:

```java
// Linked instances for SearchAuthority
Arrays.stream(SearchAuthorityIT.LINKED_INSTANCES)
    .forEach(i -> inventoryApi.createInstance(TENANT_ID, i));

// Authority records via Kafka
asList(SampleAuthorities.getAuthoritySampleAsMap()).forEach(rec ->
    kafkaTemplate.send(inventoryAuthorityTopic(), rec.get("id").toString(),
        resourceEvent(null, AUTHORITY, rec)));
Arrays.stream(SearchAuthorityFilterIT.AUTHORITIES)
    .forEach(a -> kafkaTemplate.send(inventoryAuthorityTopic(), a.getId(),
        resourceEvent(null, AUTHORITY, a)));
Arrays.stream(SortAuthorityIT.AUTHORITIES)
    .forEach(a -> kafkaTemplate.send(inventoryAuthorityTopic(), a.getId(),
        resourceEvent(null, AUTHORITY, a)));
Arrays.stream(BrowseAuthorityIT.AUTHORITIES)
    .forEach(a -> kafkaTemplate.send(inventoryAuthorityTopic(), a.getId(),
        resourceEvent(null, AUTHORITY, a)));
```

(Check `BaseIntegrationTest.setUpTenant(int, Authority...)` for the exact `resourceEvent` overload and Kafka send signature; replicate it here.)

### Step 9.7 – Enable authority wait in `setUpSharedTenant`

Compute `TOTAL_AUTHORITIES` = 51 (SearchAuthority sample) + 15 (SearchAuthorityFilter) + 5 (SortAuthority) + `BrowseAuthorityIT.AUTHORITIES.length` (verify actual value).

Uncomment:
```java
checkThatEventsFromKafkaAreIndexed(TENANT_ID, authoritySearchPath(), TOTAL_AUTHORITIES, emptyList());
```

### Step 9.8 – Compile and run

```bash
mvn -pl . -Dtest="SearchBrowseIT#SearchAuthority*+SearchBrowseIT#SearchAuthorityFilter*+SearchBrowseIT#SortAuthority*+SearchBrowseIT#BrowseAuthority*" test -q 2>&1 | tail -30
```

Expected: all tests pass.

### Step 9.9 – Commit

```bash
git add src/test/java/org/folio/api/search/SearchAuthorityIT.java \
        src/test/java/org/folio/api/search/SearchAuthorityFilterIT.java \
        src/test/java/org/folio/api/search/SortAuthorityIT.java \
        src/test/java/org/folio/api/browse/BrowseAuthorityIT.java \
        src/test/java/org/folio/api/SearchBrowseIT.java
git commit -m "test: make authority ITs abstract; fix randomId, scope authority queries

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 10: Make linked-data IT classes abstract

**Files:**
- Modify: `src/test/java/org/folio/api/search/SearchLinkedDataInstanceIT.java`
- Modify: `src/test/java/org/folio/api/search/SearchLinkedDataWorkIT.java`
- Modify: `src/test/java/org/folio/api/search/SearchLinkedDataHubIT.java`
- Modify: `src/test/java/org/folio/api/SearchBrowseIT.java`

### Step 10.1 – Understand linked-data data loading

These classes call `setUpTenant(LinkedDataInstance.class, getInstanceSampleAsMap(), ...)` which sends records via a different code path from regular instances — the linked-data Kafka topic and its own index. Their tests query `linkedDataInstanceSearchPath()` / `linkedDataWorkSearchPath()` / `linkedDataHubSearchPath()` — a separate index from regular instances, so **no interference** with shared data. No assertion scoping needed.

### Step 10.2 – Make each class abstract and remove lifecycle

For all three files, apply the same pattern:

```java
@IntegrationTest
abstract class SearchLinkedDataInstanceIT extends BaseIntegrationTest {
  // @BeforeAll / @AfterAll removed; all @Test methods unchanged
}
```

### Step 10.3 – Add linked-data record loading to `setUpSharedTenant`

After the sub-resource unlock block, add (check `BaseIntegrationTest.setUpTenant(Class<?>, Map...)` for the exact `saveRecords` signature):

```java
// Linked-data instances
saveRecords(TENANT_ID, linkedDataInstanceSearchPath(),
    asList(SampleLinkedData.getInstanceSampleAsMap(), SampleLinkedData.getInstance2SampleAsMap()),
    2, emptyList(),
    rec -> kafkaTemplate.send(linkedDataInstanceTopic(), rec.get("id").toString(),
        resourceEvent(null, LINKED_DATA_INSTANCE, rec)));

// Linked-data works
saveRecords(TENANT_ID, linkedDataWorkSearchPath(),
    asList(SampleLinkedData.getWorkSampleAsMap(), SampleLinkedData.getWork2SampleAsMap()),
    2, emptyList(),
    rec -> kafkaTemplate.send(linkedDataWorkTopic(), rec.get("id").toString(),
        resourceEvent(null, LINKED_DATA_WORK, rec)));

// Linked-data hubs
saveRecords(TENANT_ID, linkedDataHubSearchPath(),
    asList(SampleLinkedData.getHubSampleAsMap()),
    1, emptyList(),
    rec -> kafkaTemplate.send(linkedDataHubTopic(), rec.get("id").toString(),
        resourceEvent(null, LINKED_DATA_HUB, rec)));
```

Verify the exact method names (`SampleLinkedData`, `linkedDataInstanceTopic()`, resource type constants) by checking the original `@BeforeAll` in each `*IT` file before removing them.

### Step 10.4 – Compile and run

```bash
mvn -pl . -Dtest="SearchBrowseIT#SearchLinkedDataInstance*+SearchBrowseIT#SearchLinkedDataWork*+SearchBrowseIT#SearchLinkedDataHub*" test -q 2>&1 | tail -30
```

Expected: all tests pass.

### Step 10.5 – Commit

```bash
git add src/test/java/org/folio/api/search/SearchLinkedDataInstanceIT.java \
        src/test/java/org/folio/api/search/SearchLinkedDataWorkIT.java \
        src/test/java/org/folio/api/search/SearchLinkedDataHubIT.java \
        src/test/java/org/folio/api/SearchBrowseIT.java
git commit -m "test: make linked-data ITs abstract, wire into SearchBrowseIT

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 11: Finalize counts, activate waits, run full suite

### Step 11.1 – Compute and fill in `TOTAL_INSTANCES`

Sum the `.length` of each `INSTANCES` array:

```java
private static final int TOTAL_INSTANCES =
    1                                              // semantic-web
    + SortInstanceIT.INSTANCES.length             // 5
    + SortInstanceByTitleIT.INSTANCES.length      // 13
    + SearchByEmptyValuesIT.INSTANCES.length      // 2
    // SearchInstanceFilter and SortItem use semantic-web: 0 extra
    + BrowseCallNumberIT.INSTANCES.length         // 100
    + BrowseClassificationIT.INSTANCES.length     // verify
    + BrowseContributorIT.INSTANCES.length        // verify
    + BrowseSubjectIT.INSTANCES.length            // verify
    + SearchAuthorityIT.LINKED_INSTANCES.length;  // 4
```

If unsure of a count, temporarily set `TOTAL_INSTANCES` to a large value (e.g., `9999`), run the test, and look for the logged actual count from `checkThatEventsFromKafkaAreIndexed`.

### Step 11.2 – Activate all waits in `setUpSharedTenant`

Uncomment the two `checkThatEventsFromKafkaAreIndexed` calls and all four sub-resource awaits. Remove placeholder comments.

### Step 11.3 – Run the full IT suite

```bash
mvn -pl . test -q 2>&1 | tail -50
```

Expected: `BUILD SUCCESS`. If any nested test fails:

**Common failure — wrong sub-resource count:** `EXPECTED_CALL_NUMBER_COUNT` etc. are off because some other test instances have call-numbers/contributors/subjects/classifications. Check Appendix C invariant 1. Fix by removing those fields from the data constants in the offending `*IT` file.

**Common failure — `totalRecords` assertion mismatch:** A test still uses `cql.allRecords=1` without an ID scope. Find it:
```bash
grep -rn "cql.allRecords=1" src/test/java/org/folio/api/search/ src/test/java/org/folio/api/browse/ | grep -v "SORT_.*_ID_FILTER\|ID_FILTER\|scoped\|abstract class"
```
Apply the same `id==(...)` scoping technique from Tasks 4, 5, 9.

**Common failure — `totalRecords is 0` or setup timeout:** An instance or authority loading line is missing from `setUpSharedTenant`. Confirm every data group appears.

**Common failure — context created twice for one nested class:** A `*IT` class still has `@TestPropertySource` or `@SpringBootTest(properties=...)` with different values from `SearchBrowseIT`. Remove those annotations from the abstract class.

### Step 11.4 – Commit

```bash
git add src/test/java/org/folio/api/SearchBrowseIT.java
git commit -m "test: activate shared-tenant waits; consolidation of 20 ITs into SearchBrowseIT complete

All 20 non-consortium search/browse IT classes are now abstract base classes
wired via @Nested stubs in SearchBrowseIT. Shared tenant setup/teardown runs
once; Spring context is reused across all ITs.

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Appendix A: Key method signatures in `BaseIntegrationTest`

```java
// Enable tenant (creates Folio tenant, waits for Kafka readiness)
protected static void enableTenant(String tenantId) { ... }  // line 502

// Enable a feature flag
protected static void enableFeature(TenantConfiguredFeature feature) { ... } // line 487

// Send records and wait until indexed count == expectedCount
protected static <T> void saveRecords(String tenantId, String path,
    Collection<T> rawRecords, int expectedCount,
    ThrowingConsumer<T> consumer) { ... } // line 474

// Poll until totalRecords == size on cql.allRecords=1
protected static void checkThatEventsFromKafkaAreIndexed(
    String tenantId, String path, int size,
    Collection<ResultMatcher> matchers) { ... } // line 527

// Count documents in a sub-resource index
protected static long countIndexDocument(ResourceType resourceType, String tenantId) { ... }

// Remove tenant (clean up after tests)
protected static void removeTenant(String tenantId) { ... }
```

## Appendix B: Sub-resource lock API

```java
// Lock: returns the lock timestamp (empty if lock could not be acquired)
Optional<OffsetDateTime> timestamp = subResourcesLockRepository
    .lockSubResource(ReindexEntityType.CALL_NUMBER, TENANT_ID);

// Unlock: pass back the timestamp from the lock call
subResourcesLockRepository
    .unlockSubResource(ReindexEntityType.CALL_NUMBER, timestamp, TENANT_ID);
```

`ReindexEntityType` values relevant here: `CALL_NUMBER`, `CONTRIBUTOR`, `CLASSIFICATION`, `SUBJECT`.

## Appendix C: Non-interference invariants to verify

The shared tenant works correctly only if the following are true:
1. Instances from `SortInstance`, `SortInstanceByTitle`, `SearchByEmptyValues`, `SearchInstanceFilter`, and `SortItem` groups do **not** have call-numbers, contributors, subjects, or classifications set — otherwise `EXPECTED_*_COUNT` constants need adjustment.
2. Instances from browse groups do **not** produce titles that conflict with `SearchByEmptyValues.INSTANCE_ID_1.title = "title1"` or `INSTANCE_ID_2.title = "title2"` in CQL title queries.
3. Authorities from different groups do **not** share the same `headingRef` values used by `SortAuthority` ("111", "aaa", "ccc", "ŚŚŚ", "zzz") — otherwise sort position assertions will be wrong.

Verify item 1 by inspecting the `instances()` factory methods. Verify items 2 and 3 by running the tests and observing failures.
