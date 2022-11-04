package org.folio.search.controller;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.folio.search.sample.SampleAuthorities.getAuthorityNaturalId;
import static org.folio.search.sample.SampleAuthorities.getAuthoritySampleAsMap;
import static org.folio.search.sample.SampleAuthorities.getAuthoritySampleId;
import static org.folio.search.sample.SampleAuthorities.getAuthoritySourceFileId;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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

  @BeforeAll
  static void prepare() {
    setUpTenant(Authority.class, 30, getAuthoritySampleAsMap());
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
    var response = doSearchByAuthorities(prepareQuery(query, value)).andExpect(jsonPath("$.totalRecords", is(30)));
    var actual = parseResponse(response, AuthoritySearchResult.class);
    assertThat(actual.getAuthorities()).asList().containsOnly(
      authority("Personal Name", AUTHORIZED_TYPE, "Gary A. Wills", 0),
      authority("Personal Name", REFERENCE_TYPE, "a sft personal name", null),
      authority("Personal Name", AUTH_REF_TYPE, "a saft personal name", null),

      authority("Personal Name", AUTHORIZED_TYPE, "a personal title", 0),
      authority("Personal Name", REFERENCE_TYPE, "a sft personal title", null),
      authority("Personal Name", AUTH_REF_TYPE, "a saft personal title", null),

      authority("Corporate Name", AUTHORIZED_TYPE, "a corporate name", 0),
      authority("Corporate Name", REFERENCE_TYPE, "a sft corporate name", null),
      authority("Corporate Name", AUTH_REF_TYPE, "a saft corporate name", null),

      authority("Corporate Name", AUTHORIZED_TYPE, "a corporate title", 0),
      authority("Corporate Name", REFERENCE_TYPE, "a sft corporate title", null),
      authority("Corporate Name", AUTH_REF_TYPE, "a saft corporate title", null),

      authority("Conference Name", AUTHORIZED_TYPE, "a conference name", 0),
      authority("Conference Name", REFERENCE_TYPE, "a sft conference name", null),
      authority("Conference Name", AUTH_REF_TYPE, "a saft conference name", null),

      authority("Conference Name", AUTHORIZED_TYPE, "a conference title", 0),
      authority("Conference Name", REFERENCE_TYPE, "a sft conference title", null),
      authority("Conference Name", AUTH_REF_TYPE, "a saft conference title", null),

      authority("Geographic Name", AUTHORIZED_TYPE, "a geographic name", 0),
      authority("Geographic Name", REFERENCE_TYPE, "a sft geographic name", null),
      authority("Geographic Name", AUTH_REF_TYPE, "a saft geographic name", null),

      authority("Uniform Title", AUTHORIZED_TYPE, "an uniform title", 0),
      authority("Uniform Title", REFERENCE_TYPE, "a sft uniform title", null),
      authority("Uniform Title", AUTH_REF_TYPE, "a saft uniform title", null),

      authority("Topical", AUTHORIZED_TYPE, "a topical term", 0),
      authority("Topical", REFERENCE_TYPE, "a sft topical term", null),
      authority("Topical", AUTH_REF_TYPE, "a saft topical term", null),

      authority("Genre", AUTHORIZED_TYPE, "a genre term", 0),
      authority("Genre", REFERENCE_TYPE, "a sft genre term", null),
      authority("Genre", AUTH_REF_TYPE, "a saft genre term", null)
    );
  }

  private static Stream<Arguments> testDataProvider() {
    return Stream.of(
      arguments("keyword == {value}", "\"a personal title\""),
      arguments("keyword all {value}", "\"a sft personal title\""),
      arguments("keyword all {value}", "\"a saft personal title\""),
      arguments("keyword == {value}", "\"a corporate title\""),
      arguments("keyword all {value}", "\"a sft corporate title\""),
      arguments("keyword all {value}", "\"a saft corporate title\""),
      arguments("keyword == {value}", "\"a conference title\""),
      arguments("keyword all {value}", "\"a sft conference title\""),
      arguments("keyword all {value}", "\"a saft conference title\""),
      arguments("keyword == {value}", "\"a geographic name\""),
      arguments("keyword all {value}", "\"a sft geographic name\""),
      arguments("keyword all {value}", "\"a saft geographic name\""),
      arguments("keyword == {value}", "\"an uniform title\""),
      arguments("keyword all {value}", "\"a sft uniform title\""),
      arguments("keyword all {value}", "\"a saft uniform title\""),
      arguments("keyword == {value}", "\"a topical term\""),
      arguments("keyword all {value}", "\"a sft topical term\""),
      arguments("keyword all {value}", "\"a saft topical term\""),
      arguments("keyword == {value}", "\"a genre term\""),
      arguments("keyword all {value}", "\"a sft genre term\""),
      arguments("keyword all {value}", "\"a saft genre term\""),

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

      arguments("personalNameTitle all {value}", "\"personal title\""),
      arguments("personalNameTitle == {value}", "\"a personal title\""),
      arguments("sftPersonalNameTitle all {value}", "\"personal title\""),
      arguments("sftPersonalNameTitle == {value}", "\"a sft personal title\""),
      arguments("saftPersonalNameTitle all {value}", "\"a saft personal title\""),
      arguments("saftPersonalNameTitle == {value}", "\"a saft personal title\""),

      arguments("corporateName = {value}", "\"corporate\""),
      arguments("corporateName == {value}", "\"a corporate name\""),
      arguments("corporateName == {value}", "\"*corporat*\""),
      arguments("sftCorporateName = {value}", "\"corporate name\""),
      arguments("sftCorporateName == {value}", "\"sft corporate\""),
      arguments("saftCorporateName = {value} ", "\"name saft\""),
      arguments("saftCorporateName == {value} ", "\"saft corporate name\""),

      arguments("corporateNameTitle all {value}", "\"corporate title\""),
      arguments("corporateNameTitle == {value}", "\"a corporate title\""),
      arguments("sftCorporateNameTitle all {value}", "\"corporate title\""),
      arguments("sftCorporateNameTitle == {value}", "\"a sft corporate title\""),
      arguments("saftCorporateNameTitle all {value}", "\"corporate title\""),
      arguments("saftCorporateNameTitle == {value}", "\"a saft corporate title\""),

      arguments("meetingName = {value}", "\"conference\""),
      arguments("meetingName == {value}", "\"a conference name\""),
      arguments("meetingName == {value}", "\"*onference*\""),
      arguments("sftMeetingName = {value}", "\"conference name\""),
      arguments("sftMeetingName == {value}", "\"sft conference\""),
      arguments("saftMeetingName = {value} ", "\"conference saft\""),
      arguments("saftMeetingName == {value} ", "\"saft conference name\""),

      arguments("meetingNameTitle all {value}", "\"conference title\""),
      arguments("meetingNameTitle == {value}", "\"a conference title\""),
      arguments("sftMeetingNameTitle all {value}", "\"conference title\""),
      arguments("sftMeetingNameTitle == {value}", "\"a sft conference title\""),
      arguments("saftMeetingNameTitle all {value}", "\"conference title\""),
      arguments("saftMeetingNameTitle == {value}", "\"a saft conference title\""),

      arguments("geographicName = {value}", "\"geographic\""),
      arguments("geographicName == {value}", "\"a geographic name\""),
      arguments("geographicName == {value}", "\"*graph*\""),
      arguments("sftGeographicName = {value}", "\"geographic name\""),
      arguments("sftGeographicName == {value}", "\"sft geographic\""),
      arguments("saftGeographicName = {value} ", "\"geographic saft\""),
      arguments("saftGeographicName == {value} ", "\"saft geographic name\""),

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

      arguments(specifyCommonField("lccn = {value}"), "3745-1086"),
      arguments(specifyCommonField("lccn = {value}"), "3745*"),

      arguments(specifyCommonField("identifiers.value == {value}"), "authority-identifier"),
      arguments(specifyCommonField("identifiers.value all {value}"), "311417*"),
      arguments(specifyCommonField("identifiers.value all {value}"), "*1086"),
      arguments(specifyCommonField("identifiers.value all ({value})"),
        "authority-identifier or 3114176276 or 0000-0000"),
      arguments(specifyCommonField("identifiers.value all ({value})"),
        "authority-identifier and 3114176276 and 3745-1086"),
      arguments(specifyCommonField("identifiers.identifierTypeId == {value}"), "d6f3c637-3969-4dc6-9146-6371063f049e"),
      arguments(specifyCommonField("identifiers.identifierTypeId == caaf835f-2037-46c5-9c62-71abaaaa78c5 "
        + "and identifiers.value == {value}"), "authority-identifier"),

      arguments(specifyCommonField("subjectHeadings all {value}"), "\"a subject heading\""),
      arguments(specifyCommonField("subjectHeadings == {value}"), "\"a sub*\"")
    );
  }

  private static String specifyCommonField(String query) {
    return query + " and sftPersonalName==\"*personal name\"";
  }

  private static Authority authority(String headingType, String authRefType, String headingRef,
                                     Integer numberOfTitles) {
    return new Authority()
      .id(getAuthoritySampleId())
      .sourceFileId(getAuthoritySourceFileId())
      .naturalId(getAuthorityNaturalId())
      .headingType(headingType)
      .authRefType(authRefType)
      .headingRef(headingRef)
      .numberOfTitles(numberOfTitles);
  }
}
