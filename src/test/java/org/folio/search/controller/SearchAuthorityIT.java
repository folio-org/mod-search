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

  private static final String AUTHORIZED_TYPE = "Authorized";
  private static final String REFERENCE_TYPE = "Reference";
  private static final String AUTH_REF_TYPE = "Auth/Ref";
  private static final String OTHER_HEADING_TYPE = "Other";

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
      expectedAuthority(source, visibleSearchFields("Personal Name", AUTHORIZED_TYPE), "personalName"),
      expectedAuthority(source, visibleSearchFields("Personal Name", REFERENCE_TYPE), "sftPersonalName[0]"),
      expectedAuthority(source, visibleSearchFields(OTHER_HEADING_TYPE, AUTH_REF_TYPE), "saftPersonalName[0]"),
      expectedAuthority(source, visibleSearchFields("Corporate Name", AUTHORIZED_TYPE), "corporateName"),
      expectedAuthority(source, visibleSearchFields("Corporate Name", REFERENCE_TYPE), "sftCorporateName[0]"),
      expectedAuthority(source, visibleSearchFields(OTHER_HEADING_TYPE, AUTH_REF_TYPE), "saftCorporateName[0]"),
      expectedAuthority(source, visibleSearchFields("Meeting Name", AUTHORIZED_TYPE), "meetingName"),
      expectedAuthority(source, visibleSearchFields("Meeting Name", REFERENCE_TYPE), "sftMeetingName[0]"),
      expectedAuthority(source, visibleSearchFields(OTHER_HEADING_TYPE, AUTH_REF_TYPE), "saftMeetingName[0]"),
      expectedAuthority(source, visibleSearchFields("Geographic Name", AUTHORIZED_TYPE), "geographicName"),
      expectedAuthority(source, visibleSearchFields("Geographic Name", REFERENCE_TYPE), "sftGeographicTerm[0]"),
      expectedAuthority(source, visibleSearchFields(OTHER_HEADING_TYPE, AUTH_REF_TYPE), "saftGeographicTerm[0]"),
      expectedAuthority(source, visibleSearchFields("Uniform Title", AUTHORIZED_TYPE), "uniformTitle"),
      expectedAuthority(source, visibleSearchFields("Uniform Title", REFERENCE_TYPE), "sftUniformTitle[0]"),
      expectedAuthority(source, visibleSearchFields(OTHER_HEADING_TYPE, AUTH_REF_TYPE), "saftUniformTitle[0]"),
      expectedAuthority(source, visibleSearchFields("Topical", AUTHORIZED_TYPE), "topicalTerm"),
      expectedAuthority(source, visibleSearchFields("Topical", REFERENCE_TYPE), "sftTopicalTerm[0]"),
      expectedAuthority(source, visibleSearchFields(OTHER_HEADING_TYPE, AUTH_REF_TYPE), "saftTopicalTerm[0]"),
      expectedAuthority(source, visibleSearchFields("Genre", AUTHORIZED_TYPE), "genreTerm"),
      expectedAuthority(source, visibleSearchFields("Genre", REFERENCE_TYPE), "sftGenreTerm[0]"),
      expectedAuthority(source, visibleSearchFields(OTHER_HEADING_TYPE, AUTH_REF_TYPE), "saftGenreTerm[0]")
    ));
  }

  private static Stream<Arguments> testDataProvider() {
    return Stream.of(
      arguments("personalName all {value}", "\"Gary A. Wills\""),
      arguments("personalName all {value}", "gary"),
      arguments("personalName == {value}", "\"gary a.*\""),
      arguments("personalName == {value} and headingType==\"Personal Name\"", "gary"),
      arguments("personalName == {value} and authRefType==\"Authorized\"", "gary")
    );
  }

  private static Map<String, Object> visibleSearchFields(String headingType, String authRefType) {
    return mapOf("headingType", headingType, "authRefType", authRefType);
  }
}
