package org.folio.api.browse;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CONTRIBUTORS;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceContributorBrowsePath;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.contributorBrowseItem;
import static org.folio.support.utils.TestUtils.facet;
import static org.folio.support.utils.TestUtils.facetItem;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.ContributorBrowseResult;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.RecordType;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = "folio.search-config.indexing.instance-children-index-enabled=true")
class BrowseContributorIT extends BaseIntegrationTest {

  private static final String[] NAME_TYPE_IDS =
    array("e2ef4075-310a-4447-a231-712bf10cc985", "0ad0a89a-741d-4f1a-85a6-ada214751013",
      "1f857623-89ca-4f0b-ab56-5c30f706df3e");
  private static final String[] TYPE_IDS =
    array("2a165833-1673-493f-934b-f3d3c8fcb299", "3ae36e29-e38f-457c-8fcf-1974a6cb63d3",
      "653ffe66-aa3f-4f1c-a090-c42c4011ef40");
  private static final String[] AUTHORITY_IDS =
    array("0a4c6d10-2161-4f64-aace-9e919489b6c9", "7ff32633-cc49-4332-870a-b05e329d2a2d");
  private static final Instance[] INSTANCES = instances();

  @BeforeAll
  static void prepare(@Autowired SubResourcesLockRepository subResourcesLockRepository) {
    setUpTenant();

    enableFeature(BROWSE_CONTRIBUTORS);

    var timestamp = subResourcesLockRepository.lockSubResource(ReindexEntityType.CONTRIBUTOR, TENANT_ID);
    if (timestamp.isEmpty()) {
      throw new IllegalStateException("Unexpected state of database: unable to lock contributor resource");
    }

    saveRecords(TENANT_ID, instanceSearchPath(), asList(INSTANCES), INSTANCES.length, emptyList(),
      instance -> inventoryApi.createInstance(TENANT_ID, instance));

    // this is needed to test deleting contributors when all instances are unlinked from a contributor
    var instanceToUpdate = INSTANCES[0];
    instanceToUpdate.setContributors(Collections.emptyList());
    inventoryApi.updateInstance(TENANT_ID, instanceToUpdate);

    subResourcesLockRepository.unlockSubResource(ReindexEntityType.CONTRIBUTOR, timestamp.get(), TENANT_ID);

    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() -> {
      var counted = countIndexDocument(ResourceType.INSTANCE_CONTRIBUTOR, TENANT_ID);
      assertThat(counted).isEqualTo(12);
    });
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @MethodSource("contributorBrowsingDataProvider")
  @DisplayName("browseByContributor_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}'', limit={2}")
  void browseByContributor_parameterized(String query, String anchor, Integer limit,
                                         ContributorBrowseResult expected) {
    var request = get(instanceContributorBrowsePath()).param("query", prepareQuery(query, '"' + anchor + '"'))
      .param("limit", String.valueOf(limit));

    var actual = parseResponse(doGet(request), ContributorBrowseResult.class);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void browseByContributor_withNameTypeFilter() {
    var request = get(instanceContributorBrowsePath()).param("query",
      "(" + prepareQuery("name >= {value} or name < {value}", '"' + "John Lennon" + '"') + ") "
      + "and contributorNameTypeId==" + NAME_TYPE_IDS[0]).param("limit", "5");

    var actual = parseResponse(doGet(request), ContributorBrowseResult.class);
    var expected = new ContributorBrowseResult().totalRecords(5).prev(null).next("Paul McCartney").items(
      List.of(
        contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0]),
        contributorBrowseItem(2, "Bon Jovi", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
        contributorBrowseItem(0, true, "John Lennon"),
        contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
        contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0], TYPE_IDS[1])));

    assertThat(actual).isEqualTo(expected);
  }

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForContributors_parameterized")
  void getFacetsForContributors_parameterized(String query, String[] facets, Map<String, Facet> expected) {
    var request = doGet(recordFacetsPath(RecordType.CONTRIBUTORS, query, facets));
    var actual = parseResponse(request, FacetResult.class);

    expected.forEach((facetName, expectedFacet) -> {
      var actualFacet = actual.getFacets().get(facetName);

      assertThat(actualFacet).isNotNull();
      assertThat(actualFacet.getValues()).containsExactlyInAnyOrderElementsOf(expectedFacet.getValues());
    });
  }

  private static Stream<Arguments> contributorBrowsingDataProvider() {
    var aroundQuery = "name > {value} or name < {value}";
    var aroundIncludingQuery = "name >= {value} or name < {value}";
    var forwardQuery = "name > {value}";
    var forwardIncludingQuery = "name >= {value}";
    var backwardQuery = "name < {value}";
    var backwardIncludingQuery = "name <= {value}";

    return Stream.of(arguments(aroundQuery, "John", 5,
        new ContributorBrowseResult().totalRecords(12).prev("Bon Jovi").next("John Lennon").items(List.of(
          contributorBrowseItem(1, "Bon Jovi", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[0]),
          contributorBrowseItem(2, "George Harrison", NAME_TYPE_IDS[1], AUTHORITY_IDS[0], TYPE_IDS[2]),
          contributorBrowseItem(0, true, "John"),
          contributorBrowseItem(2, "John Lennon", NAME_TYPE_IDS[2], AUTHORITY_IDS[1], TYPE_IDS[0]),
          contributorBrowseItem(1, "John Lennon", NAME_TYPE_IDS[2], null, TYPE_IDS[0])))),

      arguments(aroundQuery, "Lenon", 5,
        new ContributorBrowseResult().totalRecords(12).prev("Klaus Meine").next("Paul McCartney").items(List.of(
          contributorBrowseItem(1, "Klaus Meine", NAME_TYPE_IDS[1], AUTHORITY_IDS[0]),
          contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(0, true, "Lenon"),
          contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(1, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[2])))),

      arguments(aroundIncludingQuery, "Meine", 5,
        new ContributorBrowseResult().totalRecords(12).prev("Klaus Meine").next("Paul McCartney").items(List.of(
          contributorBrowseItem(1, "Klaus Meine", NAME_TYPE_IDS[1], AUTHORITY_IDS[0]),
          contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(0, true, "Meine"),
          contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(1, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[2])))),

      arguments(aroundIncludingQuery, "Klaus Meine", 5,
        new ContributorBrowseResult().totalRecords(12).prev("John Lennon").next("Paul McCartney").items(List.of(
          contributorBrowseItem(2, "John Lennon", NAME_TYPE_IDS[2], AUTHORITY_IDS[1], TYPE_IDS[0]),
          contributorBrowseItem(1, "John Lennon", NAME_TYPE_IDS[2], null, TYPE_IDS[0]),
          contributorBrowseItem(1, true, "Klaus Meine", NAME_TYPE_IDS[1], AUTHORITY_IDS[0]),
          contributorBrowseItem(2, true, "Klaus Meine", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0], TYPE_IDS[1])))),

      arguments(aroundIncludingQuery, "Zak", 25,
        new ContributorBrowseResult().totalRecords(12).prev(null).next(null).items(List.of(
          contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[2]),
          contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0]),
          contributorBrowseItem(2, "Bon Jovi", NAME_TYPE_IDS[0], AUTHORITY_IDS[0],
            TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
          contributorBrowseItem(1, "Bon Jovi", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[0]),
          contributorBrowseItem(2, "George Harrison", NAME_TYPE_IDS[1], AUTHORITY_IDS[0], TYPE_IDS[2]),
          contributorBrowseItem(2, "John Lennon", NAME_TYPE_IDS[2], AUTHORITY_IDS[1], TYPE_IDS[0]),
          contributorBrowseItem(1, "John Lennon", NAME_TYPE_IDS[2], null, TYPE_IDS[0]),
          contributorBrowseItem(1, "Klaus Meine", NAME_TYPE_IDS[1], AUTHORITY_IDS[0]),
          contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(1, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[2]),
          contributorBrowseItem(2, "Ringo Starr", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(0, true, "Zak")))),

      arguments(aroundIncludingQuery, "PMC", 5,
        new ContributorBrowseResult().totalRecords(12).prev("Paul McCartney").next(null).items(List.of(
          contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(1, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[2]),
          contributorBrowseItem(0, true, "PMC"),
          contributorBrowseItem(2, "Ringo Starr", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1])))),

      arguments(aroundIncludingQuery, "a", 5,
        new ContributorBrowseResult().totalRecords(12).prev(null).next("Anthony Kiedis").items(List.of(
          contributorBrowseItem(0, true, "a"),
          contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[2]),
          contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0])))),

      arguments(aroundIncludingQuery, "z", 5,
        new ContributorBrowseResult().totalRecords(12).prev("Paul McCartney").next(null).items(List.of(
          contributorBrowseItem(1, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[2]),
          contributorBrowseItem(2, "Ringo Starr", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(0, true, "z")))),

      // browsing forward
      arguments(forwardQuery, "ringo", 5,
        new ContributorBrowseResult().totalRecords(12).prev("Ringo Starr").next(null).items(List.of(
          contributorBrowseItem(2, "Ringo Starr", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1])))),

      arguments(forwardQuery, "anthony", 5, new ContributorBrowseResult()
        .totalRecords(12).prev("Anthony Kiedis").next("George Harrison").items(List.of(
          contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[2]),
          contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0]),
          contributorBrowseItem(2, "Bon Jovi", NAME_TYPE_IDS[0], AUTHORITY_IDS[0],
            TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
          contributorBrowseItem(1, "Bon Jovi", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[0]),
          contributorBrowseItem(2, "George Harrison", NAME_TYPE_IDS[1], AUTHORITY_IDS[0], TYPE_IDS[2])))),

      arguments(forwardQuery, "Z", 10, new ContributorBrowseResult().totalRecords(12).items(emptyList())),

      arguments(forwardIncludingQuery, "Ringo Starr", 5,
        new ContributorBrowseResult().totalRecords(12).prev("Ringo Starr").next(null).items(List.of(
          contributorBrowseItem(2, "Ringo Starr", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1])))),

      arguments(forwardIncludingQuery, "anthony", 5, new ContributorBrowseResult()
        .totalRecords(12).prev("Anthony Kiedis").next("George Harrison").items(List.of(
          contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[2]),
          contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0]),
          contributorBrowseItem(2, "Bon Jovi", NAME_TYPE_IDS[0], AUTHORITY_IDS[0],
            TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
          contributorBrowseItem(1, "Bon Jovi", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[0]),
          contributorBrowseItem(2, "George Harrison", NAME_TYPE_IDS[1], AUTHORITY_IDS[0], TYPE_IDS[2])))),

      // browsing backward
      arguments(backwardQuery, "Ringo Starr", 5,
        new ContributorBrowseResult().totalRecords(12).prev("John Lennon").next("Paul McCartney").items(List.of(
          contributorBrowseItem(2, "John Lennon", NAME_TYPE_IDS[2], AUTHORITY_IDS[1], TYPE_IDS[0]),
          contributorBrowseItem(1, "Klaus Meine", NAME_TYPE_IDS[1], AUTHORITY_IDS[0]),
          contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(1, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[2])))),

      arguments(backwardQuery, "R", 5,
        new ContributorBrowseResult().totalRecords(12).prev("John Lennon").next("Paul McCartney").items(List.of(
          contributorBrowseItem(2, "John Lennon", NAME_TYPE_IDS[2], AUTHORITY_IDS[1], TYPE_IDS[0]),
          contributorBrowseItem(1, "Klaus Meine", NAME_TYPE_IDS[1], AUTHORITY_IDS[0]),
          contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(1, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[2])))),

      arguments(backwardQuery, "A", 10, new ContributorBrowseResult().totalRecords(12).items(emptyList())),

      arguments(backwardIncludingQuery, "ringo", 5,
        new ContributorBrowseResult().totalRecords(12).prev("John Lennon").next("Paul McCartney").items(List.of(
          contributorBrowseItem(2, "John Lennon", NAME_TYPE_IDS[2], AUTHORITY_IDS[1], TYPE_IDS[0]),
          contributorBrowseItem(1, "Klaus Meine", NAME_TYPE_IDS[1], AUTHORITY_IDS[0]),
          contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(1, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[2])))));
  }

  private static Instance[] instances() {
    return contributorBrowseInstanceData().stream().map(BrowseContributorIT::instance).toArray(Instance[]::new);
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

  private static Stream<Arguments> facetQueriesProvider() {
    return Stream.of(arguments("name=*", array("contributorNameTypeId"), mapOf("contributorNameTypeId",
        facet(facetItem(NAME_TYPE_IDS[0], 5), facetItem(NAME_TYPE_IDS[1], 5), facetItem(NAME_TYPE_IDS[2], 2)))),

      arguments("name=*", array("contributorNameTypeId:2"),
        mapOf("contributorNameTypeId", facet(facetItem(NAME_TYPE_IDS[0], 5), facetItem(NAME_TYPE_IDS[1], 5)))),

      arguments("contributorNameTypeId==\"" + NAME_TYPE_IDS[0] + "\"", array("contributorNameTypeId:1"),
        mapOf("contributorNameTypeId", facet(facetItem(NAME_TYPE_IDS[0], 5)))),

      arguments("contributorNameTypeId==(\"" + NAME_TYPE_IDS[1] + "\" or \"" + NAME_TYPE_IDS[2] + "\")",
        array("contributorNameTypeId:2"),
        mapOf("contributorNameTypeId", facet(facetItem(NAME_TYPE_IDS[1], 5), facetItem(NAME_TYPE_IDS[2], 2)))));
  }
}
