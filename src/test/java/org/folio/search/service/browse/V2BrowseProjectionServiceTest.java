package org.folio.search.service.browse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.IndexRepository;
import org.folio.search.repository.V2BrowseProjectionRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class V2BrowseProjectionServiceTest {

  private static final String MAIN_INDEX = "folio_instance_search_diku_1";
  private static final String BROWSE_INDEX = "folio_v2_contributor_diku_1";

  @Mock
  private V2BrowseProjectionRepository repository;
  @Mock
  private IndexRepository indexRepository;
  @Mock
  private Executor browseUpsertExecutor;

  @InjectMocks
  private V2BrowseProjectionService service;

  @Test
  void rebuildContributors_skipsWhenEmpty() {
    service.rebuildContributors(Set.of(), MAIN_INDEX, BROWSE_INDEX);

    verify(repository, never()).streamByNestedBrowseIds(anyString(), anyString(), anySet(), any(), any(String[].class));
  }

  @Test
  void rebuildContributors_upsertsWhenHitsFound() {
    var browseId = "contributor-1";
    var hits = List.<Map<String, Object>>of(
      Map.of("tenantId", "diku", "shared", false,
        "instance", Map.of(
          "contributors", List.of(
            Map.of("browseId", browseId, "name", "Smith, John",
              "contributorNameTypeId", "type-1", "contributorTypeId", "ctype-1"))))
    );

    streamNestedHits(hits);

    service.rebuildContributors(Set.of(browseId), MAIN_INDEX, BROWSE_INDEX);

    verify(repository).bulkUpsert(eq(BROWSE_INDEX),
      argThat((Collection<Map<String, Object>> docs) ->
        docs.size() == 1 && docs.iterator().next().get("id").equals(browseId)));
    verify(repository).bulkDelete(eq(BROWSE_INDEX), eq(Set.of()));
  }

  @Test
  void rebuildContributors_deletesWhenNoHitsForId() {
    var browseId = "contributor-orphaned";

    streamNestedHits(List.of());

    service.rebuildContributors(Set.of(browseId), MAIN_INDEX, BROWSE_INDEX);

    verify(repository).bulkUpsert(eq(BROWSE_INDEX), argThat(Collection::isEmpty));
    verify(repository).bulkDelete(eq(BROWSE_INDEX), eq(Set.of(browseId)));
  }

  @Test
  void rebuildSubjects_skipsWhenEmpty() {
    service.rebuildSubjects(Set.of(), MAIN_INDEX, BROWSE_INDEX);

    verify(repository, never()).streamByNestedBrowseIds(anyString(), anyString(), anySet(), any(), any(String[].class));
  }

  @Test
  void rebuildSubjects_upsertsWhenHitsFound() {
    var browseId = "subject-1";
    var hits = List.<Map<String, Object>>of(
      Map.of("tenantId", "diku", "shared", false,
        "instance", Map.of(
          "subjects", List.of(
            Map.of("browseId", browseId, "value", "Library science"))))
    );

    streamNestedHits(hits);

    service.rebuildSubjects(Set.of(browseId), MAIN_INDEX, BROWSE_INDEX);

    verify(repository).bulkUpsert(eq(BROWSE_INDEX),
      argThat((Collection<Map<String, Object>> docs) ->
        docs.size() == 1 && docs.iterator().next().get("id").equals(browseId)));
  }

  @Test
  void rebuildSubjects_aggregatesMultipleHitsForSameBrowseId() {
    var browseId = "subject-1";
    var hits = List.<Map<String, Object>>of(
      Map.of("tenantId", "diku", "shared", false,
        "instance", Map.of(
          "subjects", List.of(Map.of("browseId", browseId, "value", "Library science")))),
      Map.of("tenantId", "diku", "shared", false,
        "instance", Map.of(
          "subjects", List.of(Map.of("browseId", browseId, "value", "Library science"))))
    );

    streamNestedHits(hits);

    service.rebuildSubjects(Set.of(browseId), MAIN_INDEX, BROWSE_INDEX);

    verify(repository).bulkUpsert(eq(BROWSE_INDEX),
      argThat((Collection<Map<String, Object>> docs) -> {
        if (docs.size() != 1) {
          return false;
        }
        var doc = docs.iterator().next();
        @SuppressWarnings("unchecked")
        var instances = (List<Map<String, Object>>) doc.get("instances");
        return instances.size() == 1
          && ((Number) instances.getFirst().get("count")).intValue() == 2;
      }));
  }

  @Test
  void rebuildClassifications_skipsWhenEmpty() {
    service.rebuildClassifications(Set.of(), MAIN_INDEX, BROWSE_INDEX);

    verify(repository, never()).streamByNestedBrowseIds(anyString(), anyString(), anySet(), any(), any(String[].class));
  }

  @Test
  void rebuildClassifications_upsertsWhenHitsFound() {
    var browseId = "class-1";
    var hits = List.<Map<String, Object>>of(
      Map.of("tenantId", "diku", "shared", false,
        "instance", Map.of(
          "classifications", List.of(
            Map.of("browseId", browseId, "classificationNumber", "QA76",
              "classificationTypeId", "lcc"))))
    );

    streamNestedHits(hits);

    service.rebuildClassifications(Set.of(browseId), MAIN_INDEX, BROWSE_INDEX);

    verify(repository).bulkUpsert(eq(BROWSE_INDEX),
      argThat((Collection<Map<String, Object>> docs) ->
        docs.size() == 1 && docs.iterator().next().get("id").equals(browseId)));
  }

  @Test
  void rebuildClassifications_aggregatesMultipleHitsForSameBrowseId() {
    var browseId = "class-1";
    var hits = List.<Map<String, Object>>of(
      Map.of("tenantId", "diku", "shared", false,
        "instance", Map.of(
          "classifications", List.of(
            Map.of("browseId", browseId, "classificationNumber", "QA76", "classificationTypeId", "lcc")))),
      Map.of("tenantId", "diku", "shared", false,
        "instance", Map.of(
          "classifications", List.of(
            Map.of("browseId", browseId, "classificationNumber", "QA76", "classificationTypeId", "lcc"))))
    );

    streamNestedHits(hits);

    service.rebuildClassifications(Set.of(browseId), MAIN_INDEX, BROWSE_INDEX);

    verify(repository).bulkUpsert(eq(BROWSE_INDEX),
      argThat((Collection<Map<String, Object>> docs) -> {
        if (docs.size() != 1) {
          return false;
        }
        var doc = docs.iterator().next();
        @SuppressWarnings("unchecked")
        var instances = (List<Map<String, Object>>) doc.get("instances");
        return instances.size() == 1
          && ((Number) instances.getFirst().get("count")).intValue() == 2;
      }));
  }

  @Test
  void rebuildCallNumbers_skipsWhenEmpty() {
    service.rebuildCallNumbers(Set.of(), MAIN_INDEX, BROWSE_INDEX);

    verify(repository, never()).streamItemsByCallNumberBrowseIds(anyString(), anySet(), any(), any(String[].class));
  }

  @Test
  void rebuildCallNumbers_upsertsWhenHitsFound() {
    var browseId = "cn-1";
    var hits = List.<Map<String, Object>>of(
      Map.of("tenantId", "diku", "shared", false, "instanceId", "inst-1",
        "item", Map.of(
          "itemCallNumberBrowseId", browseId,
          "effectiveLocationId", "loc-1",
          "effectiveCallNumberComponents", Map.of(
            "callNumber", "QA76.9.A25",
            "prefix", "FOLIO",
            "suffix", "v.1",
            "typeId", "type-1")))
    );

    doAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      var consumer = (Consumer<List<Map<String, Object>>>) invocation.getArgument(2);
      consumer.accept(hits);
      return null;
    }).when(repository).streamItemsByCallNumberBrowseIds(anyString(), anySet(), any(), any(String[].class));

    service.rebuildCallNumbers(Set.of(browseId), MAIN_INDEX, BROWSE_INDEX);

    verify(repository).bulkUpsert(eq(BROWSE_INDEX),
      argThat((Collection<Map<String, Object>> docs) ->
        docs.size() == 1 && docs.iterator().next().get("id").equals(browseId)));
  }

  @Test
  void rebuildCallNumbers_mergesSameBucketAndDeduplicatesInstanceIds() {
    var browseId = "cn-1";
    var hits = List.<Map<String, Object>>of(
      Map.of("tenantId", "diku", "shared", false, "instanceId", "inst-1",
        "item", Map.of(
          "itemCallNumberBrowseId", browseId,
          "effectiveLocationId", "loc-1",
          "effectiveCallNumberComponents", Map.of("callNumber", "QA76.9.A25"))),
      Map.of("tenantId", "diku", "shared", false, "instanceId", "inst-2",
        "item", Map.of(
          "itemCallNumberBrowseId", browseId,
          "effectiveLocationId", "loc-1",
          "effectiveCallNumberComponents", Map.of("callNumber", "QA76.9.A25"))),
      Map.of("tenantId", "diku", "shared", false, "instanceId", "inst-1",
        "item", Map.of(
          "itemCallNumberBrowseId", browseId,
          "effectiveLocationId", "loc-1",
          "effectiveCallNumberComponents", Map.of("callNumber", "QA76.9.A25")))
    );

    doAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      var consumer = (Consumer<List<Map<String, Object>>>) invocation.getArgument(2);
      consumer.accept(hits);
      return null;
    }).when(repository).streamItemsByCallNumberBrowseIds(anyString(), anySet(), any(), any(String[].class));

    service.rebuildCallNumbers(Set.of(browseId), MAIN_INDEX, BROWSE_INDEX);

    verify(repository).bulkUpsert(eq(BROWSE_INDEX),
      argThat((Collection<Map<String, Object>> docs) -> {
        if (docs.size() != 1) {
          return false;
        }
        var doc = docs.iterator().next();
        @SuppressWarnings("unchecked")
        var instances = (List<Map<String, Object>>) doc.get("instances");
        if (instances.size() != 1) {
          return false;
        }
        var instance = instances.getFirst();
        @SuppressWarnings("unchecked")
        var instanceIds = (Set<String>) instance.get("instanceId");
        return Set.of("inst-1", "inst-2").equals(instanceIds);
      }));
  }

  @Test
  void rebuildContributors_aggregatesMultipleHitsForSameBrowseId() {
    var browseId = "contributor-1";
    var hits = List.<Map<String, Object>>of(
      Map.of("tenantId", "diku", "shared", false,
        "instance", Map.of(
          "contributors", List.of(
            Map.of("browseId", browseId, "name", "Smith, John",
              "contributorNameTypeId", "type-1")))),
      Map.of("tenantId", "diku", "shared", false,
        "instance", Map.of(
          "contributors", List.of(
            Map.of("browseId", browseId, "name", "Smith, John",
              "contributorNameTypeId", "type-1"))))
    );

    streamNestedHits(hits);

    service.rebuildContributors(Set.of(browseId), MAIN_INDEX, BROWSE_INDEX);

    verify(repository).bulkUpsert(eq(BROWSE_INDEX),
      argThat((Collection<Map<String, Object>> docs) -> {
        if (docs.size() != 1) {
          return false;
        }
        var doc = docs.iterator().next();
        @SuppressWarnings("unchecked")
        var instances = (List<Map<String, Object>>) doc.get("instances");
        // Two hits from same tenant → count should be 2
        return instances.size() == 1
          && ((Number) instances.getFirst().get("count")).intValue() == 2;
      }));
  }

  @Test
  void rebuildContributors_mergesContributorTypeIdsPerTenantBucket() {
    var browseId = "contributor-1";
    var hits = List.<Map<String, Object>>of(
      Map.of("tenantId", "diku", "shared", false,
        "instance", Map.of(
          "contributors", List.of(
            Map.of("browseId", browseId, "name", "Smith, John",
              "contributorNameTypeId", "type-1", "contributorTypeId", "ctype-1"),
            Map.of("browseId", browseId, "name", "Smith, John",
              "contributorNameTypeId", "type-1", "contributorTypeId", "ctype-2"))))
    );

    streamNestedHits(hits);

    service.rebuildContributors(Set.of(browseId), MAIN_INDEX, BROWSE_INDEX);

    verify(repository).bulkUpsert(eq(BROWSE_INDEX),
      argThat((Collection<Map<String, Object>> docs) -> {
        if (docs.size() != 1) {
          return false;
        }
        var doc = docs.iterator().next();
        @SuppressWarnings("unchecked")
        var instances = (List<Map<String, Object>>) doc.get("instances");
        if (instances.size() != 1) {
          return false;
        }
        var instance = instances.getFirst();
        @SuppressWarnings("unchecked")
        var typeIds = (Set<String>) instance.get("typeId");
        return ((Number) instance.get("count")).intValue() == 2
          && Set.of("ctype-1", "ctype-2").equals(typeIds);
      }));
  }

  @Test
  void rebuildFull_aggregatesInstanceBrowseDocsAcrossMultiplePages() {
    runExecutorTasksSynchronously();
    var contributorId = "contributor-1";
    var subjectId = "subject-1";
    var classificationId = "classification-1";
    var firstPage = List.<Map<String, Object>>of(
      Map.of("tenantId", "diku", "shared", false,
        "instance", Map.of(
          "contributors", List.of(
            Map.of("browseId", contributorId, "name", "Smith, John",
              "contributorNameTypeId", "type-1", "contributorTypeId", "ctype-1")),
          "subjects", List.of(Map.of("browseId", subjectId, "value", "Library science")),
          "classifications", List.of(
            Map.of("browseId", classificationId, "classificationNumber", "QA76",
              "classificationTypeId", "lcc")))));
    var secondPage = List.<Map<String, Object>>of(
      Map.of("tenantId", "diku", "shared", false,
        "instance", Map.of(
          "contributors", List.of(
            Map.of("browseId", contributorId, "name", "Smith, John",
              "contributorNameTypeId", "type-1", "contributorTypeId", "ctype-2")),
          "subjects", List.of(Map.of("browseId", subjectId, "value", "Library science")),
          "classifications", List.of(
            Map.of("browseId", classificationId, "classificationNumber", "QA76",
              "classificationTypeId", "lcc")))));

    doAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      var consumer = (Consumer<List<Map<String, Object>>>) invocation.getArgument(1);
      consumer.accept(firstPage);
      consumer.accept(secondPage);
      return null;
    }).when(repository).streamAllInstanceBrowseSourceDocs(anyString(), any(), any(String[].class));
    doAnswer(invocation -> null)
      .when(repository).streamAllItemBrowseSourceDocs(anyString(), any(), any(String[].class));

    service.rebuildFull(MAIN_INDEX, browseIndexMap());

    verify(indexRepository).refreshIndices(MAIN_INDEX);
    verify(repository).bulkUpsert(eq("browse-contributor"),
      argThat((Collection<Map<String, Object>> docs) -> {
        if (docs.size() != 1) {
          return false;
        }
        var doc = docs.iterator().next();
        @SuppressWarnings("unchecked")
        var instances = (List<Map<String, Object>>) doc.get("instances");
        if (instances.size() != 1) {
          return false;
        }
        var instance = instances.getFirst();
        @SuppressWarnings("unchecked")
        var typeIds = (Set<String>) instance.get("typeId");
        return doc.get("id").equals(contributorId)
          && ((Number) instance.get("count")).intValue() == 2
          && Set.of("ctype-1", "ctype-2").equals(typeIds);
      }));
    verify(repository).bulkUpsert(eq("browse-subject"),
      argThat((Collection<Map<String, Object>> docs) -> {
        if (docs.size() != 1) {
          return false;
        }
        var doc = docs.iterator().next();
        @SuppressWarnings("unchecked")
        var instances = (List<Map<String, Object>>) doc.get("instances");
        return doc.get("id").equals(subjectId)
          && instances.size() == 1
          && ((Number) instances.getFirst().get("count")).intValue() == 2;
      }));
    verify(repository).bulkUpsert(eq("browse-classification"),
      argThat((Collection<Map<String, Object>> docs) -> {
        if (docs.size() != 1) {
          return false;
        }
        var doc = docs.iterator().next();
        @SuppressWarnings("unchecked")
        var instances = (List<Map<String, Object>>) doc.get("instances");
        return doc.get("id").equals(classificationId)
          && instances.size() == 1
          && ((Number) instances.getFirst().get("count")).intValue() == 2;
      }));
    verify(repository, never()).streamByNestedBrowseIds(anyString(), anyString(), anySet(), any(), any(String[].class));
  }

  @Test
  void rebuildFull_aggregatesCallNumberDocsAcrossMultiplePages() {
    runExecutorTasksSynchronously();
    var browseId = "cn-1";
    var firstPage = List.<Map<String, Object>>of(
      Map.of("tenantId", "diku", "shared", false, "instanceId", "inst-1",
        "item", Map.of(
          "itemCallNumberBrowseId", browseId,
          "effectiveLocationId", "loc-1",
          "effectiveCallNumberComponents", Map.of("callNumber", "QA76.9.A25"))));
    var secondPage = List.<Map<String, Object>>of(
      Map.of("tenantId", "diku", "shared", false, "instanceId", "inst-2",
        "item", Map.of(
          "itemCallNumberBrowseId", browseId,
          "effectiveLocationId", "loc-1",
          "effectiveCallNumberComponents", Map.of("callNumber", "QA76.9.A25"))),
      Map.of("tenantId", "diku", "shared", false, "instanceId", "inst-1",
        "item", Map.of(
          "itemCallNumberBrowseId", browseId,
          "effectiveLocationId", "loc-1",
          "effectiveCallNumberComponents", Map.of("callNumber", "QA76.9.A25"))));

    doAnswer(invocation -> null)
      .when(repository).streamAllInstanceBrowseSourceDocs(anyString(), any(), any(String[].class));
    doAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      var consumer = (Consumer<List<Map<String, Object>>>) invocation.getArgument(1);
      consumer.accept(firstPage);
      consumer.accept(secondPage);
      return null;
    }).when(repository).streamAllItemBrowseSourceDocs(anyString(), any(), any(String[].class));

    service.rebuildFull(MAIN_INDEX, browseIndexMap());

    verify(repository).bulkUpsert(eq("browse-call-number"),
      argThat((Collection<Map<String, Object>> docs) -> {
        if (docs.size() != 1) {
          return false;
        }
        var doc = docs.iterator().next();
        @SuppressWarnings("unchecked")
        var instances = (List<Map<String, Object>>) doc.get("instances");
        if (instances.size() != 1) {
          return false;
        }
        var instance = instances.getFirst();
        @SuppressWarnings("unchecked")
        var instanceIds = (Set<String>) instance.get("instanceId");
        return doc.get("id").equals(browseId) && Set.of("inst-1", "inst-2").equals(instanceIds);
      }));
    verify(repository, never()).streamItemsByCallNumberBrowseIds(anyString(), anySet(), any(), any(String[].class));
  }

  private void streamNestedHits(List<Map<String, Object>> hits) {
    doAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      var consumer = (Consumer<List<Map<String, Object>>>) invocation.getArgument(3);
      consumer.accept(hits);
      return null;
    }).when(repository).streamByNestedBrowseIds(anyString(), anyString(), anySet(), any(), any(String[].class));
  }

  /**
   * Configures the {@link #browseUpsertExecutor} mock to run submitted tasks synchronously
   * on the calling thread, so that {@code CompletableFuture.runAsync(..., executor).join()}
   * inside {@link V2BrowseProjectionService#rebuildFull} actually completes instead of blocking.
   */
  private void runExecutorTasksSynchronously() {
    doAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    }).when(browseUpsertExecutor).execute(any(Runnable.class));
  }

  private Map<ResourceType, String> browseIndexMap() {
    return Map.of(
      ResourceType.V2_CONTRIBUTOR, "browse-contributor",
      ResourceType.V2_SUBJECT, "browse-subject",
      ResourceType.V2_CLASSIFICATION, "browse-classification",
      ResourceType.V2_CALL_NUMBER, "browse-call-number"
    );
  }
}
