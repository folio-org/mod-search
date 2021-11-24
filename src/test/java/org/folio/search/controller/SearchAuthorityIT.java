package org.folio.search.controller;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.folio.search.sample.SampleAuthorities.getAuthoritySample;
import static org.folio.search.sample.SampleAuthorities.getAuthoritySampleAsMap;
import static org.folio.search.sample.SampleAuthorities.getAuthoritySampleId;
import static org.folio.search.utils.AuthoritySearchUtils.expectedAuthority;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthoritySearchResult;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

@IntegrationTest
class SearchAuthorityIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(Authority.class, 21, getAuthoritySampleAsMap());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @MethodSource("testDataProvider")
  @DisplayName("search by authorities (single authority found)")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}''")
  void searchByAuthorities_parameterized(String query, String value) throws Exception {
    doSearchByAuthorities(prepareQuery(query, value))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.authorities[0].id", is(getAuthoritySampleId())));
  }

  @CsvSource({
    "cql.allRecords=1,",
    "id={value}, \"\"",
    "id=={value}, 55294032-fcf6-45cc-b6da-4420a61ef72c",
    "id=={value}, 55294032-fcf6-45cc-b6da-*"
  })
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}''")
  @DisplayName("search by authorities (check that they are divided correctly)")
  void searchByAuthorities_parameterized_all(String query, String value) throws Exception {
    var response = doSearchByAuthorities(prepareQuery(query, value)).andExpect(jsonPath("$.totalRecords", is(21)));
    var actual = parseResponse(response, AuthoritySearchResult.class);
    var source = getAuthoritySample();
    assertThat(actual.getAuthorities()).isEqualTo(List.of(
      expectedAuthority(source, visibleSearchFields("Personal Name"), "personalName"),
      expectedAuthority(source, visibleSearchFields("Personal Name"), "sftPersonalName[0]"),
      expectedAuthority(source, visibleSearchFields("Other"), "saftPersonalName[0]"),
      expectedAuthority(source, visibleSearchFields("Corporate Name"), "corporateName"),
      expectedAuthority(source, visibleSearchFields("Corporate Name"), "sftCorporateName[0]"),
      expectedAuthority(source, visibleSearchFields("Other"), "saftCorporateName[0]"),
      expectedAuthority(source, visibleSearchFields("Meeting Name"), "meetingName"),
      expectedAuthority(source, visibleSearchFields("Meeting Name"), "sftMeetingName[0]"),
      expectedAuthority(source, visibleSearchFields("Other"), "saftMeetingName[0]"),
      expectedAuthority(source, visibleSearchFields("Geographic Name"), "geographicName"),
      expectedAuthority(source, visibleSearchFields("Geographic Name"), "sftGeographicTerm[0]"),
      expectedAuthority(source, visibleSearchFields("Other"), "saftGeographicTerm[0]"),
      expectedAuthority(source, visibleSearchFields("Uniform Title"), "uniformTitle"),
      expectedAuthority(source, visibleSearchFields("Uniform Title"), "sftUniformTitle[0]"),
      expectedAuthority(source, visibleSearchFields("Other"), "saftUniformTitle[0]"),
      expectedAuthority(source, visibleSearchFields("Topical"), "topicalTerm"),
      expectedAuthority(source, visibleSearchFields("Topical"), "sftTopicalTerm[0]"),
      expectedAuthority(source, visibleSearchFields("Other"), "saftTopicalTerm[0]"),
      expectedAuthority(source, visibleSearchFields("Genre"), "genreTerm"),
      expectedAuthority(source, visibleSearchFields("Genre"), "sftGenreTerm[0]"),
      expectedAuthority(source, visibleSearchFields("Other"), "saftGenreTerm[0]")
    ));
  }

  private static Stream<Arguments> testDataProvider() {
    return Stream.of(
      arguments("personalName all {value}", "\"Gary A. Wills\""),
      arguments("personalName all {value}", "gary"),
      arguments("personalName == {value}", "\"gary a.*\""),

      arguments("genreTerm all {value}", "\"a genre term\""),
      arguments("genreTerm all {value}", "genre"),
      arguments("genreTerm == {value}", "\"a gen*\""),
      arguments("genreTerm == {value} and headingType==\"Genre\"", "\"a gen*\""),
      arguments("sftGenreTerm = {value}", "\"sft genre term\""),
      arguments("sftGenreTerm == {value}", "\"sft genre term\""),
      arguments("sftGenreTerm == {value}", "\"*gen*\""),
      arguments("saftGenreTerm = {value}", "\"saft term\""),
      arguments("saftGenreTerm == {value}", "\"*saft gen*\"")
    );
  }

  private static Map<String, Object> visibleSearchFields(String headingType) {
    return mapOf("headingType", headingType);
  }
}
