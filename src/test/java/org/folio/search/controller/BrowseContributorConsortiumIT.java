package org.folio.search.controller;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.folio.search.support.base.ApiEndpoints.instanceContributorBrowsePath;
import static org.folio.search.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.search.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.search.utils.TestConstants.CONSORTIUM_TENANT_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.array;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.contributorBrowseItem;
import static org.folio.search.utils.TestUtils.facet;
import static org.folio.search.utils.TestUtils.facetItem;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceContributorBrowseResult;
import org.folio.search.domain.dto.RecordType;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.SearchUtils;
import org.folio.spring.test.type.IntegrationTest;
import org.folio.tenant.domain.dto.Parameter;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
@IntegrationTest
class BrowseContributorConsortiumIT extends BaseIntegrationTest {

  private static final String[] NAME_TYPE_IDS =
    array("e2ef4075-310a-4447-a231-712bf10cc985", "0ad0a89a-741d-4f1a-85a6-ada214751013",
      "1f857623-89ca-4f0b-ab56-5c30f706df3e");
  private static final String[] TYPE_IDS =
    array("2a165833-1673-493f-934b-f3d3c8fcb299", "3ae36e29-e38f-457c-8fcf-1974a6cb63d3",
      "653ffe66-aa3f-4f1c-a090-c42c4011ef40");
  private static final String[] AUTHORITY_IDS =
    array("0a4c6d10-2161-4f64-aace-9e919489b6c9", "7ff32633-cc49-4332-870a-b05e329d2a2d");
  private static final Instance[] INSTANCES_MEMBER = instancesMember();
  private static final Instance[] INSTANCES_CENTRAL = instancesCentral();

