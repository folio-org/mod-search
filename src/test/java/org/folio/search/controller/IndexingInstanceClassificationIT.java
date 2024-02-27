package org.folio.search.controller;

import static org.assertj.core.api.AssertionsForClassTypes.entry;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.folio.search.model.client.CqlQuery.exactMatchAny;
import static org.folio.search.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceClassificationsInner;
import org.folio.search.model.client.CqlQueryParam;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.SearchUtils;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class IndexingInstanceClassificationIT extends BaseIntegrationTest {

  @Autowired
  private RestHighLevelClient restHighLevelClient;

  @BeforeAll
  static void prepare() {
    setUpTenant(Instance.class);
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }


  @Test
  void shouldIndexInstanceClassification_createNewDocument() {
    var instanceId1 = randomId();
    var instanceId2 = randomId();
    var lcTypeId = "ce176ace-a53e-4b4d-aa89-725ed7b2edac";
    var number = "N123";
    var classification = new InstanceClassificationsInner().classificationNumber(number).classificationTypeId(lcTypeId);
    var instance1 = new Instance().id(instanceId1).addClassificationsItem(classification);
    var instance2 = new Instance().id(instanceId2).addClassificationsItem(classification);
    inventoryApi.createInstance(TENANT_ID, instance1);
    inventoryApi.createInstance(TENANT_ID, instance2);
    assertCountByIds(instanceSearchPath(), List.of(instanceId1, instanceId2), 2);
    await(() -> assertThat(fetchAllInstanceClassifications(TENANT_ID).getHits().getHits()).hasSize(1));

    var hits = fetchAllInstanceClassifications(TENANT_ID).getHits().getHits();
    var sourceAsMap = hits[0].getSourceAsMap();
    assertThat(sourceAsMap)
      .contains(
        entry("number", number),
        entry("typeId", lcTypeId),
        entry("defaultShelvingOrder", "N123"),
        entry("deweyShelvingOrder", "N 3123"),
        entry("lcShelvingOrder", "N 3123")
      );

    @SuppressWarnings("unchecked")
    var instances = (List<Map<String, Object>>) sourceAsMap.get("instances");
    assertThat(instances)
      .allSatisfy(map -> assertThat(map).containsEntry("shared", false))
      .allSatisfy(map -> assertThat(map).containsEntry("tenantId", TENANT_ID))
      .anySatisfy(map -> assertThat(map).containsEntry("instanceId", instanceId1))
      .anySatisfy(map -> assertThat(map).containsEntry("instanceId", instanceId2));
  }

  @Test
  void shouldIndexInstanceClassification_deleteDocument() {
    var instanceId = randomId();
    var classification = new InstanceClassificationsInner().classificationNumber("N123").classificationTypeId("type1");
    var instance = new Instance().id(instanceId).addClassificationsItem(classification);
    inventoryApi.createInstance(TENANT_ID, instance);
    assertCountByIds(instanceSearchPath(), List.of(instanceId), 1);
    await(() -> assertThat(fetchAllInstanceClassifications(TENANT_ID).getHits().getHits()).hasSize(1));
    inventoryApi.updateInstance(TENANT_ID, instance.classifications(null));
    await(() -> assertThat(fetchAllInstanceClassifications(TENANT_ID).getHits().getHits()).isEmpty());
  }

  private static void assertCountByIds(String path, List<String> ids, int expected) {
    var query = exactMatchAny(CqlQueryParam.ID, ids).toString();
    await(() -> doSearch(path, query).andExpect(jsonPath("$.totalRecords", is(expected))));
  }

  private static void await(ThrowingRunnable runnable) {
    Awaitility.await()
      .atMost(ONE_MINUTE)
      .pollInterval(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(runnable);
  }

  @SneakyThrows
  private SearchResponse fetchAllInstanceClassifications(String tenantId) {
    var searchRequest = new SearchRequest()
      .source(searchSource().query(matchAllQuery()))
      .indices(getIndexName(SearchUtils.INSTANCE_CLASSIFICATION_RESOURCE, tenantId));
    return restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
  }
}
