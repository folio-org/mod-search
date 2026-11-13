package org.folio.api.browse;

import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.RecordType.CALL_NUMBERS;
import static org.folio.search.utils.SearchUtils.ALL_RECORDS_QUERY;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER2_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceCallNumberBrowsePath;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.sample.SampleInstances.getSemanticWeb;
import static org.folio.support.testdata.ConsortiumTestDataManager.MEMBER2_LOCATION;
import static org.folio.support.testdata.ConsortiumTestDataManager.callNumberInstances;
import static org.folio.support.utils.CallNumberTestData.CallNumberTestDataRecord;
import static org.folio.support.utils.CallNumberTestData.CallNumberTypeId.LC;
import static org.folio.support.utils.CallNumberTestData.callNumbers;
import static org.folio.support.utils.CallNumberTestData.cnEmptyBrowseItem;
import static org.folio.support.utils.CallNumberTestData.locations;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.facet;
import static org.folio.support.utils.TestUtils.facetItem;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.domain.dto.CallNumberBrowseResult;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.model.index.CallNumberResource;
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
  private static final List<Instance> INSTANCES = callNumberInstances();
  private static final String SEMANTIC_WEB_CALL_NUMBER_TYPE_ID = "512173a7-bd09-490e-b773-17d83f2b63fe";

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
  void browseByCallNumber_withLocationFilter() {
    var request = get(instanceCallNumberBrowsePath(BrowseOptionType.ALL))
      .param("expandAll", "true")
      .param(QUERY_PARAM, "(fullCallNumber>=\"a\" or fullCallNumber<\"a\") "
                          + "and instances.locationId==(\"%s\")".formatted(MEMBER2_LOCATION))
      .param(LIMIT_PARAM, String.valueOf(10));
    var actual = parseResponse(doGet(request, MEMBER_TENANT_ID), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(cnBrowseResult(null, null, 0, List.of(cnEmptyBrowseItem("a"))));
  }

  @Test
  void browseByCallNumber_fromCentralTenant_withTenantIdFacet_sameCallNumberInMemberTenantOfSharedInstance() {
    var request = get(instanceCallNumberBrowsePath(BrowseOptionType.ALL))
      .param("expandAll", "true")
      .param(QUERY_PARAM, "(fullCallNumber>=\"TA357 .A78 2010\" or fullCallNumber<\"TA357 .A78 2010\") "
                          + "and instances.tenantId==(\"%s\")".formatted(MEMBER2_TENANT_ID))
      .param(LIMIT_PARAM, String.valueOf(10));
    var actual = parseResponse(doGet(request, CENTRAL_TENANT_ID), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(cnBrowseResult(null, null, 2,
      List.of(cnBrowseItem(callNumbers().get(1).callNumber(), 0),
        cnBrowseItem(callNumbers().getFirst().callNumber(), 0, 1, true))));
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

    var callNumbers = callNumbers().stream()
      .map(CallNumberTestDataRecord::callNumber)
      .collect(Collectors.toMap(callNumberResource -> Integer.parseInt(callNumberResource.id()), identity()));

    return Stream.of(
      arguments(aroundQuery, CENTRAL_TENANT_ID, BrowseOptionType.ALL, callNumbers.get(1).fullCallNumber(), 5,
        cnBrowseResult(callNumbers.get(35).fullCallNumber(), callNumbers.get(43).fullCallNumber(), 60, List.of(
          cnBrowseItem(callNumbers.get(35), 18),
          cnBrowseItem(callNumbers.get(25), 15),
          cnBrowseItem(callNumbers.get(1), 0, 3, true),
          cnBrowseItem(callNumbers.get(33), 17),
          cnBrowseItem(callNumbers.get(43), 23)
        ))),

      arguments(aroundQuery, MEMBER_TENANT_ID, BrowseOptionType.ALL, callNumbers.get(1).fullCallNumber(), 5,
        cnBrowseResult(callNumbers.get(91).fullCallNumber(), semanticWebCallNumber(90000), 103, List.of(
          cnBrowseItem(callNumbers.get(91), 45),
          cnBrowseItem(callNumbers.get(25), 15),
          cnBrowseItem(callNumbers.get(1), 0, 3, true),
          semanticWebCnBrowseItem(10101),
          semanticWebCnBrowseItem(90000)
        )))
    );
  }

  private static CallNumberBrowseResult cnBrowseResult(String prev, String next, int total,
                                                       List<CallNumberBrowseItem> items) {
    return new CallNumberBrowseResult().prev(prev).next(next).items(items).totalRecords(total);
  }

  private static CallNumberBrowseItem cnBrowseItem(CallNumberResource resource, int instanceIndex) {
    return cnBrowseItem(resource, instanceIndex, 1, null);
  }

  private static CallNumberBrowseItem cnBrowseItem(CallNumberResource resource, int instanceIndex, int count,
                                                   Boolean isAnchor) {
    return new CallNumberBrowseItem()
      .fullCallNumber(resource.fullCallNumber())
      .callNumber(resource.callNumber())
      .callNumberPrefix(resource.callNumberPrefix())
      .callNumberSuffix(resource.callNumberSuffix())
      .callNumberTypeId(resource.callNumberTypeId())
      .instanceTitle(count == 1 ? INSTANCES.get(instanceIndex).getTitle() : null)
      .totalRecords(count)
      .isAnchor(isAnchor);
  }

  private static Stream<Arguments> facetQueriesProvider() {
    var locations = locations();
    return Stream.of(
      arguments(CENTRAL_TENANT_ID, ALL_RECORDS_QUERY, array(LOCATION_FACET),
        expectedLocationFacet(locations, 27, 20, 13)),
      arguments(CENTRAL_TENANT_ID, ALL_RECORDS_QUERY, array(TENANT_FACET), mapOf(TENANT_FACET,
        facet(facetItem(CENTRAL_TENANT_ID, 60), facetItem(MEMBER_TENANT_ID, 2), facetItem(MEMBER2_TENANT_ID, 2)))),
      arguments(CENTRAL_TENANT_ID, "instances.tenantId==" + MEMBER2_TENANT_ID, array(TENANT_FACET), mapOf(TENANT_FACET,
        facet(facetItem(CENTRAL_TENANT_ID, 60), facetItem(MEMBER_TENANT_ID, 2), facetItem(MEMBER2_TENANT_ID, 2)))),
      arguments(CENTRAL_TENANT_ID, "callNumberTypeId=\"" + LC.getId() + "\"", array(LOCATION_FACET),
        expectedLocationFacet(locations, 6, 4, 2)),
      arguments(MEMBER_TENANT_ID, ALL_RECORDS_QUERY, array(LOCATION_FACET),
        expectedMemberLocationFacet(locations, 42, 34, 24)),
      arguments(MEMBER_TENANT_ID, ALL_RECORDS_QUERY, array(TENANT_FACET), mapOf(TENANT_FACET,
        facet(facetItem(CENTRAL_TENANT_ID, 60), facetItem(MEMBER_TENANT_ID, 45),
          facetItem(MEMBER2_TENANT_ID, 3)))),
      arguments(MEMBER_TENANT_ID, "instances.shared==false", array(LOCATION_FACET),
        expectedMemberLocationFacet(locations, 15, 14, 11)),
      arguments(MEMBER_TENANT_ID, "instances.shared==false", array(TENANT_FACET), mapOf(TENANT_FACET,
        facet(facetItem(MEMBER_TENANT_ID, 43), facetItem(MEMBER2_TENANT_ID, 1)))),
      arguments(MEMBER_TENANT_ID, "instances.locationId==" + MEMBER2_LOCATION, array(SHARED_FACET, LOCATION_FACET),
        mapOf(SHARED_FACET, facet(facetItem("false", 1))),
        mapOf(LOCATION_FACET, expectedMemberLocationFacet(locations, 42, 34, 24)))
    );
  }

  private static Map<String, Facet> expectedLocationFacet(Map<Integer, String> locations,
                                                          int totalRecords1, int totalRecords2, int totalRecords3) {
    return mapOf(LOCATION_FACET, locationFacet(locations, totalRecords1, totalRecords2, totalRecords3));
  }

  /**
   * Location facet as seen from the member tenant: besides the common locations it contains the member2 location
   * and the item locations of the semantic web instance, which is local to the member tenant.
   */
  private static Map<String, Facet> expectedMemberLocationFacet(Map<Integer, String> locations,
                                                                int totalRecords1, int totalRecords2,
                                                                int totalRecords3) {
    var facet = locationFacet(locations, totalRecords1, totalRecords2, totalRecords3);
    facet.addValuesItem(facetItem(MEMBER2_LOCATION, 1));
    getSemanticWeb().getItems().stream()
      .collect(Collectors.groupingBy(Item::getEffectiveLocationId, Collectors.counting()))
      .forEach((locationId, count) -> facet.addValuesItem(facetItem(locationId, count.intValue())));
    return mapOf(LOCATION_FACET, facet);
  }

  private static Facet locationFacet(Map<Integer, String> locations,
                                     int totalRecords1, int totalRecords2, int totalRecords3) {
    return facet(
      facetItem(locations.get(1), totalRecords1),
      facetItem(locations.get(2), totalRecords2),
      facetItem(locations.get(3), totalRecords3)
    );
  }

  private static String semanticWebCallNumber(int number) {
    return "TK5105.88815 . A58 2004 FT MEADE suffix-" + number;
  }

  private static CallNumberBrowseItem semanticWebCnBrowseItem(int number) {
    return new CallNumberBrowseItem()
      .fullCallNumber(semanticWebCallNumber(number))
      .callNumber("TK5105.88815 . A58 2004 FT MEADE")
      .callNumberPrefix("prefix-" + number)
      .callNumberSuffix("suffix-" + number)
      .callNumberTypeId(SEMANTIC_WEB_CALL_NUMBER_TYPE_ID)
      .instanceTitle(getSemanticWeb().getTitle())
      .totalRecords(1);
  }
}
