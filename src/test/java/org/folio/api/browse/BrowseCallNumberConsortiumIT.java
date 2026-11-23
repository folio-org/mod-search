package org.folio.api.browse;

import static org.apache.commons.collections4.MapUtils.getString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.RecordType.CALL_NUMBERS;
import static org.folio.search.utils.SearchUtils.ALL_RECORDS_QUERY;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER2_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceCallNumberBrowsePath;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.sample.SampleInstances.getSemanticWeb;
import static org.folio.support.testdata.ConsortiumTestDataManager.CALL_NUMBER_LC_TYPE_ID;
import static org.folio.support.testdata.ConsortiumTestDataManager.LOCAL_INSTANCE_ID;
import static org.folio.support.testdata.ConsortiumTestDataManager.MEMBER2_LOCATION;
import static org.folio.support.testdata.ConsortiumTestDataManager.SHARED_INSTANCE_ID;
import static org.folio.support.testdata.ConsortiumTestDataManager.itemsOfInstance;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.cnBrowseItem;
import static org.folio.support.utils.TestUtils.cnBrowseResult;
import static org.folio.support.utils.TestUtils.cnEmptyBrowseItem;
import static org.folio.support.utils.TestUtils.facet;
import static org.folio.support.utils.TestUtils.facetItem;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.CallNumberBrowseResult;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetItem;
import org.folio.search.domain.dto.FacetResult;
import org.folio.support.base.BaseConsortiumIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class BrowseCallNumberConsortiumIT extends BaseConsortiumIntegrationTest {

  private static final String LOCATION_FACET = "instances.locationId";
  private static final String TENANT_FACET = "instances.tenantId";
  private static final String SHARED_FACET = "instances.shared";

  // call number types of the call number browse fixtures
  private static final String DEWEY_TYPE_ID = "0b5d15ad-1738-4e9e-a9c1-e972e95ce71c";
  private static final String NLM_TYPE_ID = "530b84ea-4965-4cda-9d9e-6a5ef91fd21e";
  private static final String OTHER_TYPE_ID = "cf74a451-41ad-49aa-aa2b-21d2d2f2e235";

  // item locations of the call number browse fixtures
  private static final String SPECIAL_COLLECTIONS_LOCATION = "65b6c2e9-8a7b-4a10-9b5d-ba1cf0313cd7";
  private static final String RARE_BOOKS_LOCATION = "b777f3a4-4372-4792-a87d-8e8f177eab10";
  private static final String ANNEX_STACKS_LOCATION = "0d106980-1789-42ac-b355-a6c7a74ddea3";

  private static final String ANCHOR_CALL_NUMBER = "TA357 .A78 2010";

  @MethodSource("callNumberBrowsingDataProvider")
  @DisplayName("browseByCallNumber_parameterized")
  @ParameterizedTest(name = "[{index}] query={0} tenant={1}, option={2}, value=''{3}'', limit={4}")
  void browseByCallNumber_parameterized(String query, String tenant, BrowseOptionType optionType, String input,
                                        Integer limit, CallNumberBrowseResult expected) {
    var request = get(instanceCallNumberBrowsePath(optionType))
      .param("expandAll", "true")
      .param(QUERY_PARAM, prepareQuery(query, '"' + input + '"'))
      .param(LIMIT_PARAM, String.valueOf(limit));
    var actual = parseResponse(doGet(request, tenant), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void browseByCallNumber_forward_fromMemberTenant_returnsLocalInstanceCallNumbers() {
    var request = get(instanceCallNumberBrowsePath(BrowseOptionType.ALL))
      .param("expandAll", "true")
      .param(QUERY_PARAM, prepareQuery("fullCallNumber >= {value}", "\"Twilight Breaking pt. 1\""))
      .param(LIMIT_PARAM, "2");
    var actual = parseResponse(doGet(request, MEMBER_TENANT_ID), CallNumberBrowseResult.class);
    assertThat(actual.getItems())
      .containsExactly(
        cnBrowseItem("Twilight Breaking", "DVD", "pt. 1", OTHER_TYPE_ID, 1, "Instance 35"),
        cnBrowseItem("WA 900.AA1 I55h 1996", NLM_TYPE_ID, 1, "Instance 35"));
  }

  @Test
  void browseByCallNumber_withLocationFilter() {
    var request = get(instanceCallNumberBrowsePath(BrowseOptionType.ALL))
      .param("expandAll", "true")
      .param(QUERY_PARAM, "(fullCallNumber>=\"a\" or fullCallNumber<\"a\") "
                          + "and instances.locationId==(\"%s\")".formatted(MEMBER2_LOCATION))
      .param(LIMIT_PARAM, String.valueOf(10));
    var actual = parseResponse(doGet(request, MEMBER_TENANT_ID), CallNumberBrowseResult.class);
    assertThat(actual)
      .isEqualTo(cnBrowseResult(null, null, 0, List.of(cnEmptyBrowseItem("a"))));
  }

  @Test
  void browseByCallNumber_fromCentralTenant_withTenantIdFacet_sameCallNumberInMemberTenantOfSharedInstance() {
    var request = get(instanceCallNumberBrowsePath(BrowseOptionType.ALL))
      .param("expandAll", "true")
      .param(QUERY_PARAM, "(fullCallNumber>=\"%1$s\" or fullCallNumber<\"%1$s\") ".formatted(ANCHOR_CALL_NUMBER)
                          + "and instances.tenantId==(\"%s\")".formatted(MEMBER2_TENANT_ID))
      .param(LIMIT_PARAM, String.valueOf(10));
    var actual = parseResponse(doGet(request, CENTRAL_TENANT_ID), CallNumberBrowseResult.class);
    // member2 also owns an item of the shared slice instance, so its call number is listed as well
    assertThat(actual).isEqualTo(cnBrowseResult(null, null, 3, List.of(
      cnBrowseItem("664.051 4", DEWEY_TYPE_ID, 1, "Instance 1"),
      cnBrowseItem(ANCHOR_CALL_NUMBER, CALL_NUMBER_LC_TYPE_ID, 1, "Instance 1", true),
      cnBrowseItem("Z665.2.U6 V37 2023", "95467209-6d7b-468b-94df-0f5d7ad2747d", 1,
        "Sharing collections : a guide to consortial cataloging"))));
  }

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] tenant={0} query={1}, facets={2}")
  @DisplayName("getFacetsForCallNumbers_ecs_parameterized")
  void getFacetsForCallNumbers_positive(String tenantId, String query, String[] facets, Map<String, Facet> expected) {
    var actual = parseResponse(doGet(recordFacetsPath(CALL_NUMBERS, query, facets), tenantId), FacetResult.class);

    expected.forEach((facetName, expectedFacet) -> {
      assertNotNull(actual.getFacets());
      var actualFacet = actual.getFacets().get(facetName);

      assertThat(actualFacet).isNotNull();
      assertThat(actualFacet.getValues())
        .containsExactlyInAnyOrderElementsOf(expectedFacet.getValues());
    });
  }

  private static Stream<Arguments> callNumberBrowsingDataProvider() {
    var aroundQuery = "fullCallNumber >= {value} or fullCallNumber < {value}";

    return Stream.of(
      arguments(aroundQuery, CENTRAL_TENANT_ID, BrowseOptionType.ALL, ANCHOR_CALL_NUMBER, 5,
        cnBrowseResult("Rowling", "WC 450 R47 2012", 61, List.of(
          cnBrowseItem("Rowling", "jFICTION", null, OTHER_TYPE_ID, 1, "Instance 19"),
          cnBrowseItem("SYLY-12", OTHER_TYPE_ID, 1, "Instance 16"),
          cnBrowseItem(ANCHOR_CALL_NUMBER, CALL_NUMBER_LC_TYPE_ID, 3, null, true),
          cnBrowseItem("WB 100 M4891", NLM_TYPE_ID, 1, "Instance 18"),
          cnBrowseItem("WC 450 R47 2012", NLM_TYPE_ID, 1, "Instance 24")
        ))),

      arguments(aroundQuery, MEMBER_TENANT_ID, BrowseOptionType.ALL, ANCHOR_CALL_NUMBER, 5,
        cnBrowseResult("SDD 66727", "TK5105.88815 . A58 2004 FT MEADE suffix-90000", 105, List.of(
          cnBrowseItem("SDD 66727", CALL_NUMBER_LC_TYPE_ID, 1, "Instance 46"),
          cnBrowseItem("SYLY-12", OTHER_TYPE_ID, 1, "Instance 16"),
          cnBrowseItem(ANCHOR_CALL_NUMBER, CALL_NUMBER_LC_TYPE_ID, 3, null, true),
          cnBrowseItem("TK5105.88815 . A58 2004 FT MEADE", "prefix-10101", "suffix-10101",
            "512173a7-bd09-490e-b773-17d83f2b63fe", 1, "A sem\\ntic web primer :0747-0850 & wolves"),
          cnBrowseItem("TK5105.88815 . A58 2004 FT MEADE", "prefix-90000", "suffix-90000",
            "512173a7-bd09-490e-b773-17d83f2b63fe", 1, "A sem\\ntic web primer :0747-0850 & wolves")
        )))
    );
  }

  private static Stream<Arguments> facetQueriesProvider() {
    return Stream.of(
      arguments(CENTRAL_TENANT_ID, ALL_RECORDS_QUERY, array(LOCATION_FACET),
        new ExpectedLocations(27, 20, 13).fixtureCallNumber(SHARED_INSTANCE_ID).toFacets()),
      arguments(CENTRAL_TENANT_ID, ALL_RECORDS_QUERY, array(TENANT_FACET), mapOf(TENANT_FACET,
        facet(facetItem(CENTRAL_TENANT_ID, 61), facetItem(MEMBER_TENANT_ID, 3), facetItem(MEMBER2_TENANT_ID, 3)))),
      arguments(CENTRAL_TENANT_ID, "instances.tenantId==" + MEMBER2_TENANT_ID, array(TENANT_FACET), mapOf(TENANT_FACET,
        facet(facetItem(CENTRAL_TENANT_ID, 61), facetItem(MEMBER_TENANT_ID, 3), facetItem(MEMBER2_TENANT_ID, 3)))),
      arguments(CENTRAL_TENANT_ID, "callNumberTypeId=\"" + CALL_NUMBER_LC_TYPE_ID + "\"", array(LOCATION_FACET),
        new ExpectedLocations(6, 4, 2).toFacets()),
      arguments(MEMBER_TENANT_ID, ALL_RECORDS_QUERY, array(LOCATION_FACET), allMemberLocationFacets()),
      arguments(MEMBER_TENANT_ID, ALL_RECORDS_QUERY, array(TENANT_FACET), mapOf(TENANT_FACET,
        facet(facetItem(CENTRAL_TENANT_ID, 61), facetItem(MEMBER_TENANT_ID, 47),
          facetItem(MEMBER2_TENANT_ID, 4)))),
      arguments(MEMBER_TENANT_ID, "instances.shared==false", array(LOCATION_FACET),
        new ExpectedLocations(15, 14, 11).memberLocalRecords().fixtureCallNumber(LOCAL_INSTANCE_ID).toFacets()),
      arguments(MEMBER_TENANT_ID, "instances.shared==false", array(TENANT_FACET), mapOf(TENANT_FACET,
        facet(facetItem(MEMBER_TENANT_ID, 44), facetItem(MEMBER2_TENANT_ID, 1)))),
      arguments(MEMBER_TENANT_ID, "instances.locationId==" + MEMBER2_LOCATION, array(SHARED_FACET, LOCATION_FACET),
        mapOf(SHARED_FACET, facet(facetItem("false", 1)), LOCATION_FACET,
          allMemberLocationFacets().get(LOCATION_FACET)))
    );
  }

  /**
   * Location facet of all call numbers visible from the member tenant (the facet ignores its own filter).
   */
  private static Map<String, Facet> allMemberLocationFacets() {
    return new ExpectedLocations(42, 34, 24).memberLocalRecords()
      .fixtureCallNumber(SHARED_INSTANCE_ID).fixtureCallNumber(LOCAL_INSTANCE_ID).toFacets();
  }

  /**
   * Expected values of the call number location facet: number of call number records per location.
   */
  private static final class ExpectedLocations {

    private final Map<String, Integer> counts = new LinkedHashMap<>();

    /**
     * Starts with the three locations of the call number browse fixtures.
     */
    private ExpectedLocations(int specialCollections, int rareBooks, int annexStacks) {
      add(SPECIAL_COLLECTIONS_LOCATION, specialCollections);
      add(RARE_BOOKS_LOCATION, rareBooks);
      add(ANNEX_STACKS_LOCATION, annexStacks);
    }

    /**
     * Adds the records only visible from the member tenant: the member2 location and the item locations of the
     * semantic web instance, which is local to the member tenant.
     */
    private ExpectedLocations memberLocalRecords() {
      add(MEMBER2_LOCATION, 1);
      getSemanticWeb().getItems().forEach(item -> add(item.getEffectiveLocationId(), 1));
      return this;
    }

    /**
     * Adds the call number of a consortium search slice instance: all its items share one call number, so it is one
     * record, counted once for every location that holds its items.
     */
    private ExpectedLocations fixtureCallNumber(String instanceId) {
      itemsOfInstance(instanceId).stream().map(item -> getString(item.inventoryRecord(), "effectiveLocationId"))
        .distinct().forEach(locationId -> add(locationId, 1));
      return this;
    }

    private void add(String locationId, int count) {
      counts.merge(locationId, count, Integer::sum);
    }

    private Map<String, Facet> toFacets() {
      return mapOf(LOCATION_FACET, facet(counts.entrySet().stream()
        .map(entry -> facetItem(entry.getKey(), entry.getValue()))
        .toArray(FacetItem[]::new)));
    }
  }
}