  @BeforeAll
  static void prepare(@Autowired RestHighLevelClient restHighLevelClient) throws InterruptedException {
    setUpTenant(CONSORTIUM_TENANT_ID, INSTANCES_CENTRAL.length, INSTANCES_CENTRAL);
    setUpTenant(TENANT_ID, INSTANCES_CENTRAL.length + INSTANCES_MEMBER.length, INSTANCES_MEMBER);

    // this is needed to test deleting contributors when all instances are unlinked from a contributor
    var instanceToUpdate = INSTANCES_CENTRAL[0];
    instanceToUpdate.setContributors(Collections.emptyList());
    inventoryApi.updateInstance(CONSORTIUM_TENANT_ID, instanceToUpdate);

    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var searchRequest = new SearchRequest()
        .source(searchSource().query(matchAllQuery()).trackTotalHits(true).from(0).size(0))
        .indices(getIndexName(SearchUtils.CONTRIBUTOR_RESOURCE, CONSORTIUM_TENANT_ID));
      var searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
      assertThat(searchResponse.getHits().getTotalHits().value).isEqualTo(12);
    });
  }

  //todo: move 4 methods below to consortium integration test base in a scope of MSEARCH-562
  @SneakyThrows
  protected static void setUpTenant(String tenantName, int expectedCount, Instance... instances) {
    setUpTenant(tenantName, instanceSearchPath(), () -> { }, asList(instances), expectedCount,
      instance -> inventoryApi.createInstance(tenantName, instance));
  }

  @SneakyThrows
  private static <T> void setUpTenant(String tenant, String validationPath, Runnable postInitAction,
                                      List<T> records, Integer expectedCount, Consumer<T> consumer) {
    enableTenant(tenant);
    postInitAction.run();
    saveRecords(tenant, validationPath, records, expectedCount, consumer);
  }

  @SneakyThrows
  protected static void enableTenant(String tenant) {
    var tenantAttributes = new TenantAttributes().moduleTo("mod-search");
    tenantAttributes.addParametersItem(new Parameter("centralTenantId").value(CONSORTIUM_TENANT_ID));

    mockMvc.perform(post("/_/tenant", randomId())
        .content(asJsonString(tenantAttributes))
        .headers(defaultHeaders(tenant))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForContributors_parameterized")
  void getFacetsForContributors_parameterized(String query, String[] facets, Map<String, Facet> expected) {
    var actual = parseResponse(doGet(recordFacetsPath(RecordType.CONTRIBUTORS, query, facets)), FacetResult.class);

    expected.forEach((facetName, expectedFacet) -> {
      var actualFacet = actual.getFacets().get(facetName);

      assertThat(actualFacet).isNotNull();
      assertThat(actualFacet.getValues())
        .containsExactlyInAnyOrderElementsOf(expectedFacet.getValues());
    });
  }

  @Test
  void browseByContributor_shared() {
    var request = get(instanceContributorBrowsePath()).param("query",
      "(" + prepareQuery("name >= {value} or name < {value}", '"' + "Bon Jovi" + '"') + ") "
        + "and instances.shared==true").param("limit", "5");

    var actual = parseResponse(doGet(request), InstanceContributorBrowseResult.class);
    var expected = new InstanceContributorBrowseResult().totalRecords(12).prev(null).next("George Harrison").items(
      List.of(
        contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0]),
        contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[2]),
        contributorBrowseItem(2, true, "Bon Jovi", NAME_TYPE_IDS[0], AUTHORITY_IDS[0],
          TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
        contributorBrowseItem(1, true, "Bon Jovi", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[0]),
        contributorBrowseItem(2, "George Harrison", NAME_TYPE_IDS[1], AUTHORITY_IDS[0], TYPE_IDS[2])));

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void browseByContributor_local() {
    var request = get(instanceContributorBrowsePath()).param("query",
      "(" + prepareQuery("name >= {value} or name < {value}", '"' + "Bon Jovi" + '"') + ") "
        + "and instances.shared==false").param("limit", "5");

    var actual = parseResponse(doGet(request), InstanceContributorBrowseResult.class);
    var expected = new InstanceContributorBrowseResult().totalRecords(8).prev(null).next("John Lennon").items(
      List.of(
        contributorBrowseItem(1, true, "Bon Jovi", NAME_TYPE_IDS[0], AUTHORITY_IDS[0],
          TYPE_IDS[1], TYPE_IDS[2]),
        contributorBrowseItem(2, "George Harrison", NAME_TYPE_IDS[1], AUTHORITY_IDS[0], TYPE_IDS[2]),
        contributorBrowseItem(2, "John Lennon", NAME_TYPE_IDS[2], AUTHORITY_IDS[1], TYPE_IDS[0])));

    assertThat(actual).isEqualTo(expected);
  }


  private static Stream<Arguments> facetQueriesProvider() {
    return Stream.of(
      arguments("cql.allRecords=1", array("instances.shared"), mapOf("instances.shared",
        facet(facetItem("false", 8), facetItem("true", 5)))),
      arguments("cql.allRecords=1", array("instances.tenantId"),
        mapOf("instances.tenantId", facet(facetItem(TENANT_ID, 8),
          facetItem(CONSORTIUM_TENANT_ID, 5))))
    );
  }

  private static Instance[] instancesMember() {
    return contributorBrowseInstanceData().subList(3, 7).stream()
      .map(BrowseContributorConsortiumIT::instance).toArray(Instance[]::new);
  }

  private static Instance[] instancesCentral() {
    return contributorBrowseInstanceData().subList(0, 3).stream()
      .map(BrowseContributorConsortiumIT::instance).toArray(Instance[]::new);
  }

  @SuppressWarnings("unchecked")
  private static Instance instance(List<Object> data) {
    return new Instance().id(randomId()).title((String) data.get(0)).contributors((List<Contributor>) data.get(1))
      .staffSuppress(false).discoverySuppress(false).holdings(emptyList());
  }

  private static List<List<Object>> contributorBrowseInstanceData() {
    return List.of(
      List.of("instance #00", List.of(
        contributor("Darth Vader", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0])
      )),
      List.of("instance #01", List.of(
        contributor("Bon Jovi", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0]),
        contributor("Klaus Meine", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0]),
        contributor("Anthony Kiedis", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0])
      )),
      List.of("instance #02", List.of(
        contributor("Bon Jovi", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[0]),
        contributor("Klaus Meine", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[1]),
        contributor("Anthony Kiedis", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[2])
      )),
      List.of("instance #03", List.of(
        contributor("Bon Jovi", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[1]),
        contributor("Bon Jovi", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[2]),
        contributor("Klaus Meine", NAME_TYPE_IDS[1], AUTHORITY_IDS[0], null)
      )),
      List.of("instance #04", List.of(
        contributor("John Lennon", NAME_TYPE_IDS[2], AUTHORITY_IDS[1], TYPE_IDS[0]),
        contributor("Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0]),
        contributor("George Harrison", NAME_TYPE_IDS[1], AUTHORITY_IDS[0], TYPE_IDS[2]),
        contributor("Ringo Starr", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[0])
      )),
      List.of("instance #05", List.of(
        contributor("John Lennon", NAME_TYPE_IDS[2], AUTHORITY_IDS[1], TYPE_IDS[0]),
        contributor("Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[1]),
        contributor("George Harrison", NAME_TYPE_IDS[1], AUTHORITY_IDS[0], TYPE_IDS[2]),
        contributor("Ringo Starr", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[1])
      )),
      List.of("instance #06", List.of(
        contributor("Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[2]),
        contributor("John Lennon", NAME_TYPE_IDS[2], null, TYPE_IDS[0])
      )));
  }

  private static Contributor contributor(String name, String nameTypeId, String authorityId, String typeId) {
    return new Contributor()
      .name(name)
      .contributorNameTypeId(nameTypeId)
      .contributorTypeId(typeId)
      .authorityId(authorityId);
  }

  private static <T> void saveRecords(String tenant, String validationPath, List<T> records, Integer expectedCount,
                                      Consumer<T> consumer) {
    records.forEach(consumer);
    if (records.size() > 0) {
      checkThatEventsFromKafkaAreIndexed(tenant, validationPath, expectedCount);
    }
  }
}
