package org.folio.api.browse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.SearchUtils.ALL_RECORDS_QUERY;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceClassificationBrowsePath;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.sample.SampleInstances.getSemanticWeb;
import static org.folio.support.testdata.ConsortiumTestDataManager.DEWEY_TYPE_ID;
import static org.folio.support.testdata.ConsortiumTestDataManager.LC2_TYPE_ID;
import static org.folio.support.testdata.ConsortiumTestDataManager.LC_TYPE_ID;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.classificationBrowseItem;
import static org.folio.support.utils.TestUtils.classificationBrowseResult;
import static org.folio.support.utils.TestUtils.facet;
import static org.folio.support.utils.TestUtils.facetItem;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.ClassificationNumberBrowseResult;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.RecordType;
import org.folio.support.base.BaseConsortiumIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class BrowseClassificationConsortiumIT extends BaseConsortiumIntegrationTest {

  private static final String SEMANTIC_WEB_LC_TYPE_ID = "ce176ace-a53e-4b4d-aa89-725ed7b2edac";

  @Test
  void browseByClassification_shared() {
    var request = get(instanceClassificationBrowsePath(BrowseOptionType.ALL))
      .param(QUERY_PARAM, prepareQuery("number < {value} or number >= {value} and instances.shared==true",
        "\"QD33 .O87\""))
      .param(LIMIT_PARAM, "4")
      .param(PRECEDING_RECORDS_COUNT_PARAM, "2");
    var actual = parseResponse(doGet(request), ClassificationNumberBrowseResult.class);
    assertThat(actual)
      .as("Shared browse result should match expected classification entries")
      .usingRecursiveComparison().ignoringFields(COLLECTION_IGNORING_FIELDS)
      .isEqualTo(classificationBrowseResult("HQ536 .A565 2018", null, 8, List.of(
        classificationBrowseItem("HQ536 .A565 2018", LC2_TYPE_ID, 1, "instance #03"),
        classificationBrowseItem("N6679.R64 G88 2010", LC_TYPE_ID, 1, "instance #03"),
        classificationBrowseItem("QD33 .O87", LC_TYPE_ID, 1, "instance #04", true,
          "Contributor 1", "Contributor 2"),
        classificationBrowseItem("QD453 .M8 1961", LC_TYPE_ID, 1, "instance #05")

      )));
  }

  @Test
  void browseByClassification_local() {
    var semanticWeb = getSemanticWeb();
    var request = get(instanceClassificationBrowsePath(BrowseOptionType.ALL))
      .param(QUERY_PARAM, prepareQuery("number < {value} or number >= {value} and instances.shared==false",
        "\"QD33 .O87\""))
      .param(LIMIT_PARAM, "4")
      .param(PRECEDING_RECORDS_COUNT_PARAM, "2");
    var actual = parseResponse(doGet(request), ClassificationNumberBrowseResult.class);
    assertThat(actual)
      .as("Local browse result should match expected classification entries")
      .usingRecursiveComparison().ignoringFields(COLLECTION_IGNORING_FIELDS)
      .isEqualTo(classificationBrowseResult("372.4", "SF433 .D47 2004", 13, List.of(
        classificationBrowseItem("372.4", DEWEY_TYPE_ID, 1, "instance #09"),
        classificationBrowseItem("HD1691 .I5 1967", SEMANTIC_WEB_LC_TYPE_ID, 1, semanticWeb.getTitle(),
          semanticWeb.getContributors().stream().map(Contributor::getName).toArray(String[]::new)),
        classificationBrowseItem("QD33 .O87", LC_TYPE_ID, 1, "instance #10", true,
          "Contributor #3", "Contributor #4"),
        classificationBrowseItem("SF433 .D47 2004", LC_TYPE_ID, 1, "instance #06")
      )));
  }

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForClassifications_parameterized")
  void getFacetsForClassifications_parameterized(String query, String[] facets, Map<String, Facet> expected) {
    var actual = parseResponse(doGet(recordFacetsPath(RecordType.CLASSIFICATIONS, query, facets)), FacetResult.class);

    expected.forEach((facetName, expectedFacet) -> {
      var actualFacet = actual.getFacets().get(facetName);

      assertThat(actualFacet)
        .as("Facet '%s' should be present in results", facetName)
        .isNotNull();
      assertThat(actualFacet.getValues())
        .as("Facet '%s' values should match expected", facetName)
        .containsExactlyInAnyOrderElementsOf(expectedFacet.getValues());
    });
  }

  private static Stream<Arguments> facetQueriesProvider() {
    return Stream.of(
      arguments(ALL_RECORDS_QUERY, array("instances.shared"), mapOf("instances.shared",
        facet(facetItem("false", 13), facetItem("true", 8)))),
      arguments(ALL_RECORDS_QUERY, array("instances.tenantId"),
        mapOf("instances.tenantId", facet(facetItem(MEMBER_TENANT_ID, 13),
          facetItem(CENTRAL_TENANT_ID, 8))))
    );
  }
}
