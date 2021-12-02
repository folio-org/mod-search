package org.folio.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.support.base.ApiEndpoints.authorityFacets;
import static org.folio.search.utils.TestUtils.array;
import static org.folio.search.utils.TestUtils.facet;
import static org.folio.search.utils.TestUtils.facetItem;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.Metadata;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@IntegrationTest
class SearchAuthorityFilterIT extends BaseIntegrationTest {

  private static final int RECORDS_COUNT = 15;

  private static final String[] IDS = array(
    "1353873c-0e5e-4d64-a2f9-6c444dc4cd46",
    "cc6bbc19-3f54-43c5-8736-b85688619641",
    "39a52d91-8dbb-4348-ab06-5c6115e600cd",
    "62f72eeb-ed5a-4619-b01f-1750d5528d25",
    "62f72eeb-ed5a-4619-b01f-1750d5528d26",
    "62f72eeb-ed5a-4619-b01f-1750d5528d27",
    "62f72eeb-ed5a-4619-b01f-1750d5528d28",
    "62f72eeb-ed5a-4619-b01f-1750d5528d29",
    "62f72eeb-ed5a-4619-b01f-1750d5528d30",
    "62f72eeb-ed5a-4619-b01f-1750d5528d31",
    "62f72eeb-ed5a-4619-b01f-1750d5528d32",
    "62f72eeb-ed5a-4619-b01f-1750d5528d33",
    "62f72eeb-ed5a-4619-b01f-1750d5528d34",
    "62f72eeb-ed5a-4619-b01f-1750d5528d35",
    "62f72eeb-ed5a-4619-b01f-1750d5528d36");

