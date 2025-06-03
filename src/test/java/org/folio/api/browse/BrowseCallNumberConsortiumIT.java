package org.folio.api.browse;

import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.folio.search.domain.dto.RecordType.CALL_NUMBERS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CALL_NUMBERS;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER2_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.browseConfigPath;
import static org.folio.support.base.ApiEndpoints.instanceCallNumberBrowsePath;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.utils.CallNumberTestData.CallNumberTestDataRecord;
import static org.folio.support.utils.CallNumberTestData.CallNumberTypeId.LC;
import static org.folio.support.utils.CallNumberTestData.callNumbers;
import static org.folio.support.utils.CallNumberTestData.cnEmptyBrowseItem;
import static org.folio.support.utils.CallNumberTestData.instance;
import static org.folio.support.utils.CallNumberTestData.instances;
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
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.domain.dto.CallNumberBrowseResult;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.model.index.CallNumberResource;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
import org.folio.search.utils.SearchUtils;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseConsortiumIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = "folio.search-config.indexing.instance-children-index-enabled=true")
class BrowseCallNumberConsortiumIT extends BaseConsortiumIntegrationTest {

  private static final String LOCATION_FACET = "instances.locationId";
  private static final String TENANT_FACET = "instances.tenantId";
  private static final String SHARED_FACET = "instances.shared";
  private static final List<Instance> INSTANCES = instances();
  private static final String MEMBER2_LOCATION = UUID.randomUUID().toString();

