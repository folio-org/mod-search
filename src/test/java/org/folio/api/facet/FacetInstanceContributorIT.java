package org.folio.api.facet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.facet;
import static org.folio.support.utils.TestUtils.facetItem;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.RecordType;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class FacetInstanceContributorIT extends BaseSharedTest {

  private static final String[] NAME_TYPE_IDS =
    array("e2ef4075-310a-4447-a231-712bf10cc985",
      "0ad0a89a-741d-4f1a-85a6-ada214751013",
      "1f857623-89ca-4f0b-ab56-5c30f706df3e",
      "2b94c631-fca9-4892-a730-03ee529ffe2a",
      "9fb7f83e-260e-479f-9539-dfd9a628b858");

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForContributors_parameterized")
  void getFacetsForContributors_parameterized(String query, String[] facets, Map<String, Facet> expected) {
    var request = doGet(recordFacetsPath(RecordType.CONTRIBUTORS, query, facets), TENANT_ID);
    var actual = parseResponse(request, FacetResult.class);

    expected.forEach((facetName, expectedFacet) -> {
      assertNotNull(actual.getFacets());
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
      // all 5 name types present in the shared test dataset
      arguments("name=*", array("contributorNameTypeId"), mapOf("contributorNameTypeId",
        facet(facetItem(NAME_TYPE_IDS[0], 6), facetItem(NAME_TYPE_IDS[1], 5), facetItem(NAME_TYPE_IDS[2], 2),
          facetItem(NAME_TYPE_IDS[3], 54), facetItem(NAME_TYPE_IDS[4], 1)))),

      arguments("name=*", array("contributorNameTypeId:2"),
        mapOf("contributorNameTypeId", facet(facetItem(NAME_TYPE_IDS[3], 54), facetItem(NAME_TYPE_IDS[0], 6)))),

      arguments("contributorNameTypeId==\"" + NAME_TYPE_IDS[0] + "\"", array("contributorNameTypeId:1"),
        mapOf("contributorNameTypeId", facet(facetItem(NAME_TYPE_IDS[0], 6)))),

      arguments("contributorNameTypeId==(\"" + NAME_TYPE_IDS[1] + "\" or \"" + NAME_TYPE_IDS[2] + "\")",
        array("contributorNameTypeId:2"),
        mapOf("contributorNameTypeId", facet(facetItem(NAME_TYPE_IDS[1], 5), facetItem(NAME_TYPE_IDS[2], 2)))));
  }
}
