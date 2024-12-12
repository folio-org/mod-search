package org.folio.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.folio.search.domain.dto.RecordType.CALL_NUMBERS;
import static org.folio.search.support.base.ApiEndpoints.browseConfigPath;
import static org.folio.search.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.search.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.search.utils.CallNumberTestData.CallNumberTypeId.LC;
import static org.folio.search.utils.CallNumberTestData.locations;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.folio.search.utils.TestUtils.array;
import static org.folio.search.utils.TestUtils.facet;
import static org.folio.search.utils.TestUtils.facetItem;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.mockCallNumberTypes;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.folio.search.domain.dto.BrowseConfig;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.support.base.BaseConsortiumIntegrationTest;
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
class BrowseCallNumberConsortiumIT extends BaseConsortiumIntegrationTest {

  public static final String LOCATION_FACET = "instances.locationId";
  public static final String TENANT_FACET = "instances.tenantId";

  @BeforeAll
  static void prepare() {
    var allInstances = CallNumberTestData.instances();
    setUpTenant(CENTRAL_TENANT_ID);
    setUpTenant(MEMBER_TENANT_ID);
    var centralInstances = allInstances.subList(0, 30);
    saveRecords(CENTRAL_TENANT_ID, instanceSearchPath(), centralInstances, centralInstances.size(),
      instance -> inventoryApi.createInstance(CENTRAL_TENANT_ID, instance));
    var memberInstances = allInstances.subList(30, allInstances.size());
    saveRecords(MEMBER_TENANT_ID, instanceSearchPath(), memberInstances, allInstances.size(),
      instance -> inventoryApi.createInstance(MEMBER_TENANT_ID, instance));

    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var counted = countIndexDocument(ResourceType.INSTANCE_CALL_NUMBER, CENTRAL_TENANT_ID);
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

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] tenant={0} query={1}, facets={2}")
  @DisplayName("getFacetsForCallNumbers_ecs_parameterized")
  void getFacetsForCallNumbers_positive(String tenantId, String query, String[] facets, Map<String, Facet> expected) {
    var actual = parseResponse(doGet(recordFacetsPath(CALL_NUMBERS, query, facets), tenantId), FacetResult.class);

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
      arguments(CENTRAL_TENANT_ID, "cql.allRecords=1", array(LOCATION_FACET),
        expectedLocationFacet(locations, 27, 20, 13)),
      arguments(CENTRAL_TENANT_ID, "cql.allRecords=1", array(TENANT_FACET), mapOf(TENANT_FACET,
        facet(facetItem(CENTRAL_TENANT_ID, 60)))),
      arguments(CENTRAL_TENANT_ID, "callNumberTypeId=\"" + LC.getId() + "\"", array(LOCATION_FACET),
        expectedLocationFacet(locations, 6, 4, 2)),
      arguments(MEMBER_TENANT_ID, "cql.allRecords=1", array(LOCATION_FACET),
        expectedLocationFacet(locations, 42, 34, 24)),
      arguments(MEMBER_TENANT_ID, "cql.allRecords=1", array(TENANT_FACET), mapOf(TENANT_FACET,
        facet(facetItem(CENTRAL_TENANT_ID, 60), facetItem(MEMBER_TENANT_ID, 40)))),
      arguments(MEMBER_TENANT_ID, "instances.shared=false", array(LOCATION_FACET),
        expectedLocationFacet(locations, 15, 14, 11)),
      arguments(MEMBER_TENANT_ID, "instances.shared=false", array(TENANT_FACET), mapOf(TENANT_FACET,
        facet(facetItem(MEMBER_TENANT_ID, 40))))
    );
  }

  private static Map<String, Facet> expectedLocationFacet(Map<Integer, String> locations,
                                                          int totalRecords1,
                                                          int totalRecords2, int totalRecords3) {
    return mapOf(LOCATION_FACET, facet(
        facetItem(locations.get(1), totalRecords1),
        facetItem(locations.get(2), totalRecords2),
        facetItem(locations.get(3), totalRecords3)
      )
    );
  }

  private static void updateLcConfig(List<UUID> typeIds) {
    var config = new BrowseConfig()
      .id(BrowseOptionType.LC)
      .shelvingAlgorithm(ShelvingOrderAlgorithmType.LC)
      .typeIds(typeIds);

    var stub = mockCallNumberTypes(okapi.wireMockServer(), typeIds.toArray(new UUID[0]));
    doPut(browseConfigPath(BrowseType.CALL_NUMBER, BrowseOptionType.LC), CENTRAL_TENANT_ID, config);
    okapi.wireMockServer().removeStub(stub);
  }

}
