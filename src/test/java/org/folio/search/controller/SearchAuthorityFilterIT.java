package org.folio.search.controller;

import static org.folio.search.utils.TestUtils.array;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Authority;
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

  private static final String[] IDS = array(
    "1353873c-0e5e-4d64-a2f9-6c444dc4cd46",
    "cc6bbc19-3f54-43c5-8736-b85688619641",
    "39a52d91-8dbb-4348-ab06-5c6115e600cd",
    "62f72eeb-ed5a-4619-b01f-1750d5528d25");

  @BeforeAll
  static void prepare() {
    setUpTenant(4, authorities());
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

  private static Stream<Arguments> filteredSearchQueriesProvider() {
    return Stream.of(
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

  private static Authority[] authorities() {
    var authorities = IntStream.range(0, 4)
      .mapToObj(i -> new Authority().id(IDS[i]).personalName("Resource" + i))
      .toArray(Authority[]::new);

    authorities[0]
      .metadata(metadata("2021-03-01T00:00:00.000+00:00", "2021-03-05T12:30:00.000+00:00"));

    authorities[1]
      .metadata(metadata("2021-03-10T01:00:00.000+00:00", "2021-03-12T15:40:00.000+00:00"));

    authorities[2]
      .metadata(metadata("2021-03-08T15:00:00.000+00:00", "2021-03-15T22:30:00.000+00:00"));

    authorities[3]
      .metadata(metadata("2021-03-15T12:00:00.000+00:00", "2021-03-15T12:00:00.000+00:00"));

    return authorities;
  }

  private static Metadata metadata(String createdDate, String updatedDate) {
    return new Metadata().createdDate(createdDate).updatedDate(updatedDate);
  }
}