  @BeforeAll
  static void prepare() {
    setUpTenant(RECORDS_COUNT, authorities());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @MethodSource("filteredSearchQueriesProvider")
  @DisplayName("searchByAuthorities_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}")
  void searchByAuthorities_parameterized(String query, List<String> expectedIds) throws Exception {
    doSearchByAuthorities(query)
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(expectedIds.size())))
      .andExpect(jsonPath("authorities[*].id", containsInAnyOrder(expectedIds.toArray(String[]::new))));
  }

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForAuthorities_parameterized")
  void getFacetsForAuthorities_parameterized(String query, String[] facets, Map<String, Facet> expected) {
    var actual = parseResponse(doGet(authorityFacets(query, facets)), FacetResult.class);

    expected.forEach((facetName, expectedFacet) -> {
      var actualFacet = actual.getFacets().get(facetName);

      assertThat(actualFacet).isNotNull();
      assertThat(actualFacet.getValues())
        .containsExactlyInAnyOrderElementsOf(expectedFacet.getValues());
    });
  }

  private static Stream<Arguments> filteredSearchQueriesProvider() {
    return Stream.of(
      arguments("(id=* and headingType==\"Conference Name\")", List.of(IDS[4])),
      arguments("(id=* and headingType==\"Geographic Name\")", List.of(IDS[5])),
      arguments("(id=* and headingType==\"Genre\")", List.of(IDS[6], IDS[7])),
      arguments("(id=* and headingType==\"Corporate Name\")", List.of(IDS[8], IDS[9])),
      arguments("(id=* and headingType==\"Topical\")", List.of(IDS[10])),
      arguments("(id=* and headingType==\"Uniform Title\")", List.of(IDS[11], IDS[12])),
      arguments("(headingType==\"Uniform Title\")", List.of(IDS[11], IDS[12])),

      arguments("(id=* and authRefType==\"Auth/Ref\" and headingType==\"Other\")", List.of(IDS[13], IDS[14])),
      arguments("(id=* and authRefType==\"Authorized\" and headingType==\"Personal Name\")", List.of(IDS[0], IDS[1], IDS[2], IDS[3])),
      arguments("(authRefType==\"Authorized\" and headingType==\"Conference Name\")", List.of(IDS[4])),

      arguments("(metadata.createdDate>= 2021-03-01) ", List.of(IDS[0], IDS[1], IDS[2], IDS[3])),
      arguments("(metadata.createdDate > 2021-03-01) ", List.of(IDS[1], IDS[2], IDS[3])),
      arguments("(metadata.createdDate>= 2021-03-01 and metadata.createdDate < 2021-03-10) ",
        List.of(IDS[0], IDS[2])),

      arguments("(metadata.updatedDate >= 2021-03-14) ", List.of(IDS[2], IDS[3])),
      arguments("(metadata.updatedDate > 2021-03-01) ", List.of(IDS[0], IDS[1], IDS[2], IDS[3])),
      arguments("(metadata.updatedDate > 2021-03-05) ", List.of(IDS[1], IDS[2], IDS[3])),
      arguments("(metadata.updatedDate < 2021-03-15) ", List.of(IDS[0], IDS[1])),
      arguments("(metadata.updatedDate > 2021-03-14 and metadata.updatedDate < 2021-03-16) ",
        List.of(IDS[2], IDS[3]))
    );
  }

  private static Stream<Arguments> facetQueriesProvider() {
    return Stream.of(
      arguments("id=*", array("headingType"), mapOf("headingType", facet(
        facetItem("Personal Name", 4), facetItem("Corporate Name", 2),
        facetItem("Conference Name", 1), facetItem("Geographic Name", 1),
        facetItem("Uniform Title", 2), facetItem("Topical", 1),
        facetItem("Genre", 2), facetItem("Other", 2))
      )),

      arguments("id=*", array("headingType:2"), mapOf("headingType", facet(
        facetItem("Personal Name", 4), facetItem("Corporate Name", 2)))),

      arguments("headingType==\"Genre\"", array("headingType:1"),
        mapOf("headingType", facet(
          facetItem("Genre", 2)))),

      arguments("headingType==(\"Corporate Name\" or \"Conference Name\")", array("headingType:2"),
        mapOf("headingType", facet(
          facetItem("Corporate Name", 2), facetItem("Conference Name", 1)))),

      arguments("headingType==(\"Topical\" or \"Other\")", array("headingType:2"),
        mapOf("headingType", facet(
          facetItem("Topical", 1), facetItem("Other", 2))))
    );
  }


  private static Authority[] authorities() {
    var authorities = IntStream.range(0, RECORDS_COUNT)
      .mapToObj(i -> new Authority().id(IDS[i]))
      .toArray(Authority[]::new);

    authorities[0]
      .personalName("Resource 0")
      .metadata(metadata("2021-03-01T00:00:00.000+00:00", "2021-03-05T12:30:00.000+00:00"));

    authorities[1]
      .personalName("Resource 1")
      .metadata(metadata("2021-03-10T01:00:00.000+00:00", "2021-03-12T15:40:00.000+00:00"));

    authorities[2]
      .personalName("Resource 2")
      .metadata(metadata("2021-03-08T15:00:00.000+00:00", "2021-03-15T22:30:00.000+00:00"));

    authorities[3]
      .personalName("Resource 3")
      .metadata(metadata("2021-03-15T12:00:00.000+00:00", "2021-03-15T12:00:00.000+00:00"));

    authorities[4]
      .meetingName("ConferenceName");
    authorities[5]
      .geographicName("GeographicName");
    authorities[6]
      .genreTerm("GenreTerm");
    authorities[7]
      .genreTerm("GenreTerm");
    authorities[8]
      .corporateName("CorporateName");
    authorities[9]
      .corporateName("CorporateName");
    authorities[10]
      .topicalTerm("TopicalTerm");
    authorities[11]
      .uniformTitle("UniformTitle");
    authorities[12]
      .uniformTitle("UniformTitle");
    authorities[13]
      .saftUniformTitle(Collections.singletonList("UniformTitle"));
    authorities[14]
      .saftPersonalName(Collections.singletonList("PersonalName"));

    return authorities;
  }

  private static Metadata metadata(String createdDate, String updatedDate) {
    return new Metadata().createdDate(createdDate).updatedDate(updatedDate);
  }
}
