package org.folio.search.controller;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TWO_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TWO_MINUTES;
import static org.folio.search.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.search.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.FOLIO_CN_TYPE;
import static org.folio.search.utils.TestConstants.LOCAL_CN_TYPE;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.folio.search.utils.TestUtils.array;
import static org.folio.search.utils.TestUtils.facet;
import static org.folio.search.utils.TestUtils.facetItem;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.assertj.core.util.Lists;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.domain.dto.RecordType;
import org.folio.search.support.base.BaseConsortiumIntegrationTest;
import org.folio.search.utils.SearchUtils;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class BrowseCallNumberConsortiumIT extends BaseConsortiumIntegrationTest {


  private static final String[] LOCATIONS = array("location 1", "location 2");
  private static final Instance[] INSTANCES_CENTRAL = instancesCentral();
  private static final Instance[] INSTANCES_MEMBER = instancesMember();


  @BeforeAll
  static void prepare(@Autowired RestHighLevelClient restHighLevelClient) throws InterruptedException {
    setUpTenant(CENTRAL_TENANT_ID, INSTANCES_CENTRAL);
    setUpTenant(MEMBER_TENANT_ID);
    saveRecords(MEMBER_TENANT_ID, instanceSearchPath(), asList(INSTANCES_MEMBER),
      4,
      instance -> inventoryApi.createInstance(MEMBER_TENANT_ID, instance));

    await().atMost(TWO_MINUTES).pollInterval(TWO_HUNDRED_MILLISECONDS).untilAsserted(() -> {
      var searchRequest = new SearchRequest()
        .source(searchSource().query(matchAllQuery()).trackTotalHits(true).from(0))
        .indices(getIndexName(SearchUtils.INSTANCE_RESOURCE, CENTRAL_TENANT_ID));
      var searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
      assertThat(searchResponse.getHits().getTotalHits().value).isEqualTo(4);
    });
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForInstances_parameterized")
  void getFacetsForInstances_positive(String tenantId, String query, String[] facets, Map<String, Facet> expected) {
    var actual = parseResponse(
      doGet(recordFacetsPath(RecordType.INSTANCES, query, facets), tenantId), FacetResult.class);

    expected.forEach((facetName, expectedFacet) -> {
      var actualFacet = actual.getFacets().get(facetName);

      assertThat(actualFacet).isNotNull();
      assertThat(actualFacet.getValues())
        .containsExactlyInAnyOrderElementsOf(expectedFacet.getValues());
    });
  }

  private static Stream<Arguments> facetQueriesProvider() {
    return Stream.of(
      arguments(CENTRAL_TENANT_ID, "cql.allRecords=1", array("item.effectiveLocationId"), mapOf(
        "items.effectiveLocationId", facet(facetItem(LOCATIONS[0], 1), facetItem(LOCATIONS[1], 1)))),
      arguments(CENTRAL_TENANT_ID, "cql.allRecords=1", array("holdings.tenantId"), mapOf(
        "holdings.tenantId", facet(facetItem(CENTRAL_TENANT_ID, 1), facetItem(MEMBER_TENANT_ID, 1)))),
      arguments(CENTRAL_TENANT_ID, "callNumberType=\"local\"", array("item.effectiveLocationId"), mapOf(
        "items.effectiveLocationId", facet(facetItem(LOCATIONS[0], 1), facetItem(LOCATIONS[1], 1)))),
      arguments(CENTRAL_TENANT_ID, "callNumberType=\"local\"", array("holdings.tenantId"), mapOf(
        "holdings.tenantId", facet(facetItem(CENTRAL_TENANT_ID, 1), facetItem(MEMBER_TENANT_ID, 1)))),
      arguments(MEMBER_TENANT_ID, "cql.allRecords=1", array("item.effectiveLocationId"), mapOf(
        "items.effectiveLocationId", facet(facetItem(LOCATIONS[0], 3), facetItem(LOCATIONS[1], 1)))),
      arguments(MEMBER_TENANT_ID, "cql.allRecords=1", array("holdings.tenantId"), mapOf(
        "holdings.tenantId", facet(facetItem(CENTRAL_TENANT_ID, 1), facetItem(MEMBER_TENANT_ID, 3)))),
      arguments(MEMBER_TENANT_ID, "callNumberType=\"local\"", array("item.effectiveLocationId"), mapOf(
        "items.effectiveLocationId", facet(facetItem(LOCATIONS[0], 2), facetItem(LOCATIONS[1], 1)))),
      arguments(MEMBER_TENANT_ID, "callNumberType=\"local\"", array("holdings.tenantId"), mapOf(
        "holdings.tenantId", facet(facetItem(CENTRAL_TENANT_ID, 1), facetItem(MEMBER_TENANT_ID, 2))))
    );
  }

  private static Instance[] instancesCentral() {
    return Stream.of(
        Lists.list("instance #01", CENTRAL_TENANT_ID, null, null, List.of()),
        List.of("instance #02", CENTRAL_TENANT_ID, LOCAL_CN_TYPE, LOCATIONS[0], List.of("central")))
      .map(BrowseCallNumberConsortiumIT::instance)
      .toArray(Instance[]::new);
  }

  private static Instance[] instancesMember() {
    return Stream.of(
        List.of("instance #02", MEMBER_TENANT_ID, LOCAL_CN_TYPE, LOCATIONS[1], List.of("member 1", "member 2")),
        List.of("instance #03", MEMBER_TENANT_ID, LOCAL_CN_TYPE, LOCATIONS[0], List.of("member 3")),
        List.of("instance #04", MEMBER_TENANT_ID, FOLIO_CN_TYPE, LOCATIONS[0], List.of("member 4")))
      .map(BrowseCallNumberConsortiumIT::instance)
      .toArray(Instance[]::new);
  }

  @SuppressWarnings("unchecked")
  private static Instance instance(List<Object> data) {
    var tenantId = (String) data.get(1);
    var items = ((List<String>) data.get(4)).stream()
      .map(callNumber -> new Item()
        .tenantId(tenantId)
        .id(randomId())
        .discoverySuppress(false)
        .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents()
          .callNumber(callNumber).typeId(String.valueOf(data.get(2))))
        .effectiveLocationId(String.valueOf(data.get(3)))
        .effectiveShelvingOrder(callNumber))
      .toList();
    var holdings = ((List<String>) data.get(4)).stream()
      .map(callNumber -> new Holding()
        .tenantId(tenantId)
        .id(randomId())
      )
      .toList();

    var title = (String) data.get(0);
    return new Instance()
      .id(title.equals("instance #02") ? "840d391f-ae06-4cbc-b66b-0ad317d193a2" : UUID.randomUUID().toString())
      .title(title)
      .staffSuppress(false)
      .discoverySuppress(false)
      .isBoundWith(false)
      .shared(CENTRAL_TENANT_ID.equals(tenantId))
      .tenantId(tenantId)
      .items(items)
      .holdings(holdings);
  }
}