  @BeforeAll
  static void prepare(@Autowired SubResourcesLockRepository subResourcesLockRepository) {
    setUpTenant(CENTRAL_TENANT_ID);
    setUpTenant(MEMBER_TENANT_ID);
    setUpTenant(MEMBER2_TENANT_ID);

    enableFeature(CENTRAL_TENANT_ID, BROWSE_CALL_NUMBERS);

    var timestamp = subResourcesLockRepository.lockSubResource(ReindexEntityType.CALL_NUMBER, CENTRAL_TENANT_ID);
    if (timestamp.isEmpty()) {
      throw new IllegalStateException("Unexpected state of database: unable to lock call-number resource");
    }
    var centralInstances = INSTANCES.subList(0, 30);
    saveRecords(CENTRAL_TENANT_ID, instanceSearchPath(), centralInstances, centralInstances.size(),
      instance -> inventoryApi.createInstance(CENTRAL_TENANT_ID, instance));
    var memberInstances = INSTANCES.subList(30, INSTANCES.size());
    saveRecords(MEMBER_TENANT_ID, instanceSearchPath(), memberInstances, INSTANCES.size(),
      instance -> inventoryApi.createInstance(MEMBER_TENANT_ID, instance));
    var instance1 = centralInstances.getFirst();
    instance1.setSource(SearchUtils.SOURCE_CONSORTIUM_PREFIX + "FOLIO");
    saveRecords(MEMBER_TENANT_ID, instanceSearchPath(), List.of(instance1), INSTANCES.size(),
      instance -> inventoryApi.createInstance(MEMBER_TENANT_ID, instance));

    var dataRecord = new CallNumberTestDataRecord(callNumbers().getLast().callNumber(), MEMBER2_LOCATION);
    var member2Instance = instance("51", List.of(dataRecord));
    saveRecords(MEMBER2_TENANT_ID, instanceSearchPath(), List.of(member2Instance, instance1),
      centralInstances.size() + 1,
      instance -> inventoryApi.createInstance(MEMBER2_TENANT_ID, instance));
    subResourcesLockRepository.unlockSubResource(ReindexEntityType.CALL_NUMBER, timestamp.get(), CENTRAL_TENANT_ID);

    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() -> {
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

  @MethodSource("callNumberBrowsingDataProvider")
  @DisplayName("browseByCallNumber_parameterized")
  @ParameterizedTest(name = "[{index}] query={0} tenant={1}, option={2}, value=''{3}'', limit={4}")
  void browseByCallNumber_parameterized(String query, String tenant, BrowseOptionType optionType, String input,
                                        Integer limit, CallNumberBrowseResult expected) {
    var request = get(instanceCallNumberBrowsePath(optionType))
      .param("expandAll", "true")
      .param("query", prepareQuery(query, '"' + input + '"'))
      .param("limit", String.valueOf(limit));
    var actual = parseResponse(doGet(request, tenant), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void browseByCallNumber_withLocationFilter() {
    var request = get(instanceCallNumberBrowsePath(BrowseOptionType.ALL))
      .param("expandAll", "true")
      .param("query", "(fullCallNumber>=\"a\" or fullCallNumber<\"a\") "
                      + "and instances.locationId==(\"%s\")".formatted(MEMBER2_LOCATION))
      .param("limit", String.valueOf(10));
    var actual = parseResponse(doGet(request, MEMBER_TENANT_ID), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(cnBrowseResult(null, null, 0, List.of(cnEmptyBrowseItem("a"))));
  }

  @Test
  void browseByCallNumber_fromCentralTenant_withTenantIdFacet_sameCallNumberInMemberTenantOfSharedInstance() {
    var request = get(instanceCallNumberBrowsePath(BrowseOptionType.ALL))
      .param("expandAll", "true")
      .param("query", "(fullCallNumber>=\"TA357 .A78 2010\" or fullCallNumber<\"TA357 .A78 2010\") "
                      + "and instances.tenantId==(\"%s\")".formatted(MEMBER2_TENANT_ID))
      .param("limit", String.valueOf(10));
    var actual = parseResponse(doGet(request, CENTRAL_TENANT_ID), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(cnBrowseResult(null, null, 2,
      List.of(cnBrowseItem(callNumbers().get(1).callNumber(), 0, 1),
        cnBrowseItem(callNumbers().getFirst().callNumber(), 0, 1, true))));
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

  private static Stream<Arguments> callNumberBrowsingDataProvider() {
    var aroundQuery = "fullCallNumber >= {value} or fullCallNumber < {value}";

    var callNumbers = callNumbers().stream()
      .map(CallNumberTestDataRecord::callNumber)
      .collect(Collectors.toMap(callNumberResource -> Integer.parseInt(callNumberResource.id()), identity()));

    return Stream.of(
      arguments(aroundQuery, CENTRAL_TENANT_ID, BrowseOptionType.ALL, callNumbers.get(1).fullCallNumber(), 5,
        cnBrowseResult(callNumbers.get(35).fullCallNumber(), callNumbers.get(43).fullCallNumber(), 60, List.of(
          cnBrowseItem(callNumbers.get(35), 18, 1),
          cnBrowseItem(callNumbers.get(25), 15, 1),
          cnBrowseItem(callNumbers.get(1), 0, 3, true),
          cnBrowseItem(callNumbers.get(33), 17, 1),
          cnBrowseItem(callNumbers.get(43), 23, 1)
        ))),

      arguments(aroundQuery, MEMBER_TENANT_ID, BrowseOptionType.ALL, callNumbers.get(1).fullCallNumber(), 5,
        cnBrowseResult(callNumbers.get(91).fullCallNumber(), callNumbers.get(68).fullCallNumber(), 100, List.of(
          cnBrowseItem(callNumbers.get(91), 45, 1),
          cnBrowseItem(callNumbers.get(25), 15, 1),
          cnBrowseItem(callNumbers.get(1), 0, 3, true),
          cnBrowseItem(callNumbers.get(70), 34, 1),
          cnBrowseItem(callNumbers.get(68), 34, 1)
        )))
    );
  }

  private static CallNumberBrowseResult cnBrowseResult(String prev, String next, int total,
                                                       List<CallNumberBrowseItem> items) {
    return new CallNumberBrowseResult().prev(prev).next(next).items(items).totalRecords(total);
  }

  private static CallNumberBrowseItem cnBrowseItem(CallNumberResource resource, int instanceIndex, int count) {
    return cnBrowseItem(resource, instanceIndex, count, null);
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
      arguments(CENTRAL_TENANT_ID, "cql.allRecords=1", array(LOCATION_FACET),
        expectedLocationFacet(locations, 27, 20, 13, false)),
      arguments(CENTRAL_TENANT_ID, "cql.allRecords=1", array(TENANT_FACET), mapOf(TENANT_FACET,
        facet(facetItem(CENTRAL_TENANT_ID, 60), facetItem(MEMBER_TENANT_ID, 2), facetItem(MEMBER2_TENANT_ID, 2)))),
      arguments(CENTRAL_TENANT_ID, "instances.tenantId==" + MEMBER2_TENANT_ID, array(TENANT_FACET), mapOf(TENANT_FACET,
        facet(facetItem(CENTRAL_TENANT_ID, 60), facetItem(MEMBER_TENANT_ID, 2), facetItem(MEMBER2_TENANT_ID, 2)))),
      arguments(CENTRAL_TENANT_ID, "callNumberTypeId=\"" + LC.getId() + "\"", array(LOCATION_FACET),
        expectedLocationFacet(locations, 6, 4, 2, false)),
      arguments(MEMBER_TENANT_ID, "cql.allRecords=1", array(LOCATION_FACET),
        expectedLocationFacet(locations, 42, 34, 24, true)),
      arguments(MEMBER_TENANT_ID, "cql.allRecords=1", array(TENANT_FACET), mapOf(TENANT_FACET,
        facet(facetItem(CENTRAL_TENANT_ID, 60), facetItem(MEMBER_TENANT_ID, 42),
          facetItem(MEMBER2_TENANT_ID, 3)))),
      arguments(MEMBER_TENANT_ID, "instances.shared==false", array(LOCATION_FACET),
        expectedLocationFacet(locations, 15, 14, 11, true)),
      arguments(MEMBER_TENANT_ID, "instances.shared==false", array(TENANT_FACET), mapOf(TENANT_FACET,
        facet(facetItem(MEMBER_TENANT_ID, 40), facetItem(MEMBER2_TENANT_ID, 1)))),
      arguments(MEMBER_TENANT_ID, "instances.locationId==" + MEMBER2_LOCATION, array(SHARED_FACET, LOCATION_FACET),
        mapOf(SHARED_FACET, facet(facetItem("false", 1))),
        mapOf(LOCATION_FACET, expectedLocationFacet(locations, 42, 34, 24, true)))
    );
  }

  private static Map<String, Facet> expectedLocationFacet(Map<Integer, String> locations,
                                                          int totalRecords1, int totalRecords2, int totalRecords3,
                                                          boolean includeMember2) {
    var facet = facet(
      facetItem(locations.get(1), totalRecords1),
      facetItem(locations.get(2), totalRecords2),
      facetItem(locations.get(3), totalRecords3)
    );
    if (includeMember2) {
      facet.addValuesItem(facetItem(MEMBER2_LOCATION, 1));
    }
    return mapOf(LOCATION_FACET, facet);
  }

  private static void updateLcConfig(List<UUID> typeIds) {
    var config = new BrowseConfig()
      .id(BrowseOptionType.LC)
      .shelvingAlgorithm(ShelvingOrderAlgorithmType.LC)
      .typeIds(typeIds);

    var stub = mockCallNumberTypes(okapi.wireMockServer(), typeIds.toArray(new UUID[0]));
    doPut(browseConfigPath(BrowseType.INSTANCE_CALL_NUMBER, BrowseOptionType.LC), CENTRAL_TENANT_ID, config);
    okapi.wireMockServer().removeStub(stub);
  }
}
