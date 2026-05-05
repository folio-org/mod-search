package org.folio.api.browse;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.base.ApiEndpoints.browseConfigPath;
import static org.folio.support.base.ApiEndpoints.instanceCallNumberBrowsePath;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.utils.CallNumberTestData.CallNumberTypeId.LC;
import static org.folio.support.utils.CallNumberTestData.CallNumberTypeId.SUDOC;
import static org.folio.support.utils.CallNumberTestData.callNumbers;
import static org.folio.support.utils.CallNumberTestData.cnBrowseItem;
import static org.folio.support.utils.CallNumberTestData.cnBrowseResult;
import static org.folio.support.utils.CallNumberTestData.cnEmptyBrowseItem;
import static org.folio.support.utils.CallNumberTestData.locations;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.facet;
import static org.folio.support.utils.TestUtils.facetItem;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.mockCallNumberTypes;
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
import org.folio.search.domain.dto.CallNumberBrowseResult;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.RecordType;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.model.index.CallNumberResource;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.TestRailCase;
import org.folio.support.base.BaseIntegrationTest;
import org.folio.support.utils.CallNumberTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@IntegrationTest
public abstract class BrowseCallNumberIT extends BaseIntegrationTest {

  public static final Instance[] INSTANCES = CallNumberTestData.instances().toArray(new Instance[0]);

