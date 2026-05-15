package org.folio.api.facet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.RecordType.SUBJECTS;
import static org.folio.search.utils.SearchUtils.ALL_RECORDS_QUERY;
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
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class FacetInstanceSubjectIT extends BaseSharedTest {

  private static final String SOURCE_ID = "33e04938-720f-4814-82f6-416f91ac5795";
  private static final String TYPE_ID = "252681cd-2fa1-4c25-a5b8-a5213a99d073";

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForSubjects_parameterized")
  void getFacetsForSubjects_parameterized(String query, String[] facets, Map<String, Facet> expected) {
    var actual = parseResponse(doGet(recordFacetsPath(SUBJECTS, query, facets), TENANT_ID), FacetResult.class);

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
      arguments(ALL_RECORDS_QUERY, array("sourceId"), mapOf("sourceId",
        facet(facetItem(SOURCE_ID, 2)))),
      arguments(ALL_RECORDS_QUERY, array("typeId"), mapOf("typeId",
        facet(facetItem(TYPE_ID, 1))))
    );
  }
}
