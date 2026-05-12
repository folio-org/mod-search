package org.folio.api.facet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.facet;
import static org.folio.support.utils.TestUtils.facetItem;
import static org.folio.support.utils.TestUtils.mapOf;
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

public abstract class FacetInstanceCallNumberIT extends BaseSharedTest {

  private static final String LC_TYPE_ID = "cbc422b0-1d17-4d43-9cc0-6c89b2efd014";

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForCallNumbers_parameterized")
  void getFacetsForSubjects_parameterized(String query, String[] facets, Map<String, Facet> expected) {
    var actual = parseResponse(doGet(recordFacetsPath(RecordType.CALL_NUMBERS, query, facets)), FacetResult.class);

    expected.forEach((facetName, expectedFacet) -> {
      var actualFacet = actual.getFacets().get(facetName);

      assertThat(actualFacet).isNotNull();
      assertThat(actualFacet.getValues())
        .containsExactlyInAnyOrderElementsOf(expectedFacet.getValues());
    });
  }

  private static Stream<Arguments> facetQueriesProvider() {
    return Stream.of(
      arguments("cql.allRecords=1", array("instances.locationId"),
        mapOf("instances.locationId", allLocationsFacet())),
      arguments("callNumberTypeId=\"" + LC_TYPE_ID + "\"", array("instances.locationId"),
        mapOf("instances.locationId", lcLocationsFacet())),
      arguments("callNumberTypeId==lc", array("instances.locationId"),
        mapOf("instances.locationId", lcLocationsFacet())),
      arguments("callNumberTypeId==all", array("instances.locationId"),
        mapOf("instances.locationId", allLocationsFacet()))
    );
  }

  private static Facet allLocationsFacet() {
    return facet(facetItem("65b6c2e9-8a7b-4a10-9b5d-ba1cf0313cd7", 44),
      facetItem("b777f3a4-4372-4792-a87d-8e8f177eab10", 34),
      facetItem("0d106980-1789-42ac-b355-a6c7a74ddea3", 25),
      facetItem("fcd64ce1-6995-48f0-840e-89ffa2288371", 7),
      facetItem("ce23dfa1-17e8-4a1f-ad6b-34ce6ab352c2", 5),
      facetItem("f1a49577-5096-4771-a8a0-d07d642241eb", 3),
      facetItem("184aae84-a5bf-4c6a-85ba-4a7c73026cd5", 2),
      facetItem("53cf956f-c1df-410b-8bea-27f712cca7c0", 2),
      facetItem("f34d27c6-a8eb-461b-acd6-5dea81771e70", 2),
      facetItem("4fdca025-1629-4688-aeb7-9c5fe5c73549", 1),
      facetItem("b241764c-1466-4e1d-a028-1a3684a5da87", 1),
      facetItem("cdd60388-0c75-4969-b3c5-2d04621ed26f", 1));
  }

  private static Facet lcLocationsFacet() {
    return facet(facetItem("65b6c2e9-8a7b-4a10-9b5d-ba1cf0313cd7", 8),
      facetItem("b777f3a4-4372-4792-a87d-8e8f177eab10", 8),
      facetItem("0d106980-1789-42ac-b355-a6c7a74ddea3", 4));
  }
}
