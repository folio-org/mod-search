package org.folio.search.controller;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.folio.search.sample.SampleAuthorities.getAuthoritySampleAsMap;
import static org.folio.search.sample.SampleAuthorities.getAuthoritySampleId;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
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
    assertThat(actual.getAuthorities()).isEqualTo(List.of(
      authority("Personal Name", AUTHORIZED_TYPE, "Gary A. Wills"),
      authority("Personal Name", REFERENCE_TYPE, "a sft personal name"),
      authority(OTHER_HEADING_TYPE, AUTH_REF_TYPE, "a saft personal name"),

      authority("Corporate Name", AUTHORIZED_TYPE, "a corporate name"),
      authority("Corporate Name", REFERENCE_TYPE, "a sft corporate name"),
      authority(OTHER_HEADING_TYPE, AUTH_REF_TYPE, "a saft corporate name"),

      authority("Conference Name", AUTHORIZED_TYPE, "a conference name"),
      authority("Conference Name", REFERENCE_TYPE, "a sft conference name"),
      authority(OTHER_HEADING_TYPE, AUTH_REF_TYPE, "a saft conference name"),

      authority("Geographic Name", AUTHORIZED_TYPE, "a geographic name"),
      authority("Geographic Name", REFERENCE_TYPE, "a sft geographic name"),
      authority(OTHER_HEADING_TYPE, AUTH_REF_TYPE, "a saft geographic name"),

      authority("Uniform Title", AUTHORIZED_TYPE, "an uniform title"),
      authority("Uniform Title", REFERENCE_TYPE, "a sft uniform title"),
      authority(OTHER_HEADING_TYPE, AUTH_REF_TYPE, "a saft uniform title"),

      authority("Topical", AUTHORIZED_TYPE, "a topical term"),
      authority("Topical", REFERENCE_TYPE, "a sft topical term"),
      authority(OTHER_HEADING_TYPE, AUTH_REF_TYPE, "a saft topical term"),

      authority("Genre", AUTHORIZED_TYPE, "a genre term"),
      authority("Genre", REFERENCE_TYPE, "a sft genre term"),
      authority(OTHER_HEADING_TYPE, AUTH_REF_TYPE, "a saft genre term")
    ));
  }

  private static Stream<Arguments> testDataProvider() {
    return Stream.of(
      arguments("personalName all {value}", "\"Gary A. Wills\""),
      arguments("personalName all {value}", "gary"),
      arguments("personalName == {value}", "\"gary a.*\""),
      arguments("personalName == {value} and headingType==\"Personal Name\"", "gary"),
      arguments("personalName == {value} and authRefType==\"Authorized\"", "gary"),
      arguments("sftPersonalName = {value}", "\"personal sft name\""),
      arguments("sftPersonalName == {value}", "\"sft personal name\""),
      arguments("sftPersonalName == {value}", "\"*persona*\""),
      arguments("saftPersonalName = {value}", "\"saft name\""),
      arguments("saftPersonalName == {value}", "\"*saft persona*\""),

      arguments("corporateName = {value}", "\"corporate\""),
      arguments("corporateName == {value}", "\"a corporate name\""),
      arguments("corporateName == {value}", "\"*corporat*\""),
      arguments("sftCorporateName = {value}", "\"corporate name\""),
      arguments("sftCorporateName == {value}", "\"sft corporate\""),
      arguments("saftCorporateName = {value} ", "\"name saft\""),
      arguments("saftCorporateName == {value} ", "\"saft corporate name\""),

      arguments("meetingName = {value}", "\"conference\""),
      arguments("meetingName == {value}", "\"a conference name\""),
      arguments("meetingName == {value}", "\"*onference*\""),
      arguments("sftMeetingName = {value}", "\"conference name\""),
      arguments("sftMeetingName == {value}", "\"sft conference\""),
      arguments("saftMeetingName = {value} ", "\"conference saft\""),
      arguments("saftMeetingName == {value} ", "\"saft conference name\""),

      arguments("uniformTitle = {value}", "\"uniform\""),
      arguments("uniformTitle == {value}", "\"an uniform title\""),
      arguments("uniformTitle == {value}", "\"*nifor*\""),
      arguments("sftUniformTitle = {value}", "\"uniform title\""),
      arguments("sftUniformTitle == {value}", "\"sft uniform\""),
      arguments("saftUniformTitle = {value} ", "\"title saft\""),
      arguments("saftUniformTitle == {value} ", "\"saft uniform title\""),

      arguments("topicalTerm all {value}", "\"a topical term\""),
      arguments("topicalTerm all {value}", "topical"),
      arguments("topicalTerm == {value}", "\"a top*\""),
      arguments("topicalTerm == {value} and headingType==\"Topical\"", "\"a top*\""),
      arguments("sftTopicalTerm = {value}", "\"sft topical term\""),
      arguments("sftTopicalTerm == {value}", "\"sft topical term\""),
      arguments("sftTopicalTerm == {value}", "\"*top*\""),
      arguments("saftTopicalTerm = {value}", "\"saft term\""),
      arguments("saftTopicalTerm == {value}", "\"*saft top*\""),

      arguments("genreTerm all {value}", "\"a genre term\""),
      arguments("genreTerm all {value}", "genre"),
      arguments("genreTerm == {value}", "\"a gen*\""),
      arguments("genreTerm == {value} and headingType==\"Genre\"", "\"a gen*\""),
      arguments("sftGenreTerm = {value}", "\"sft genre term\""),
      arguments("sftGenreTerm == {value}", "\"sft genre term\""),
      arguments("sftGenreTerm == {value}", "\"*gen*\""),
      arguments("saftGenreTerm = {value}", "\"saft term\""),
      arguments("saftGenreTerm == {value}", "\"*saft gen*\""),

      arguments("subjectHeadings all {value} and personalName==\"Gary\"", "\"a subject heading\""),
      arguments("subjectHeadings all {value} and personalName==\"Gary\"", "subject"),
      arguments("subjectHeadings == {value} and personalName==\"Gary\"", "\"a sub*\"")
    );
  }

  private static Authority authority(String headingType, String authRefType, String headingRef) {
    return new Authority()
      .id(getAuthoritySampleId())
      .headingType(headingType)
      .authRefType(authRefType)
      .headingRef(headingRef);
  }
}
