package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.folio.search.support.base.ApiEndpoints.browseConfigPath;
import static org.folio.search.support.base.ApiEndpoints.instanceCallNumberBrowsePath;
import static org.folio.search.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.search.utils.CallNumberTestData.CallNumberTypeId.LC;
import static org.folio.search.utils.CallNumberTestData.callNumbers;
import static org.folio.search.utils.CallNumberTestData.locations;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.array;
import static org.folio.search.utils.TestUtils.facet;
import static org.folio.search.utils.TestUtils.facetItem;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.mockCallNumberTypes;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.folio.search.domain.dto.BrowseConfig;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.domain.dto.CallNumberBrowseResult;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.RecordType;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.model.index.CallNumberResource;
import org.folio.search.model.types.ResourceType;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.CallNumberTestData;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@IntegrationTest
class BrowseCallNumberIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(CallNumberTestData.instances().toArray(Instance[]::new));
    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var counted = countIndexDocument(ResourceType.INSTANCE_CALL_NUMBER, TENANT_ID);
      assertThat(counted).isEqualTo(100);
    });
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @BeforeEach
  void setUp() {
    updateLcConfig(List.of(UUID.fromString(LC.getId())));
  }

  @MethodSource("callNumberBrowsingDataProvider")
  @DisplayName("browseByCallNumber_parameterized")
  @ParameterizedTest(name = "[{0}] query={1}, option={2}, value=''{3}'', limit={4}")
  void browseByCallNumber_parameterized(int index, String query, BrowseOptionType optionType, String input,
                                        Integer limit, CallNumberBrowseResult expected) {
    var request = get(instanceCallNumberBrowsePath(optionType))
      .param("expandAll", "true")
      .param("query", prepareQuery(query, '"' + input + '"'))
      .param("limit", String.valueOf(limit));
    var actual = parseResponse(doGet(request), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(expected);
  }

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForCallNumbers_parameterized")
  void getFacetsForSubjects_parameterized(String query, String[] facets, Map<String, Facet> expected) {
    var actual = parseResponse(doGet(recordFacetsPath(RecordType.CALL_NUMBERS, query, facets)), FacetResult.class);

    expected.forEach((facetName, expectedFacet) -> {
      var actualFacet = actual.getFacets().get(facetName);

      assertThat(actualFacet).isNotNull();
      assertThat(actualFacet.getValues())
        .containsExactlyInAnyOrderElementsOf(expectedFacet.getValues());
    });
  }

  private static Stream<Arguments> facetQueriesProvider() {
    var locations = locations();
    return Stream.of(
      arguments("cql.allRecords=1", array("instances.locationId"), mapOf("instances.locationId",
        facet(facetItem(locations.get(1), 42), facetItem(locations.get(2), 34), facetItem(locations.get(3), 24)))),
      arguments("callNumberTypeId=\"" + LC.getId() + "\"", array("instances.locationId"), mapOf("instances.locationId",
        facet(facetItem(locations.get(1), 8), facetItem(locations.get(2), 8), facetItem(locations.get(3), 4)))),
      arguments("callNumberTypeId==lc", array("instances.locationId"), mapOf("instances.locationId",
        facet(facetItem(locations.get(1), 8), facetItem(locations.get(2), 8), facetItem(locations.get(3), 4)))),
      arguments("callNumberTypeId==all", array("instances.locationId"), mapOf("instances.locationId",
        facet(facetItem(locations.get(1), 42), facetItem(locations.get(2), 34), facetItem(locations.get(3), 24))))
    );
  }

  private static Stream<Arguments> callNumberBrowsingDataProvider() {
    var aroundQuery = "fullCallNumber >= {value} or fullCallNumber < {value}";
    var forwardQuery = "fullCallNumber > {value}";
    var backwardQuery = "fullCallNumber < {value}";

    var callNumbers = callNumbers().stream()
      .map(CallNumberTestData.CallNumberTestDataRecord::callNumber)
      .collect(Collectors.toMap(callNumberResource -> Integer.parseInt(callNumberResource.id()), identity()));

    return Stream.of(
      // anchor call number appears in the middle of the result set
      arguments(1, aroundQuery, BrowseOptionType.ALL, callNumbers.get(1).fullCallNumber(), 5,
        cnBrowseResult(callNumbers.get(91).fullCallNumber(), callNumbers.get(68).fullCallNumber(), 100, List.of(
          cnBrowseItem(callNumbers.get(91), 1),
          cnBrowseItem(callNumbers.get(25), 1),
          cnBrowseItem(callNumbers.get(1), 3, true),
          cnBrowseItem(callNumbers.get(70), 1),
          cnBrowseItem(callNumbers.get(68), 1)
        ))),

      // not existed anchor call number appears in the middle of the result set
      arguments(2, aroundQuery, BrowseOptionType.ALL, "TA357 .A78 2011", 5,
        cnBrowseResult(callNumbers.get(25).fullCallNumber(), callNumbers.get(68).fullCallNumber(), 100, List.of(
          cnBrowseItem(callNumbers.get(25), 1),
          cnBrowseItem(callNumbers.get(1), 3),
          cnEmptyBrowseItem("TA357 .A78 2011"),
          cnBrowseItem(callNumbers.get(70), 1),
          cnBrowseItem(callNumbers.get(68), 1)
        ))),

      // anchor call number appears first in the result set
      arguments(3, aroundQuery, BrowseOptionType.ALL, callNumbers.get(50).fullCallNumber(), 5,
        cnBrowseResult(null, callNumbers.get(95).fullCallNumber(), 100, List.of(
          cnBrowseItem(callNumbers.get(50), 1, true),
          cnBrowseItem(callNumbers.get(97), 1),
          cnBrowseItem(callNumbers.get(95), 1)
        ))),

      // not existed anchor call number appears first in the result set
      arguments(4, aroundQuery, BrowseOptionType.ALL, "0.0", 5,
        cnBrowseResult(null, callNumbers.get(97).fullCallNumber(), 100, List.of(
          cnEmptyBrowseItem("0.0"),
          cnBrowseItem(callNumbers.get(50), 1),
          cnBrowseItem(callNumbers.get(97), 1)
        ))),

      // anchor call number appears last in the result set
      arguments(5, aroundQuery, BrowseOptionType.ALL, callNumbers.get(11).fullCallNumber(), 5,
        cnBrowseResult(callNumbers.get(49).fullCallNumber(), null, 100, List.of(
          cnBrowseItem(callNumbers.get(49), 1),
          cnBrowseItem(callNumbers.get(44), 1),
          cnBrowseItem(callNumbers.get(11), 1, true)
        ))),

      // not existed anchor call number appears last in the result set
      arguments(6, aroundQuery, BrowseOptionType.ALL, "ZZ", 5,
        cnBrowseResult(callNumbers.get(44).fullCallNumber(), null, 100, List.of(
          cnBrowseItem(callNumbers.get(44), 1),
          cnBrowseItem(callNumbers.get(11), 1),
          cnEmptyBrowseItem("ZZ")
        ))),

      // anchor call number appears in the middle of the result set when filtering by type
      arguments(7, aroundQuery, BrowseOptionType.LC, callNumbers.get(46).fullCallNumber(), 5,
        cnBrowseResult(callNumbers.get(66).fullCallNumber(), callNumbers.get(21).fullCallNumber(), 20, List.of(
          cnBrowseItem(callNumbers.get(66), 1),
          cnBrowseItem(callNumbers.get(96), 1),
          cnBrowseItem(callNumbers.get(46), 1, true),
          cnBrowseItem(callNumbers.get(86), 1),
          cnBrowseItem(callNumbers.get(21), 2)
        ))),

      // forward browsing from the middle of the result set
      arguments(8, forwardQuery, BrowseOptionType.ALL, callNumbers.get(22).fullCallNumber(), 5,
        cnBrowseResult(callNumbers.get(47).fullCallNumber(), callNumbers.get(32).fullCallNumber(), 100, List.of(
          cnBrowseItem(callNumbers.get(47), 1),
          cnBrowseItem(callNumbers.get(62), 1),
          cnBrowseItem(callNumbers.get(67), 1),
          cnBrowseItem(callNumbers.get(55), 1),
          cnBrowseItem(callNumbers.get(32), 1)
        ))),

      // forward browsing from the end of the result set
      arguments(9, forwardQuery, BrowseOptionType.ALL, callNumbers.get(11).fullCallNumber(), 5,
        cnBrowseResult(null, null, 100, emptyList())),

      // backward browsing from the middle of the result set
      arguments(10, backwardQuery, BrowseOptionType.ALL, callNumbers.get(22).fullCallNumber(), 5,
        cnBrowseResult(callNumbers.get(92).fullCallNumber(), callNumbers.get(90).fullCallNumber(), 100, List.of(
          cnBrowseItem(callNumbers.get(92), 1),
          cnBrowseItem(callNumbers.get(17), 1),
          cnBrowseItem(callNumbers.get(27), 1),
          cnBrowseItem(callNumbers.get(42), 1),
          cnBrowseItem(callNumbers.get(90), 1)
        ))),

      // backward browsing from the end of the result set
      arguments(11, backwardQuery, BrowseOptionType.ALL, callNumbers.get(50).fullCallNumber(), 5,
        cnBrowseResult(null, null, 100, emptyList()))
    );
  }

  private static void updateLcConfig(List<UUID> typeIds) {
    var config = new BrowseConfig()
      .id(BrowseOptionType.LC)
      .shelvingAlgorithm(ShelvingOrderAlgorithmType.LC)
      .typeIds(typeIds);

    var stub = mockCallNumberTypes(okapi.wireMockServer(), typeIds.toArray(new UUID[0]));
    doPut(browseConfigPath(BrowseType.CALL_NUMBER, BrowseOptionType.LC), config);
    okapi.wireMockServer().removeStub(stub);
  }

  private static CallNumberBrowseResult cnBrowseResult(String prev, String next, int total,
                                                       List<CallNumberBrowseItem> items) {
    return new CallNumberBrowseResult().prev(prev).next(next).items(items).totalRecords(total);
  }

  private static CallNumberBrowseItem cnEmptyBrowseItem(String callNumber) {
    return new CallNumberBrowseItem().fullCallNumber(callNumber).isAnchor(true).totalRecords(0);
  }

  private static CallNumberBrowseItem cnBrowseItem(CallNumberResource resource, int count) {
    return cnBrowseItem(resource, count, null);
  }

  private static CallNumberBrowseItem cnBrowseItem(CallNumberResource resource, int count, Boolean isAnchor) {
    return new CallNumberBrowseItem()
      .fullCallNumber(resource.fullCallNumber())
      .callNumber(resource.callNumber())
      .callNumberPrefix(resource.callNumberPrefix())
      .callNumberSuffix(resource.callNumberSuffix())
      .callNumberTypeId(resource.callNumberTypeId())
      .volume(resource.volume())
      .chronology(resource.chronology())
      .enumeration(resource.enumeration())
      .copyNumber(resource.copyNumber())
      .totalRecords(count)
      .isAnchor(isAnchor);
  }

}
