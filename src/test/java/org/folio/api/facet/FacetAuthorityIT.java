package org.folio.api.facet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.facet;
import static org.folio.support.utils.TestUtils.facetItem;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.RecordType;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class FacetAuthorityIT extends BaseSharedTest {

  private static final String[] IDS = {
    "00000001-0000-4000-8000-000000000000",
    "00000046-0000-4000-8000-000000000000",
    "00000005-0000-4000-8000-000000000000",
    "00000009-0000-4000-8000-000000000000",
    "6edc7db0-5363-41ec-bf63-0242ac130002",
    "75b6e1e8-5363-41ec-bf63-0242ac130002",
    "62f72eeb-ed5a-4619-b01f-1750d5528d27",
    "62f72eeb-ed5a-4619-b01f-1750d5528d28",
    "7bfb7550-5363-41ec-bf63-0242ac130002",
    "3ec9be46-b002-472e-87c9-0e8a4c9eb8d2",
    "74f97d56-37ce-44b8-9316-4e5dd4efc103",
    "62f72eeb-ed5a-4619-b01f-1750d5528d32",
    "8eb6625e-5363-41ec-bf63-0242ac130002",
    "0d4cfb3c-0d5d-4bab-8e99-8f077b09bd34",
    "2dfd4b30-8c45-4a2e-8779-05f338513584"
  };

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForAuthorities_parameterized")
  void getFacetsForAuthorities_parameterized(String query, String[] facets, Map<String, Facet> expected) {
    var actual = parseResponse(
      doGet(recordFacetsPath(RecordType.AUTHORITIES, query, facets)), FacetResult.class);

    expected.forEach((facetName, expectedFacet) -> {
      assertNotNull(actual.getFacets());
      var actualFacet = actual.getFacets().get(facetName);

      assertThat(actualFacet).as("Facet %s exists", facetName).isNotNull();
      assertThat(actualFacet.getValues()).as("Facet %s values are expected", facetName)
        .containsExactlyInAnyOrderElementsOf(expectedFacet.getValues());
    });
  }

  @Test
  void searchByAuthorities_negative_invalidFacetName() throws Exception {
    attemptGet(recordFacetsPath(RecordType.AUTHORITIES, "cql.allRecords=1", "unknownFacet:5"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Invalid facet value")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("facet")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("unknownFacet")));
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private static Stream<Arguments> facetQueriesProvider() {
    var allFacets = array("headingType", "subjectHeadings", "sourceFileId");
    return Stream.of(
      arguments("id=*", allFacets, mapOf(
        "headingType", composeHeadingTypeFacet(),
        "subjectHeadings", composeSubjectHeadingFacet(),
        "sourceFileId", composeSourceFileFacet()
      )),

      arguments("id=*", array("headingType:2"), mapOf("headingType", facet(
        facetItem("Personal Name", 20), facetItem("Corporate Name", 12)))),

      arguments("headingType==\"Genre\"", array("headingType:1"),
        mapOf("headingType", facet(facetItem("Genre", 11)))),

      arguments("headingType==(\"Corporate Name\" or \"Conference Name\")", array("headingType:2"),
        mapOf("headingType", facet(facetItem("Corporate Name", 12), facetItem("Conference Name", 11)))),

      arguments("headingType==(\"Topical\" or \"Other\")", array("headingType:2"),
        mapOf("headingType", facet(facetItem("Topical", 9)))),

      arguments("id=*", array("subjectHeadings:2"), mapOf("subjectHeadings", facet(
        facetItem("a", 78), facetItem("b", 37)))),

      arguments("subjectHeadings==\"c\"", array("subjectHeadings:1"),
        mapOf("subjectHeadings", facet(facetItem("c", 3)))),

      arguments("subjectHeadings==(\"d\" or \"k\")", array("subjectHeadings:2"),
        mapOf("subjectHeadings", facet(facetItem("d", 2), facetItem("k", 1)))),

      arguments("subjectHeadings==(\"r\" or \"z\")", array("subjectHeadings:2"),
        mapOf("subjectHeadings", facet(facetItem("z", 5), facetItem("r", 4)))),

      arguments("id=*", array("sourceFileId:2"), mapOf("sourceFileId", facet(
        facetItem("b4000001-5de4-4467-b77f-b2057d6d69b6", 75), facetItem("5de462a2-7a90-4467-b77f-b2057d6d69b6", 35)))),

      arguments("sourceFileId==\"b4000001-5de4-4467-b77f-b2057d6d69b6\"", array("sourceFileId:1"),
        mapOf("sourceFileId", facet(facetItem("b4000001-5de4-4467-b77f-b2057d6d69b6", 75)))),

      arguments("sourceFileId==\"NULL\"", array("sourceFileId:1"),
        mapOf("sourceFileId", facet(facetItem("NULL", 9)))),

      arguments("sourceFileId==(\"00000009-0000-4000-8000-000000000000\" or \"6edc7db0-5363-41ec-bf63-0242ac130002\")",
        array("sourceFileId:2"), mapOf("sourceFileId", facet(
          facetItem("6edc7db0-5363-41ec-bf63-0242ac130002", 1)))),

      arguments("sourceFileId==(\"75b6e1e8-5363-41ec-bf63-0242ac130002\" or \"62f72eeb-ed5a-4619-b01f-1750d5528d27\")",
        array("sourceFileId:2"),
        mapOf("sourceFileId", facet(facetItem(IDS[5], 2))))
    );
  }

  private static Facet composeSourceFileFacet() {
    return facet(
      facetItem("b4000001-5de4-4467-b77f-b2057d6d69b6", 75),
      facetItem("5de462a2-7a90-4467-b77f-b2057d6d69b6", 35),
      facetItem("NULL", 9),
      facetItem("1353873c-0e5e-4d64-a2f9-6c444dc4cd46", 4),
      facetItem("39a52d91-8dbb-4348-ab06-5c6115e600cd", 3),
      facetItem("62f72eeb-ed5a-4619-b01f-1750d5528d25", 2),
      facetItem("75b6e1e8-5363-41ec-bf63-0242ac130002", 2),
      facetItem("6edc7db0-5363-41ec-bf63-0242ac130002", 1),
      facetItem("cc6bbc19-3f54-43c5-8736-b85688619641", 1)
    );
  }

  private static Facet composeSubjectHeadingFacet() {
    return facet(
      facetItem("a", 78),
      facetItem("b", 37),
      facetItem("z", 5),
      facetItem("r", 4),
      facetItem("c", 3),
      facetItem("d", 2),
      facetItem("n", 2),
      facetItem("k", 1));
  }

  private static Facet composeHeadingTypeFacet() {
    return facet(
      facetItem("Personal Name", 20),
      facetItem("Uniform Title", 12),
      facetItem("Corporate Name", 12),
      facetItem("Genre", 11),
      facetItem("Conference Name", 11),
      facetItem("Topical", 9),
      facetItem("Geographic Name", 8),
      facetItem("Chronological Subdivision", 7),
      facetItem("Chronological Term", 7),
      facetItem("General Subdivision", 7),
      facetItem("Geographic Subdivision", 7),
      facetItem("Medium of Performance Term", 7),
      facetItem("Named Event", 7),
      facetItem("Form Subdivision", 7));
  }
}
