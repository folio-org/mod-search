package org.folio.api.browse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.SearchUtils.ALL_RECORDS_QUERY;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceContributorBrowsePath;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.sample.SampleInstances.getSemanticWeb;
import static org.folio.support.testdata.ConsortiumTestDataManager.CONTRIBUTOR_AUTHORITY_IDS;
import static org.folio.support.testdata.ConsortiumTestDataManager.CONTRIBUTOR_NAME_TYPE_IDS;
import static org.folio.support.testdata.ConsortiumTestDataManager.CONTRIBUTOR_TYPE_IDS;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.contributorBrowseItem;
import static org.folio.support.utils.TestUtils.facet;
import static org.folio.support.utils.TestUtils.facetItem;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.domain.dto.ContributorBrowseResult;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.RecordType;
import org.folio.support.base.BaseConsortiumIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class BrowseContributorConsortiumIT extends BaseConsortiumIntegrationTest {

  private static final String[] NAME_TYPE_IDS = CONTRIBUTOR_NAME_TYPE_IDS;
  private static final String[] TYPE_IDS = CONTRIBUTOR_TYPE_IDS;
  private static final String[] AUTHORITY_IDS = CONTRIBUTOR_AUTHORITY_IDS;

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForContributors_parameterized")
  void getFacetsForContributors_parameterized(String query, String[] facets, Map<String, Facet> expected) {
    var actual = parseResponse(doGet(recordFacetsPath(RecordType.CONTRIBUTORS, query, facets)), FacetResult.class);

    expected.forEach((facetName, expectedFacet) -> {
      assertNotNull(actual.getFacets());
      var actualFacet = actual.getFacets().get(facetName);

      assertThat(actualFacet).isNotNull();
      assertThat(actualFacet.getValues())
        .containsExactlyInAnyOrderElementsOf(expectedFacet.getValues());
    });
  }

  @Test
  void browseByContributor_shared() {
    var request = get(instanceContributorBrowsePath()).param(QUERY_PARAM,
      "(" + prepareQuery("name >= {value} or name < {value}", '"' + "Bon Jovi" + '"') + ") "
      + "and instances.shared==true").param(LIMIT_PARAM, "5");

    var actual = parseResponse(doGet(request), ContributorBrowseResult.class);
    var expected = new ContributorBrowseResult().totalRecords(7).prev(null).next("Contributor 1").items(
      List.of(
        contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[2]),
        contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0]),
        contributorBrowseItem(1, true, "Bon Jovi", NAME_TYPE_IDS[0], AUTHORITY_IDS[0],
          TYPE_IDS[0]),
        contributorBrowseItem(1, true, "Bon Jovi", NAME_TYPE_IDS[1], AUTHORITY_IDS[1],
          TYPE_IDS[0]),
        contributorBrowseItem(1, false, "Contributor 1")));

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void browseByContributor_shared_sameContributorInSeveralInstances_countsInstancesAndMergesTypeIds() {
    var request = get(instanceContributorBrowsePath())
      .param(QUERY_PARAM, "name >= \"Klaus Meine\" and instances.shared==true")
      .param(LIMIT_PARAM, "1");

    var actual = parseResponse(doGet(request), ContributorBrowseResult.class);

    assertThat(actual.getItems()).containsExactly(
      contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]));
  }

  @Test
  void browseByContributor_local() {
    var request = get(instanceContributorBrowsePath()).param(QUERY_PARAM,
      "(" + prepareQuery("name >= {value} or name < {value}", '"' + "Bon Jovi" + '"') + ") "
      + "and instances.shared==false")
      .param(LIMIT_PARAM, "5");

    var actual = parseResponse(doGet(request), ContributorBrowseResult.class);
    var semanticWebContributor = getSemanticWeb().getContributors().getFirst();
    var expected = new ContributorBrowseResult().totalRecords(12).prev(null).next("Contributor #4").items(
      List.of(
        contributorBrowseItem(1, semanticWebContributor.getName(), semanticWebContributor.getContributorNameTypeId(),
          semanticWebContributor.getAuthorityId(), semanticWebContributor.getContributorTypeId()),
        contributorBrowseItem(1, true, "Bon Jovi", NAME_TYPE_IDS[0], AUTHORITY_IDS[0],
          TYPE_IDS[1], TYPE_IDS[2]),
        contributorBrowseItem(1, false, "Contributor #3"),
        contributorBrowseItem(1, false, "Contributor #4")));

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void browseByContributor_local_sameContributorInSeveralInstances_countsInstances() {
    var georgeHarrisonRequest = get(instanceContributorBrowsePath())
      .param(QUERY_PARAM, "name >= \"George Harrison\" and instances.shared==false")
      .param(LIMIT_PARAM, "1");
    var johnLennonRequest = get(instanceContributorBrowsePath())
      .param(QUERY_PARAM, "name >= \"John Lennon\" and instances.shared==false")
      .param(LIMIT_PARAM, "2");

    var georgeHarrison = parseResponse(doGet(georgeHarrisonRequest), ContributorBrowseResult.class);
    var johnLennon = parseResponse(doGet(johnLennonRequest), ContributorBrowseResult.class);

    assertThat(georgeHarrison.getItems()).containsExactly(
      contributorBrowseItem(2, "George Harrison", NAME_TYPE_IDS[1], AUTHORITY_IDS[0], TYPE_IDS[2]));
    assertThat(johnLennon.getItems()).containsExactlyInAnyOrder(
      contributorBrowseItem(2, "John Lennon", NAME_TYPE_IDS[2], AUTHORITY_IDS[1], TYPE_IDS[0]),
      contributorBrowseItem(1, "John Lennon", NAME_TYPE_IDS[2], null, TYPE_IDS[0]));
  }

  private static Stream<Arguments> facetQueriesProvider() {
    return Stream.of(
      arguments(ALL_RECORDS_QUERY, array("instances.shared"), mapOf("instances.shared",
        facet(facetItem("false", 12), facetItem("true", 7)))),
      arguments(ALL_RECORDS_QUERY, array("instances.tenantId"),
        mapOf("instances.tenantId", facet(facetItem(MEMBER_TENANT_ID, 12),
          facetItem(CENTRAL_TENANT_ID, 7))))
    );
  }
}