  @BeforeEach
  void setUp() {
    updateLcConfig(List.of(UUID.fromString(LC.getId())));
    updateSudocConfig(List.of(UUID.fromString(SUDOC.getId())));
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

  /**
   * Only call numbers of the type(s) configured for a browse option appear as exact matches.
   * Non-configured types (LC, DEWEY, NLM, OTHER) must NOT produce an exact match when browsing
   * with the SUDOC option; only SUDOC-typed call numbers should.
   */
  @Test
  @TestRailCase(627509)
  void browseByCallNumber_sudocOption_onlyConfiguredTypesReturnExactMatch() {
    var cnByNum = createCallNumberLookup();

    // Non-configured types (IDs 1=LC, 2=DEWEY, 3=NLM, 5=OTHER) must NOT produce an exact match
    for (var cnId : List.of(1, 2, 3, 5)) {
      var cn = cnByNum.get(cnId);
      assertThat(browse(cn.fullCallNumber(), BrowseOptionType.SUDOC).getItems())
        .as("Expected no exact match for non-SUDOC call number '%s' (id=%d)", cn.callNumber(), cnId)
        .anySatisfy(item -> assertThat(item).isEqualTo(cnEmptyBrowseItem(cn.fullCallNumber())));
    }

    // ID 4 = "Y 10.13:980" (SUDOC type) SHOULD produce an exact match
    var sudocCn = cnByNum.get(4);
    assertThat(browse(sudocCn.fullCallNumber(), BrowseOptionType.SUDOC).getItems())
      .as("Expected exact match for SUDOC call number '%s'", sudocCn.callNumber())
      .anySatisfy(item -> {
        assertThat(item.getFullCallNumber()).isEqualTo(sudocCn.fullCallNumber());
        assertThat(item.getIsAnchor()).isTrue();
        assertThat(item.getTotalRecords()).isGreaterThan(0);
      });
  }

  /**
   * When the LC browse config has no configured call number types (empty typeIds),
   * call numbers of every type should produce an exact match when browsing with the LC option.
   */
  @Test
  @TestRailCase(627500)
  void browseByCallNumber_lcOption_emptyConfig_allTypesReturnExactMatch() {
    updateLcConfig(emptyList());

    var cnByNum = createCallNumberLookup();

    // IDs 1=LC, 2=DEWEY, 3=NLM, 4=SUDOC, 5=OTHER
    for (var cnId : List.of(1, 2, 3, 4, 5)) {
      var cn = cnByNum.get(cnId);
      assertThat(browse(cn.fullCallNumber(), BrowseOptionType.LC).getItems())
        .as("Expected exact match for '%s' (id=%d, type=%s) with empty LC config",
          cn.callNumber(), cnId, cn.callNumberTypeId())
        .anySatisfy(item -> {
          assertThat(item.getFullCallNumber()).isEqualTo(cn.fullCallNumber());
          assertThat(item.getIsAnchor()).isTrue();
          assertThat(item.getTotalRecords()).isGreaterThan(0);
        });
    }
  }

  private static Map<Integer, CallNumberResource> createCallNumberLookup() {
    return callNumbers().stream()
      .map(CallNumberTestData.CallNumberTestDataRecord::callNumber)
      .collect(Collectors.toMap(cn -> Integer.parseInt(cn.id()), identity()));
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

  @SuppressWarnings("checkstyle:MethodLength")
  private static Stream<Arguments> callNumberBrowsingDataProvider() {
    var aroundQuery = "fullCallNumber >= {value} or fullCallNumber < {value}";
    var forwardQuery = "fullCallNumber > {value}";
    var backwardQuery = "fullCallNumber < {value}";

    var callNumbers = createCallNumberLookup();

    return Stream.of(
      // anchor call number appears in the middle of the result set
      arguments(1, aroundQuery, BrowseOptionType.ALL, callNumbers.get(1).fullCallNumber(), 5,
        cnBrowseResult(callNumbers.get(91).fullCallNumber(), callNumbers.get(68).fullCallNumber(), 100, List.of(
          cnBrowseItem(callNumbers.get(91), 1, INSTANCES[45].getTitle()),
          cnBrowseItem(callNumbers.get(25), 1, INSTANCES[15].getTitle()),
          cnBrowseItem(callNumbers.get(1), 3, null, true),
          cnBrowseItem(callNumbers.get(70), 1, INSTANCES[34].getTitle()),
          cnBrowseItem(callNumbers.get(68), 1, INSTANCES[34].getTitle())
        ))),

      // not existed anchor call number appears in the middle of the result set
      arguments(2, aroundQuery, BrowseOptionType.ALL, "TA357 .A78 2011", 5,
        cnBrowseResult(callNumbers.get(25).fullCallNumber(), callNumbers.get(68).fullCallNumber(), 100, List.of(
          cnBrowseItem(callNumbers.get(25), 1, INSTANCES[15].getTitle()),
          cnBrowseItem(callNumbers.get(1), 3, null),
          cnEmptyBrowseItem("TA357 .A78 2011"),
          cnBrowseItem(callNumbers.get(70), 1, INSTANCES[34].getTitle()),
          cnBrowseItem(callNumbers.get(68), 1, INSTANCES[34].getTitle())
        ))),

      // anchor call number appears first in the result set
      arguments(3, aroundQuery, BrowseOptionType.ALL, callNumbers.get(50).fullCallNumber(), 5,
        cnBrowseResult(null, callNumbers.get(40).fullCallNumber(), 100, List.of(
          cnBrowseItem(callNumbers.get(50), 1, INSTANCES[26].getTitle(), true),
          cnBrowseItem(callNumbers.get(97), 1, INSTANCES[48].getTitle()),
          cnBrowseItem(callNumbers.get(40), 1, INSTANCES[19].getTitle())
        ))),

      // not existed anchor call number appears first in the result set
      arguments(4, aroundQuery, BrowseOptionType.ALL, "0.0", 5,
        cnBrowseResult(null, callNumbers.get(97).fullCallNumber(), 100, List.of(
          cnEmptyBrowseItem("0.0"),
          cnBrowseItem(callNumbers.get(50), 1, INSTANCES[26].getTitle()),
          cnBrowseItem(callNumbers.get(97), 1, INSTANCES[48].getTitle())
        ))),

      // anchor call number appears last in the result set
      arguments(5, aroundQuery, BrowseOptionType.ALL, callNumbers.get(11).fullCallNumber(), 5,
        cnBrowseResult(callNumbers.get(49).fullCallNumber(), null, 100, List.of(
          cnBrowseItem(callNumbers.get(49), 1, INSTANCES[26].getTitle()),
          cnBrowseItem(callNumbers.get(44), 1, INSTANCES[24].getTitle()),
          cnBrowseItem(callNumbers.get(11), 1, INSTANCES[5].getTitle(), true)
        ))),

      // not existed anchor call number appears last in the result set
      arguments(6, aroundQuery, BrowseOptionType.ALL, "ZZ", 5,
        cnBrowseResult(callNumbers.get(44).fullCallNumber(), null, 100, List.of(
          cnBrowseItem(callNumbers.get(44), 1, INSTANCES[24].getTitle()),
          cnBrowseItem(callNumbers.get(11), 1, INSTANCES[5].getTitle()),
          cnEmptyBrowseItem("ZZ")
        ))),

      // anchor call number appears in the middle of the result set when filtering by type
      arguments(7, aroundQuery, BrowseOptionType.LC, callNumbers.get(46).fullCallNumber(), 5,
        cnBrowseResult(callNumbers.get(66).fullCallNumber(), callNumbers.get(21).fullCallNumber(), 20, List.of(
          cnBrowseItem(callNumbers.get(66), 1, INSTANCES[32].getTitle()),
          cnBrowseItem(callNumbers.get(96), 1, INSTANCES[47].getTitle()),
          cnBrowseItem(callNumbers.get(46), 1, INSTANCES[25].getTitle(), true),
          cnBrowseItem(callNumbers.get(86), 1, INSTANCES[42].getTitle()),
          cnBrowseItem(callNumbers.get(21), 2, null)
        ))),

      // call number with backslashes
      arguments(7, aroundQuery, BrowseOptionType.ALL, "BR\\\\140 .J\\\\\\\\86", 5,
        cnBrowseResult(callNumbers.get(76).fullCallNumber(), callNumbers.get(80).fullCallNumber(), 100, List.of(
          cnBrowseItem(callNumbers.get(76), 1, INSTANCES[36].getTitle()),
          cnBrowseItem(callNumbers.get(15), 1, INSTANCES[8].getTitle()),
          cnBrowseItem(callNumbers.get(95), 1, INSTANCES[47].getTitle(), true),
          cnBrowseItem(callNumbers.get(20), 1, INSTANCES[11].getTitle()),
          cnBrowseItem(callNumbers.get(80), 1, INSTANCES[39].getTitle())
        ))),

      // forward browsing from the middle of the result set
      arguments(8, forwardQuery, BrowseOptionType.ALL, callNumbers.get(22).fullCallNumber(), 5,
        cnBrowseResult(callNumbers.get(47).fullCallNumber(), callNumbers.get(32).fullCallNumber(), 100, List.of(
          cnBrowseItem(callNumbers.get(47), 1, INSTANCES[26].getTitle()),
          cnBrowseItem(callNumbers.get(62), 1, INSTANCES[30].getTitle()),
          cnBrowseItem(callNumbers.get(67), 1, INSTANCES[33].getTitle()),
          cnBrowseItem(callNumbers.get(55), 1, INSTANCES[28].getTitle()),
          cnBrowseItem(callNumbers.get(32), 1, INSTANCES[16].getTitle())
        ))),

      // forward browsing from the end of the result set
      arguments(9, forwardQuery, BrowseOptionType.ALL, callNumbers.get(11).fullCallNumber(), 5,
        cnBrowseResult(null, null, 100, emptyList())),

      // backward browsing from the middle of the result set
      arguments(10, backwardQuery, BrowseOptionType.ALL, callNumbers.get(22).fullCallNumber(), 5,
        cnBrowseResult(callNumbers.get(92).fullCallNumber(), callNumbers.get(90).fullCallNumber(), 100, List.of(
          cnBrowseItem(callNumbers.get(92), 1, INSTANCES[45].getTitle()),
          cnBrowseItem(callNumbers.get(17), 1, INSTANCES[10].getTitle()),
          cnBrowseItem(callNumbers.get(27), 1, INSTANCES[15].getTitle()),
          cnBrowseItem(callNumbers.get(42), 1, INSTANCES[22].getTitle()),
          cnBrowseItem(callNumbers.get(90), 1, INSTANCES[44].getTitle())
        ))),

      // backward browsing from the end of the result set
      arguments(11, backwardQuery, BrowseOptionType.ALL, callNumbers.get(50).fullCallNumber(), 5,
        cnBrowseResult(null, null, 100, emptyList()))
    );
  }

  private CallNumberBrowseResult browse(String fullCallNumber, BrowseOptionType browseOptionType) {
    var query = "fullCallNumber >= {value} or fullCallNumber < {value}";
    var request = get(instanceCallNumberBrowsePath(browseOptionType))
      .param("expandAll", "true")
      .param("query", prepareQuery(query, '"' + fullCallNumber + '"'))
      .param("limit", "5");
    return parseResponse(doGet(request), CallNumberBrowseResult.class);
  }

  private static void updateSudocConfig(List<UUID> typeIds) {
    updateCnConfig(typeIds, BrowseOptionType.SUDOC, ShelvingOrderAlgorithmType.SUDOC);
  }

  private static void updateLcConfig(List<UUID> typeIds) {
    updateCnConfig(typeIds, BrowseOptionType.LC, ShelvingOrderAlgorithmType.LC);
  }

  private static void updateCnConfig(List<UUID> typeIds, BrowseOptionType browseOptionType,
                                     ShelvingOrderAlgorithmType algorithmType) {
    var config = new BrowseConfig()
      .id(browseOptionType)
      .shelvingAlgorithm(algorithmType)
      .typeIds(typeIds);

    var stub = mockCallNumberTypes(okapi.wireMockServer(), typeIds.toArray(new UUID[0]));
    doPut(browseConfigPath(BrowseType.INSTANCE_CALL_NUMBER, browseOptionType), config);
    okapi.wireMockServer().removeStub(stub);
  }
}
