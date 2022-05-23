package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.ONE_MINUTE;
import static org.awaitility.Duration.TWO_HUNDRED_MILLISECONDS;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.folio.search.support.base.ApiEndpoints.instanceContributorBrowsePath;
import static org.folio.search.support.base.ApiEndpoints.recordFacets;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.array;
import static org.folio.search.utils.TestUtils.contributorBrowseItem;
import static org.folio.search.utils.TestUtils.facet;
import static org.folio.search.utils.TestUtils.facetItem;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.ParsedValueCount;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceContributorBrowseItem;
import org.folio.search.domain.dto.InstanceContributorBrowseResult;
import org.folio.search.domain.dto.RecordType;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.SearchUtils;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ContributorBrowseIT extends BaseIntegrationTest {

  private static final String[] NAME_TYPE_IDS =
    array("e2ef4075-310a-4447-a231-712bf10cc985", "0ad0a89a-741d-4f1a-85a6-ada214751013",
      "1f857623-89ca-4f0b-ab56-5c30f706df3e");
  private static final String[] TYPE_IDS =
    array("2a165833-1673-493f-934b-f3d3c8fcb299", "3ae36e29-e38f-457c-8fcf-1974a6cb63d3",
      "653ffe66-aa3f-4f1c-a090-c42c4011ef40");
  private static final Instance[] INSTANCES = instances();

  @BeforeAll
  static void prepare(@Autowired RestHighLevelClient restHighLevelClient) {
    setUpTenant(INSTANCES);
    await().atMost(ONE_MINUTE).pollInterval(TWO_HUNDRED_MILLISECONDS).untilAsserted(() -> {
      var aggregation = AggregationBuilders.count("contributorTypeId").field("contributorTypeId");
      var searchRequest = new SearchRequest().source(
          searchSource().query(matchAllQuery()).trackTotalHits(true).from(0).size(100).aggregation(aggregation))
        .indices(getIndexName(SearchUtils.CONTRIBUTOR_RESOURCE, TENANT_ID)).routing(TENANT_ID);
      var searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
      assertThat(((ParsedValueCount) searchResponse.getAggregations().asList().get(0)).getValue()).isEqualTo(14);
    });
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  private static Stream<Arguments> contributorBrowsingDataProvider() {
    var aroundQuery = "name > {value} or name < {value}";
    var aroundIncludingQuery = "name >= {value} or name < {value}";
    var forwardQuery = "name > {value}";
    var forwardIncludingQuery = "name >= {value}";
    var backwardQuery = "name < {value}";
    var backwardIncludingQuery = "name <= {value}";

    return Stream.of(arguments(aroundQuery, "John", 5,
        new InstanceContributorBrowseResult().totalRecords(10).prev("Bon Jovi").next("Klaus Meine").items(
          List.of(contributorBrowseItem(2, "Bon Jovi", NAME_TYPE_IDS[0], TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(2, "George Harrison", NAME_TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(0, true, "John"),
            contributorBrowseItem(2, "John Lennon", NAME_TYPE_IDS[2], TYPE_IDS[0]),
            contributorBrowseItem(1, "Klaus Meine", NAME_TYPE_IDS[1], (String[]) null)))),

      arguments(aroundQuery, "Lenon", 5,
        new InstanceContributorBrowseResult().totalRecords(10).prev("Klaus Meine").next(null).items(
          List.of(contributorBrowseItem(1, "Klaus Meine", NAME_TYPE_IDS[1], (String[]) null),
            contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
            contributorBrowseItem(0, true, "Lenon"),
            contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(2, "Ringo Starr", NAME_TYPE_IDS[1], TYPE_IDS[0], TYPE_IDS[1])))),

      arguments(aroundIncludingQuery, "Meine", 5,
        new InstanceContributorBrowseResult().totalRecords(10).prev("Klaus Meine").next(null).items(
          List.of(contributorBrowseItem(1, "Klaus Meine", NAME_TYPE_IDS[1], (String[]) null),
            contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
            contributorBrowseItem(0, true, "Meine"),
            contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(2, "Ringo Starr", NAME_TYPE_IDS[1], TYPE_IDS[0], TYPE_IDS[1])))),

      arguments(aroundIncludingQuery, "Zak", 25,
        new InstanceContributorBrowseResult().totalRecords(10).prev(null).next(null).items(
          List.of(contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[0], TYPE_IDS[0]),
            contributorBrowseItem(1, "Bon Jovi", NAME_TYPE_IDS[1], TYPE_IDS[0]),
            contributorBrowseItem(2, "Bon Jovi", NAME_TYPE_IDS[0], TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(2, "George Harrison", NAME_TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(2, "John Lennon", NAME_TYPE_IDS[2], TYPE_IDS[0]),
            contributorBrowseItem(1, "Klaus Meine", NAME_TYPE_IDS[1], (String[]) null),
            contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
            contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(2, "Ringo Starr", NAME_TYPE_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
            contributorBrowseItem(0, true, "Zak")))),

      arguments(aroundIncludingQuery, "PMC", 5,
        new InstanceContributorBrowseResult().totalRecords(10).prev("Klaus Meine").next(null).items(
          List.of(contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
            contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(0, true, "PMC"),
            contributorBrowseItem(2, "Ringo Starr", NAME_TYPE_IDS[1], TYPE_IDS[0], TYPE_IDS[1])))),

      arguments(aroundIncludingQuery, "a", 5,
        new InstanceContributorBrowseResult().totalRecords(10).prev(null).next("Anthony Kiedis").items(
          List.of(contributorBrowseItem(0, true, "a"),
            contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[0], TYPE_IDS[0])))),

      arguments(aroundIncludingQuery, "z", 5,
        new InstanceContributorBrowseResult().totalRecords(10).prev("Paul McCartney").next(null).items(
          List.of(contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(2, "Ringo Starr", NAME_TYPE_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
            contributorBrowseItem(0, true, "z")))),

      // browsing forward
      arguments(forwardQuery, "ringo", 5,
        new InstanceContributorBrowseResult().totalRecords(10).prev("Ringo Starr").next(null)
          .items(List.of(contributorBrowseItem(2, "Ringo Starr", NAME_TYPE_IDS[1], TYPE_IDS[0], TYPE_IDS[1])))),

      arguments(forwardQuery, "anthony", 5,
        new InstanceContributorBrowseResult().totalRecords(10).prev("Anthony Kiedis").next("George Harrison").items(
          List.of(contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[0], TYPE_IDS[0]),
            contributorBrowseItem(1, "Bon Jovi", NAME_TYPE_IDS[1], TYPE_IDS[0]),
            contributorBrowseItem(2, "Bon Jovi", NAME_TYPE_IDS[0], TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(2, "George Harrison", NAME_TYPE_IDS[1], TYPE_IDS[2])))),

      arguments(forwardQuery, "Z", 10, new InstanceContributorBrowseResult().totalRecords(10).items(emptyList())),

      arguments(forwardIncludingQuery, "Ringo Starr", 5,
        new InstanceContributorBrowseResult().totalRecords(10).prev("Ringo Starr").next(null)
          .items(List.of(contributorBrowseItem(2, "Ringo Starr", NAME_TYPE_IDS[1], TYPE_IDS[0], TYPE_IDS[1])))),

      arguments(forwardIncludingQuery, "anthony", 5,
        new InstanceContributorBrowseResult().totalRecords(10).prev("Anthony Kiedis").next("George Harrison").items(
          List.of(contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[0], TYPE_IDS[0]),
            contributorBrowseItem(1, "Bon Jovi", NAME_TYPE_IDS[1], TYPE_IDS[0]),
            contributorBrowseItem(2, "Bon Jovi", NAME_TYPE_IDS[0], TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(2, "George Harrison", NAME_TYPE_IDS[1], TYPE_IDS[2])))),

      // browsing backward
      arguments(backwardQuery, "Ringo Starr", 5,
        new InstanceContributorBrowseResult().totalRecords(10).prev("John Lennon").next("Ringo Starr").items(
          List.of(contributorBrowseItem(2, "John Lennon", NAME_TYPE_IDS[2], TYPE_IDS[0]),
            contributorBrowseItem(1, "Klaus Meine", NAME_TYPE_IDS[1], (String[]) null),
            contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
            contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(2, "Ringo Starr", NAME_TYPE_IDS[1], TYPE_IDS[0], TYPE_IDS[1])))),

      arguments(backwardQuery, "R", 5,
        new InstanceContributorBrowseResult().totalRecords(10).prev("George Harrison").next("Paul McCartney").items(
          List.of(contributorBrowseItem(2, "George Harrison", NAME_TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(2, "John Lennon", NAME_TYPE_IDS[2], TYPE_IDS[0]),
            contributorBrowseItem(1, "Klaus Meine", NAME_TYPE_IDS[1], (String[]) null),
            contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
            contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2])))),

      arguments(backwardQuery, "A", 10, new InstanceContributorBrowseResult().totalRecords(10).items(emptyList())),

      arguments(backwardIncludingQuery, "ringo", 5,
        new InstanceContributorBrowseResult().totalRecords(10).prev("George Harrison").next("Paul McCartney").items(
          List.of(contributorBrowseItem(2, "George Harrison", NAME_TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(2, "John Lennon", NAME_TYPE_IDS[2], TYPE_IDS[0]),
            contributorBrowseItem(1, "Klaus Meine", NAME_TYPE_IDS[1], (String[]) null),
            contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
            contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2])))));
  }

  private static Instance[] instances() {
    return contributorBrowseInstanceData().stream().map(ContributorBrowseIT::instance).toArray(Instance[]::new);
  }

  @SuppressWarnings("unchecked")
  private static Instance instance(List<Object> data) {
    return new Instance().id(randomId()).title((String) data.get(0)).contributors((List<Contributor>) data.get(1))
      .staffSuppress(false).discoverySuppress(false).holdings(emptyList());
  }

  private static List<List<Object>> contributorBrowseInstanceData() {
    return List.of(List.of("instance #01", List.of(
        contributor("Bon Jovi", NAME_TYPE_IDS[0], TYPE_IDS[0]),
        contributor("Klaus Meine", NAME_TYPE_IDS[0], TYPE_IDS[0]),
        contributor("Anthony Kiedis", NAME_TYPE_IDS[0], TYPE_IDS[0])
      )),
      List.of("instance #02", List.of(
        contributor("Bon Jovi", NAME_TYPE_IDS[1], TYPE_IDS[0]),
        contributor("Klaus Meine", NAME_TYPE_IDS[0], TYPE_IDS[1]),
        contributor("Anthony Kiedis", NAME_TYPE_IDS[1], TYPE_IDS[2])
      )),
      List.of("instance #03", List.of(
        contributor("Bon Jovi", NAME_TYPE_IDS[0], TYPE_IDS[1]),
        contributor("Bon Jovi", NAME_TYPE_IDS[0], TYPE_IDS[2]),
        contributor("Klaus Meine", NAME_TYPE_IDS[1], null)
      )),
      List.of("instance #04", List.of(
        contributor("John Lennon", NAME_TYPE_IDS[2], TYPE_IDS[0]),
        contributor("Paul McCartney", NAME_TYPE_IDS[0], TYPE_IDS[1]),
        contributor("George Harrison", NAME_TYPE_IDS[1], TYPE_IDS[2]),
        contributor("Ringo Starr", NAME_TYPE_IDS[1], TYPE_IDS[0])
      )),
      List.of("instance #05", List.of(
        contributor("John Lennon", NAME_TYPE_IDS[2], TYPE_IDS[0]),
        contributor("Paul McCartney", NAME_TYPE_IDS[0], TYPE_IDS[2]),
        contributor("George Harrison", NAME_TYPE_IDS[1], TYPE_IDS[2]),
        contributor("Ringo Starr", NAME_TYPE_IDS[1], TYPE_IDS[1])
      )));
  }

  private static Contributor contributor(String name, String nameTypeId, String typeId) {
    return new Contributor().name(name).contributorNameTypeId(nameTypeId).contributorTypeId(typeId);
  }

  private static Stream<Arguments> facetQueriesProvider() {
    return Stream.of(arguments("name=*", array("contributorNameTypeId"), mapOf("contributorNameTypeId",
        facet(facetItem(NAME_TYPE_IDS[0], 4), facetItem(NAME_TYPE_IDS[1], 5), facetItem(NAME_TYPE_IDS[2], 1)))),

      arguments("name=*", array("contributorNameTypeId:2"),
        mapOf("contributorNameTypeId", facet(facetItem(NAME_TYPE_IDS[0], 4), facetItem(NAME_TYPE_IDS[1], 5)))),

      arguments("contributorNameTypeId==\"" + NAME_TYPE_IDS[0] + "\"", array("contributorNameTypeId:1"),
        mapOf("contributorNameTypeId", facet(facetItem(NAME_TYPE_IDS[0], 4)))),

      arguments("contributorNameTypeId==(\"" + NAME_TYPE_IDS[1] + "\" or \"" + NAME_TYPE_IDS[2] + "\")",
        array("contributorNameTypeId:2"),
        mapOf("contributorNameTypeId", facet(facetItem(NAME_TYPE_IDS[1], 5), facetItem(NAME_TYPE_IDS[2], 1)))));
  }

  @MethodSource("contributorBrowsingDataProvider")
  @DisplayName("browseByContributor_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}'', limit={2}")
  void browseByContributor_parameterized(String query, String anchor, Integer limit,
                                         InstanceContributorBrowseResult expected) {
    var request = get(instanceContributorBrowsePath()).param("query", prepareQuery(query, '"' + anchor + '"'))
      .param("limit", String.valueOf(limit));

    var actual = parseResponse(doGet(request), InstanceContributorBrowseResult.class);
    actual.getItems().sort(Comparator.comparing(InstanceContributorBrowseItem::getName, StringUtils::compareIgnoreCase)
      .thenComparing((o1, o2) -> StringUtils.compare(o1.getContributorNameTypeId(), o2.getContributorNameTypeId())));
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void browseByContributor_withNameTypeFilter() {
    var request = get(instanceContributorBrowsePath()).param("query",
      "(" + prepareQuery("name >= {value} or name < {value}", '"' + "John" + '"') + ") and contributorNameTypeId=="
        + NAME_TYPE_IDS[0]).param("limit", "5");

    var actual = parseResponse(doGet(request), InstanceContributorBrowseResult.class);
    var expected = new InstanceContributorBrowseResult().totalRecords(4).prev(null).next(null).items(
      List.of(contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[0], TYPE_IDS[0]),
        contributorBrowseItem(2, "Bon Jovi", NAME_TYPE_IDS[0], TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
        contributorBrowseItem(0, true, "John"),
        contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
        contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2])));

    assertThat(actual).isEqualTo(expected);
  }

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForContributors_parameterized")
  void getFacetsForContributors_parameterized(String query, String[] facets, Map<String, Facet> expected) {
    var request = doGet(recordFacets(RecordType.CONTRIBUTORS, query, facets));
    var actual = parseResponse(request, FacetResult.class);

    expected.forEach((facetName, expectedFacet) -> {
      var actualFacet = actual.getFacets().get(facetName);

      assertThat(actualFacet).isNotNull();
      assertThat(actualFacet.getValues()).containsExactlyInAnyOrderElementsOf(expectedFacet.getValues());
    });
  }
}
