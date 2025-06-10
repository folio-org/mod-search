package org.folio.api.browse;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CLASSIFICATIONS;
import static org.folio.search.model.Pair.pair;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceClassificationBrowsePath;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.classificationBrowseItem;
import static org.folio.support.utils.TestUtils.classificationBrowseResult;
import static org.folio.support.utils.TestUtils.facet;
import static org.folio.support.utils.TestUtils.facetItem;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.Classification;
import org.folio.search.domain.dto.ClassificationNumberBrowseResult;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.RecordType;
import org.folio.search.model.Pair;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseConsortiumIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.RequestOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = "folio.search-config.indexing.instance-children-index-enabled=true")
class BrowseClassificationConsortiumIT extends BaseConsortiumIntegrationTest {

  private static final String LC_TYPE_ID = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91a";
  private static final String LC2_TYPE_ID = "308c950f-8209-4f2e-9702-0c004a9f21bc";
  private static final String DEWEY_TYPE_ID = "50524585-046b-49a1-8ca7-8d46f2a8dc19";
  private static final Instance[] INSTANCES_MEMBER = instancesMember();
  private static final Instance[] INSTANCES_CENTRAL = instancesCentral();

  @BeforeAll
  static void prepare(@Autowired SubResourcesLockRepository subResourcesLockRepository) {
    setUpTenant(CENTRAL_TENANT_ID);
    setUpTenant(MEMBER_TENANT_ID);

    enableFeature(CENTRAL_TENANT_ID, BROWSE_CLASSIFICATIONS);

    var timestamp = subResourcesLockRepository.lockSubResource(ReindexEntityType.CLASSIFICATION, CENTRAL_TENANT_ID);
    if (timestamp.isEmpty()) {
      throw new IllegalStateException("Unexpected state of database: unable to lock classification resource");
    }

    saveRecords(CENTRAL_TENANT_ID, instanceSearchPath(), asList(INSTANCES_CENTRAL),
      INSTANCES_CENTRAL.length,
      instance -> inventoryApi.createInstance(CENTRAL_TENANT_ID, instance));
    saveRecords(MEMBER_TENANT_ID, instanceSearchPath(), asList(INSTANCES_MEMBER),
      INSTANCES_CENTRAL.length + INSTANCES_MEMBER.length,
      instance -> inventoryApi.createInstance(MEMBER_TENANT_ID, instance));

    subResourcesLockRepository.unlockSubResource(ReindexEntityType.CLASSIFICATION, timestamp.get(), CENTRAL_TENANT_ID);

    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() -> {
      var searchRequest = new SearchRequest()
        .source(searchSource().query(matchAllQuery()).trackTotalHits(true).from(0).size(100))
        .indices(getIndexName(ResourceType.INSTANCE_CLASSIFICATION, CENTRAL_TENANT_ID));
      var searchResponse = elasticClient.search(searchRequest, RequestOptions.DEFAULT);
      assertThat(searchResponse.getHits().getTotalHits().value()).isEqualTo(17);
    });
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void browseByClassification_shared() {
    var request = get(instanceClassificationBrowsePath(BrowseOptionType.ALL))
      .param("query", prepareQuery("number < {value} or number >= {value} and instances.shared==true",
        "\"QD33 .O87\""))
      .param("limit", "4")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), ClassificationNumberBrowseResult.class);
    assertThat(actual).isEqualTo(classificationBrowseResult("HQ536 .A565 2018", null, 8, List.of(
      classificationBrowseItem("HQ536 .A565 2018", LC2_TYPE_ID, 1),
      classificationBrowseItem("N6679.R64 G88 2010", LC_TYPE_ID, 1),
      classificationBrowseItem("QD33 .O87", LC_TYPE_ID, 1, true),
      classificationBrowseItem("QD453 .M8 1961", LC_TYPE_ID, 1)

    )));
  }

  @Test
  void browseByClassification_local() {
    var request = get(instanceClassificationBrowsePath(BrowseOptionType.ALL))
      .param("query", prepareQuery("number < {value} or number >= {value} and instances.shared==false",
        "\"QD33 .O87\""))
      .param("limit", "4")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), ClassificationNumberBrowseResult.class);
    assertThat(actual).isEqualTo(classificationBrowseResult("333.91", "SF433 .D47 2004", 11, List.of(
      classificationBrowseItem("333.91", DEWEY_TYPE_ID, 1),
      classificationBrowseItem("372.4", DEWEY_TYPE_ID, 1),
      classificationBrowseItem("QD33 .O87", LC_TYPE_ID, 1, true),
      classificationBrowseItem("SF433 .D47 2004", LC_TYPE_ID, 1)
    )));
  }

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForClassifications_parameterized")
  void getFacetsForClassifications_parameterized(String query, String[] facets, Map<String, Facet> expected) {
    var actual = parseResponse(doGet(recordFacetsPath(RecordType.CLASSIFICATIONS, query, facets)), FacetResult.class);

    expected.forEach((facetName, expectedFacet) -> {
      var actualFacet = actual.getFacets().get(facetName);

      assertThat(actualFacet).isNotNull();
      assertThat(actualFacet.getValues())
        .containsExactlyInAnyOrderElementsOf(expectedFacet.getValues());
    });
  }

  private static Stream<Arguments> facetQueriesProvider() {
    return Stream.of(
      arguments("cql.allRecords=1", array("instances.shared"), mapOf("instances.shared",
        facet(facetItem("false", 11), facetItem("true", 8)))),
      arguments("cql.allRecords=1", array("instances.tenantId"),
        mapOf("instances.tenantId", facet(facetItem(MEMBER_TENANT_ID, 11),
          facetItem(CENTRAL_TENANT_ID, 8))))
    );
  }

  private static Instance[] instancesCentral() {
    return classificationBrowseInstanceData().subList(0, 5).stream()
      .map(BrowseClassificationConsortiumIT::instance)
      .toArray(Instance[]::new);
  }

  private static Instance[] instancesMember() {
    return classificationBrowseInstanceData().subList(5, 10).stream()
      .map(BrowseClassificationConsortiumIT::instance)
      .toArray(Instance[]::new);
  }

  private static Instance instance(List<Object> data) {
    @SuppressWarnings("unchecked")
    var pairs = (List<Pair<String, String>>) data.get(1);
    var title = (String) data.get(0);
    var instance = new Instance()
      .id(randomId())
      .title(title)
      .classifications(pairs.stream()
        .map(pair -> new Classification()
          .classificationNumber(String.valueOf(pair.getFirst()))
          .classificationTypeId(String.valueOf(pair.getSecond())))
        .toList())
      .staffSuppress(false)
      .discoverySuppress(false)
      .holdings(emptyList());

    if ("instance #10".equals(title)) {
      instance.setContributors(List.of(new Contributor().name("Contributor #1"), new Contributor().name("Contributor #2")));
    }

    return instance;
  }

  private static List<List<Object>> classificationBrowseInstanceData() {
    return List.of(
      List.of("instance #01", List.of(pair("BJ1453 .I49 1983", LC_TYPE_ID), pair("HD1691 .I5 1967", LC_TYPE_ID))),
      List.of("instance #02", List.of(pair("BJ1453 .I49 1983", LC2_TYPE_ID))),
      List.of("instance #03", List.of(pair("HQ536 .A565 2018", LC2_TYPE_ID), pair("N6679.R64 G88 2010", LC_TYPE_ID))),
      List.of("instance #04", List.of(pair("QD33 .O87", LC_TYPE_ID))),
      List.of("instance #05", List.of(pair("QD453 .M8 1961", LC_TYPE_ID), pair("146.4", DEWEY_TYPE_ID))),
      List.of("instance #06", List.of(pair("SF433 .D47 2004", LC_TYPE_ID), pair("TX545 .M45", LC_TYPE_ID))),
      List.of("instance #07", List.of(pair("221.609", DEWEY_TYPE_ID), pair("SF991 .M94", LC2_TYPE_ID))),
      List.of("instance #08", List.of(pair("TN800 .F4613", LC_TYPE_ID))),
      List.of("instance #09", List.of(pair("292.07", DEWEY_TYPE_ID), pair("333.91", DEWEY_TYPE_ID),
        pair("372.4", DEWEY_TYPE_ID))),
      List.of("instance #10", List.of(pair("146.4", DEWEY_TYPE_ID), pair("QD33 .O87", LC_TYPE_ID),
        pair("SF991 .M94", null)))
    );
  }
}
