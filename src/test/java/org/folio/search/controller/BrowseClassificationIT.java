package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.folio.search.model.Pair.pair;
import static org.folio.search.support.base.ApiEndpoints.browseConfigPath;
import static org.folio.search.support.base.ApiEndpoints.instanceClassificationBrowsePath;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.classificationBrowseItem;
import static org.folio.search.utils.TestUtils.classificationBrowseResult;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.folio.search.domain.dto.BrowseConfig;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.domain.dto.ClassificationNumberBrowseResult;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceClassificationsInner;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.model.Pair;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.SearchUtils;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class BrowseClassificationIT extends BaseIntegrationTest {

  private static final String LC_TYPE_ID = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91a";
  private static final String LC2_TYPE_ID = "308c950f-8209-4f2e-9702-0c004a9f21bc";
  private static final String DEWEY_TYPE_ID = "50524585-046b-49a1-8ca7-8d46f2a8dc19";

  @BeforeAll
  static void prepare(@Autowired RestHighLevelClient restHighLevelClient) {
    setUpTenant(instances());
    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var searchRequest = new SearchRequest()
        .source(searchSource().query(matchAllQuery()).trackTotalHits(true).from(0).size(0))
        .indices(getIndexName(SearchUtils.INSTANCE_CLASSIFICATION_RESOURCE, TENANT_ID));
      var searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
      assertThat(searchResponse.getHits().getTotalHits().value).isEqualTo(17);
    });
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @BeforeEach
  void setUp() {
    updateLcConfig(List.of(UUID.fromString(LC_TYPE_ID)));
  }

  @MethodSource("classificationBrowsingDataProvider")
  @DisplayName("browseByClassification_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}'', limit={2}")
  void browseByClassification_parameterized(String query, String anchor, Integer limit,
                                            ClassificationNumberBrowseResult expected) {
    var request = get(instanceClassificationBrowsePath(BrowseOptionType.LC))
      .param("query", prepareQuery(query, '"' + anchor + '"'))
      .param("limit", String.valueOf(limit));
    var actual = parseResponse(doGet(request), ClassificationNumberBrowseResult.class);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void browseByClassification_allOption_browsingAroundWithPrecedingRecordsCount() {
    var request = get(instanceClassificationBrowsePath(BrowseOptionType.ALL))
      .param("query", prepareQuery("number < {value} or number >= {value}", "\"292.07\""))
      .param("limit", "10")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), ClassificationNumberBrowseResult.class);
    assertThat(actual).isEqualTo(classificationBrowseResult(null, "N6679.R64 G88 2010", 17, List.of(
      classificationBrowseItem("146.4", DEWEY_TYPE_ID, 2),
      classificationBrowseItem("221.609", DEWEY_TYPE_ID, 1),
      classificationBrowseItem("292.07", DEWEY_TYPE_ID, 1, true),
      classificationBrowseItem("333.91", DEWEY_TYPE_ID, 1),
      classificationBrowseItem("372.4", DEWEY_TYPE_ID, 1),
      classificationBrowseItem("BJ1453 .I49 1983", LC_TYPE_ID, 1),
      classificationBrowseItem("BJ1453 .I49 1983", LC2_TYPE_ID, 1),
      classificationBrowseItem("HD1691 .I5 1967", LC_TYPE_ID, 1),
      classificationBrowseItem("HQ536 .A565 2018", LC2_TYPE_ID, 1),
      classificationBrowseItem("N6679.R64 G88 2010", LC_TYPE_ID, 1)
    )));
  }

  @Test
  void browseByClassification_noExactMatch() {
    var request = get(instanceClassificationBrowsePath(BrowseOptionType.ALL))
      .param("query", prepareQuery("number < {value} or number >= {value}", "\"292.08\""))
      .param("limit", "3")
      .param("precedingRecordsCount", "1");
    var actual = parseResponse(doGet(request), ClassificationNumberBrowseResult.class);
    assertThat(actual).isEqualTo(classificationBrowseResult("292.07", "333.91", 17, List.of(
      classificationBrowseItem("292.07", DEWEY_TYPE_ID, 1),
      classificationBrowseItem("292.08", null, 0, true),
      classificationBrowseItem("333.91", DEWEY_TYPE_ID, 1)
    )));
  }

  @Test
  void browseByClassification_lcOptionConfiguredWithTwoIds() {
    updateLcConfig(List.of(UUID.fromString(LC_TYPE_ID), UUID.fromString(DEWEY_TYPE_ID)));

    var request = get(instanceClassificationBrowsePath(BrowseOptionType.LC))
      .param("query", prepareQuery("number < {value} or number >= {value}", "\"292.07\""))
      .param("limit", "10")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), ClassificationNumberBrowseResult.class);
    assertThat(actual).isEqualTo(classificationBrowseResult(null, "QD453 .M8 1961", 13, List.of(
      classificationBrowseItem("146.4", DEWEY_TYPE_ID, 2),
      classificationBrowseItem("221.609", DEWEY_TYPE_ID, 1),
      classificationBrowseItem("292.07", DEWEY_TYPE_ID, 1, true),
      classificationBrowseItem("333.91", DEWEY_TYPE_ID, 1),
      classificationBrowseItem("372.4", DEWEY_TYPE_ID, 1),
      classificationBrowseItem("BJ1453 .I49 1983", LC_TYPE_ID, 1),
      classificationBrowseItem("HD1691 .I5 1967", LC_TYPE_ID, 1),
      classificationBrowseItem("N6679.R64 G88 2010", LC_TYPE_ID, 1),
      classificationBrowseItem("QD33 .O87", LC_TYPE_ID, 2),
      classificationBrowseItem("QD453 .M8 1961", LC_TYPE_ID, 1)
    )));
  }

  private static void updateLcConfig(List<UUID> typeIds) {
    var config = new BrowseConfig()
      .id(BrowseOptionType.LC)
      .shelvingAlgorithm(ShelvingOrderAlgorithmType.LC)
      .typeIds(typeIds);

    doPut(browseConfigPath(BrowseType.INSTANCE_CLASSIFICATION, BrowseOptionType.LC), config);
  }

  private static Stream<Arguments> classificationBrowsingDataProvider() {
    var aroundIncludingQuery = "number < {value} or number >= {value}";
    var forwardQuery = "number > {value}";
    var forwardIncludingQuery = "number >= {value}";
    var backwardQuery = "number < {value}";
    var backwardIncludingQuery = "number <= {value}";

    return Stream.of(
      arguments(aroundIncludingQuery, "QD33 .O87", 5, classificationBrowseResult("HD1691 .I5 1967",
        "SF433 .D47 2004", 8, List.of(
          classificationBrowseItem("HD1691 .I5 1967", LC_TYPE_ID, 1),
          classificationBrowseItem("N6679.R64 G88 2010", LC_TYPE_ID, 1),
          classificationBrowseItem("QD33 .O87", LC_TYPE_ID, 2, true),
          classificationBrowseItem("QD453 .M8 1961", LC_TYPE_ID, 1),
          classificationBrowseItem("SF433 .D47 2004", LC_TYPE_ID, 1)
        ))),

      arguments(forwardQuery, "QD33 .O87", 5, classificationBrowseResult("QD453 .M8 1961", null, 8, List.of(
        classificationBrowseItem("QD453 .M8 1961", LC_TYPE_ID, 1),
        classificationBrowseItem("SF433 .D47 2004", LC_TYPE_ID, 1),
        classificationBrowseItem("TN800 .F4613", LC_TYPE_ID, 1),
        classificationBrowseItem("TX545 .M45", LC_TYPE_ID, 1)
      ))),

      arguments(forwardQuery, "Z", 10, classificationBrowseResult(null, null, 8, emptyList())),

      arguments(forwardIncludingQuery, "QD33 .O87", 5, classificationBrowseResult("QD33 .O87", null, 8, List.of(
        classificationBrowseItem("QD33 .O87", LC_TYPE_ID, 2),
        classificationBrowseItem("QD453 .M8 1961", LC_TYPE_ID, 1),
        classificationBrowseItem("SF433 .D47 2004", LC_TYPE_ID, 1),
        classificationBrowseItem("TN800 .F4613", LC_TYPE_ID, 1),
        classificationBrowseItem("TX545 .M45", LC_TYPE_ID, 1)
      ))),

      arguments(backwardQuery, "QD33 .O87", 5, classificationBrowseResult(null, "N6679.R64 G88 2010", 8, List.of(
        classificationBrowseItem("BJ1453 .I49 1983", LC_TYPE_ID, 1),
        classificationBrowseItem("HD1691 .I5 1967", LC_TYPE_ID, 1),
        classificationBrowseItem("N6679.R64 G88 2010", LC_TYPE_ID, 1)
      ))),

      arguments(backwardQuery, "A", 10, classificationBrowseResult(null, null, 8, emptyList())),

      arguments(backwardIncludingQuery, "QD33 .O87", 5, classificationBrowseResult(null, "QD33 .O87", 8, List.of(
        classificationBrowseItem("BJ1453 .I49 1983", LC_TYPE_ID, 1),
        classificationBrowseItem("HD1691 .I5 1967", LC_TYPE_ID, 1),
        classificationBrowseItem("N6679.R64 G88 2010", LC_TYPE_ID, 1),
        classificationBrowseItem("QD33 .O87", LC_TYPE_ID, 2)
      )))
    );
  }

  private static Instance[] instances() {
    return classificationBrowseInstanceData().stream()
      .map(BrowseClassificationIT::instance)
      .toArray(Instance[]::new);
  }

  @SuppressWarnings("unchecked")
  private static Instance instance(List<Object> data) {
    return new Instance()
      .id(randomId())
      .title((String) data.get(0))
      .classifications(((List<Pair<String, String>>) data.get(1)).stream()
        .map(pair -> new InstanceClassificationsInner()
          .classificationNumber(String.valueOf(pair.getFirst()))
          .classificationTypeId(String.valueOf(pair.getSecond())))
        .collect(Collectors.toList()))
      .staffSuppress(false)
      .discoverySuppress(false)
      .holdings(emptyList());
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
