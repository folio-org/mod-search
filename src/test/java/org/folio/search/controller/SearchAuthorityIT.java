package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.folio.search.sample.SampleAuthorities.getAuthorityNaturalId;
import static org.folio.search.sample.SampleAuthorities.getAuthoritySampleAsMap;
import static org.folio.search.sample.SampleAuthorities.getAuthoritySampleId;
import static org.folio.search.sample.SampleAuthorities.getAuthoritySourceFileId;
import static org.folio.search.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.folio.search.domain.dto.AlternativeTitle;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthoritySearchResult;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.SeriesItem;
import org.folio.search.domain.dto.Subject;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
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
    setUpTenant(Authority.class, 48, getAuthoritySampleAsMap());

    //set up linked instances
    var instance1 = new Instance().id(randomId()).title("test-resource")
      .subjects(List.of(new Subject().value("s1").authorityId(getAuthoritySampleId())));
    var instance2 = new Instance().id(randomId()).title("test-resource")
      .contributors(List.of(new Contributor().name("c1").authorityId(getAuthoritySampleId())));
    var instance3 = new Instance().id(randomId()).title("test-resource")
      .alternativeTitles(List.of(new AlternativeTitle().alternativeTitle("a1").authorityId(getAuthoritySampleId())));
    var instance4 = new Instance().id(randomId()).title("test-resource")
      .series(List.of(new SeriesItem().value("s1").authorityId(getAuthoritySampleId())));

    inventoryApi.createInstance(TENANT_ID, instance1);
    inventoryApi.createInstance(TENANT_ID, instance2);
    inventoryApi.createInstance(TENANT_ID, instance3);
    inventoryApi.createInstance(TENANT_ID, instance4);
    checkThatEventsFromKafkaAreIndexed(TENANT_ID, instanceSearchPath(), 4, emptyList());
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


  @MethodSource("testCaseInsensitiveDataProvider")
  @DisplayName("search by authorities (single authority found)")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}''")
  void searchByAuthoritiesCaseInsensitive_parameterized(String query, String value) throws Exception {
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
    var response = doSearchByAuthorities(prepareQuery(query, value)).andExpect(jsonPath("$.totalRecords", is(48)));
    var actual = parseResponse(response, AuthoritySearchResult.class);
    assertThat(actual.getAuthorities()).asInstanceOf(InstanceOfAssertFactories.LIST).containsOnly(
      authority("Personal Name", AUTHORIZED_TYPE, "Gary A. Wills", 4),
      authority("Personal Name", REFERENCE_TYPE, "a sft personal name", null),
      authority("Personal Name", AUTH_REF_TYPE, "a saft personal name", null),

      authority("Personal Name", AUTHORIZED_TYPE, "a personal title", 4),
      authority("Personal Name", REFERENCE_TYPE, "a sft personal title", null),
      authority("Personal Name", AUTH_REF_TYPE, "a saft personal title", null),

      authority("Corporate Name", AUTHORIZED_TYPE, "a corporate name", 4),
      authority("Corporate Name", REFERENCE_TYPE, "a sft corporate name", null),
      authority("Corporate Name", AUTH_REF_TYPE, "a saft corporate name", null),

      authority("Corporate Name", AUTHORIZED_TYPE, "a corporate title", 4),
      authority("Corporate Name", REFERENCE_TYPE, "a sft corporate title", null),
      authority("Corporate Name", AUTH_REF_TYPE, "a saft corporate title", null),

      authority("Conference Name", AUTHORIZED_TYPE, "a conference name", 4),
      authority("Conference Name", REFERENCE_TYPE, "a sft conference name", null),
      authority("Conference Name", AUTH_REF_TYPE, "a saft conference name", null),

      authority("Conference Name", AUTHORIZED_TYPE, "a conference title", 4),
      authority("Conference Name", REFERENCE_TYPE, "a sft conference title", null),
      authority("Conference Name", AUTH_REF_TYPE, "a saft conference title", null),

      authority("Geographic Name", AUTHORIZED_TYPE, "a geographic name", 4),
      authority("Geographic Name", REFERENCE_TYPE, "a sft geographic name", null),
      authority("Geographic Name", AUTH_REF_TYPE, "a saft geographic name", null),

      authority("Uniform Title", AUTHORIZED_TYPE, "an uniform title", 4),
      authority("Uniform Title", REFERENCE_TYPE, "a sft uniform title", null),
      authority("Uniform Title", AUTH_REF_TYPE, "a saft uniform title", null),

      authority("Named Event", AUTHORIZED_TYPE, "a named event", 4),
      authority("Named Event", REFERENCE_TYPE, "a sft named event", null),
      authority("Named Event", AUTH_REF_TYPE, "a saft named event", null),

      authority("Topical", AUTHORIZED_TYPE, "a topical term", 4),
      authority("Topical", REFERENCE_TYPE, "a sft topical term", null),
      authority("Topical", AUTH_REF_TYPE, "a saft topical term", null),

      authority("Genre", AUTHORIZED_TYPE, "a genre term", 4),
      authority("Genre", REFERENCE_TYPE, "a sft genre term", null),
      authority("Genre", AUTH_REF_TYPE, "a saft genre term", null),

      authority("Chronological Term", AUTHORIZED_TYPE, "a chron term", 4),
      authority("Chronological Term", REFERENCE_TYPE, "a sft chron term", null),
      authority("Chronological Term", AUTH_REF_TYPE, "a saft chron term", null),

      authority("Medium of Performance Term", AUTHORIZED_TYPE, "a medium perf term", 4),
      authority("Medium of Performance Term", REFERENCE_TYPE, "a sft medium perf term", null),
      authority("Medium of Performance Term", AUTH_REF_TYPE, "a saft medium perf term", null),

      authority("Geographic Subdivision", AUTHORIZED_TYPE, "a geographic subdivision", 4),
      authority("Geographic Subdivision", REFERENCE_TYPE, "a sft geographic subdivision", null),
      authority("Geographic Subdivision", AUTH_REF_TYPE, "a saft geographic subdivision", null),

      authority("Chronological Subdivision", AUTHORIZED_TYPE, "a chron subdivision", 4),
      authority("Chronological Subdivision", REFERENCE_TYPE, "a sft chron subdivision", null),
      authority("Chronological Subdivision", AUTH_REF_TYPE, "a saft chron subdivision", null),

      authority("Form Subdivision", AUTHORIZED_TYPE, "a form subdivision", 4),
      authority("Form Subdivision", REFERENCE_TYPE, "a sft form subdivision", null),
      authority("Form Subdivision", AUTH_REF_TYPE, "a saft form subdivision", null)
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
      arguments("keyword == {value}", "\"a named event\""),
      arguments("keyword all {value}", "\"a sft named event\""),
      arguments("keyword all {value}", "\"a saft named event\""),
      arguments("keyword == {value}", "\"a topical term\""),
      arguments("keyword all {value}", "\"a sft topical term\""),
      arguments("keyword all {value}", "\"a saft topical term\""),
      arguments("keyword == {value}", "\"a genre term\""),
      arguments("keyword all {value}", "\"a sft genre term\""),
      arguments("keyword all {value}", "\"a saft genre term\""),
      arguments("keyword == {value}", "\"a chron term\""),
      arguments("keyword all {value}", "\"a sft chron term\""),
      arguments("keyword all {value}", "\"a saft chron term\""),
      arguments("keyword == {value}", "\"a medium perf term\""),
      arguments("keyword all {value}", "\"a sft medium perf term\""),
      arguments("keyword all {value}", "\"a saft medium perf term\""),
      arguments("keyword == {value}", "\"a geographic subdivision\""),
      arguments("keyword all {value}", "\"a sft geographic subdivision\""),
      arguments("keyword all {value}", "\"a saft geographic subdivision\""),
      arguments("keyword == {value}", "\"a chron subdivision\""),
      arguments("keyword all {value}", "\"a sft chron subdivision\""),
      arguments("keyword all {value}", "\"a saft chron subdivision\""),
      arguments("keyword == {value}", "\"a form subdivision\""),
      arguments("keyword all {value}", "\"a sft form subdivision\""),
      arguments("keyword all {value}", "\"a saft form subdivision\""),

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

      arguments("namedEvent all {value}", "\"a named event\""),
      arguments("namedEvent all {value}", "named"),
      arguments("namedEvent == {value}", "\"a nam*\""),
      arguments("namedEvent == {value} and headingType==\"Named Event\"", "\"a nam*\""),
      arguments("sftNamedEvent = {value}", "\"sft named event\""),
      arguments("sftNamedEvent == {value}", "\"sft named event\""),
      arguments("sftNamedEvent == {value}", "\"*nam*\""),
      arguments("saftNamedEvent = {value}", "\"saft event\""),
      arguments("saftNamedEvent == {value}", "\"*saft nam*\""),

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

      arguments("chronTerm all {value}", "\"a chron term\""),
      arguments("chronTerm all {value}", "chron"),
      arguments("chronTerm == {value}", "\"a chr*\""),
      arguments("chronTerm == {value} and headingType==\"Chronological Term\"", "\"a chr*\""),
      arguments("sftChronTerm = {value}", "\"sft chron term\""),
      arguments("sftChronTerm == {value}", "\"sft chron term\""),
      arguments("sftChronTerm == {value}", "\"*chron*\""),
      arguments("saftChronTerm = {value}", "\"saft chron term\""),
      arguments("saftChronTerm == {value}", "\"*saft chron*\""),

      arguments("mediumPerfTerm all {value}", "\"a medium perf term\""),
      arguments("mediumPerfTerm all {value}", "medium"),
      arguments("mediumPerfTerm == {value}", "\"a med*\""),
      arguments("mediumPerfTerm == {value} and headingType==\"Medium of Performance Term\"", "\"a med*\""),
      arguments("sftMediumPerfTerm = {value}", "\"sft medium perf term\""),
      arguments("sftMediumPerfTerm == {value}", "\"sft medium perf term\""),
      arguments("sftMediumPerfTerm == {value}", "\"*medium*\""),
      arguments("saftMediumPerfTerm = {value}", "\"saft medium perf term\""),
      arguments("saftMediumPerfTerm == {value}", "\"*saft medium*\""),

      arguments("geographicSubdivision all {value}", "\"a geographic subdivision\""),
      arguments("geographicSubdivision all {value}", "geographic"),
      arguments("geographicSubdivision == {value}", "\"a geo*\""),
      arguments("geographicSubdivision == {value} and headingType==\"Geographic Subdivision\"", "\"a geo*\""),
      arguments("sftGeographicSubdivision = {value}", "\"sft geographic subdivision\""),
      arguments("sftGeographicSubdivision == {value}", "\"sft geographic subdivision\""),
      arguments("sftGeographicSubdivision == {value}", "\"*geo*\""),
      arguments("saftGeographicSubdivision = {value}", "\"saft geographic subdivision\""),
      arguments("saftGeographicSubdivision == {value}", "\"*saft geo*\""),

      arguments("chronSubdivision all {value}", "\"a chron subdivision\""),
      arguments("chronSubdivision all {value}", "chron"),
      arguments("chronSubdivision == {value}", "\"a chr*\""),
      arguments("chronSubdivision == {value} and headingType==\"Chronological Subdivision\"", "\"a chr*\""),
      arguments("sftChronSubdivision = {value}", "\"sft chron subdivision\""),
      arguments("sftChronSubdivision == {value}", "\"sft chron subdivision\""),
      arguments("sftChronSubdivision == {value}", "\"*chron*\""),
      arguments("saftChronSubdivision = {value}", "\"saft chron subdivision\""),
      arguments("saftChronSubdivision == {value}", "\"*saft chron*\""),

      arguments("formSubdivision all {value}", "\"a form subdivision\""),
      arguments("formSubdivision all {value}", "form"),
      arguments("formSubdivision == {value}", "\"a for*\""),
      arguments("formSubdivision == {value} and headingType==\"Form Subdivision\"", "\"a for*\""),
      arguments("sftFormSubdivision = {value}", "\"sft form subdivision\""),
      arguments("sftFormSubdivision == {value}", "\"sft form subdivision\""),
      arguments("sftFormSubdivision == {value}", "\"*form*\""),
      arguments("saftFormSubdivision = {value}", "\"saft form subdivision\""),
      arguments("saftFormSubdivision == {value}", "\"*saft form*\""),

      // search by lccn
      arguments(specifyCommonField("lccn = {value}"), "2003065165"),
      arguments(specifyCommonField("lccn = {value}"), "*65165"),
      arguments(specifyCommonField("lccn = {value}"), "n 2003075732"),
      arguments(specifyCommonField("lccn = {value}"), "N2003075732"),
      arguments(specifyCommonField("lccn = {value}"), "*75732"),
      arguments(specifyCommonField("lccn = {value}"), "20030*"),

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

  private static Stream<Arguments> testCaseInsensitiveDataProvider() {
    return Stream.of(
      arguments("keyword == {value}", "\"A PERSONAL TITLE\""),
      arguments("keyword all {value}", "\"A SFT PERSONAL TITLE\""),
      arguments("keyword == {value}", "\"A CORPORATE TITLE\""),
      arguments("keyword all {value}", "\"A SFT CORPORATE TITLE\""),

      arguments("personalName all {value}", "\"GARY A. WILLS\""),
      arguments("personalName all {value}", "GARY"),
      arguments("personalName == {value}", "\"GARY A.*\""),
      arguments("sftPersonalName = {value}", "\"PERSONAL SFT NAME\""),
      arguments("sftPersonalName == {value}", "\"SFT PERSONAL NAME\""),
      arguments("sftPersonalName == {value}", "\"*PERSONA*\""),
      arguments("saftPersonalName = {value}", "\"SAFT NAME\""),
      arguments("saftPersonalName == {value}", "\"*SAFT PERSONA*\""),

      arguments("personalNameTitle all {value}", "\"PERSONAL TITLE\""),
      arguments("personalNameTitle == {value}", "\"A PERSONAL TITLE\""),
      arguments("sftPersonalNameTitle all {value}", "\"PERSONAL TITLE\""),
      arguments("sftPersonalNameTitle == {value}", "\"A SFT PERSONAL TITLE\""),
      arguments("saftPersonalNameTitle all {value}", "\"A SAFT PERSONAL TITLE\""),
      arguments("saftPersonalNameTitle == {value}", "\"A SAFT PERSONAL TITLE\""),

      arguments("corporateName = {value}", "\"CORPORATE\""),
      arguments("corporateName == {value}", "\"A CORPORATE NAME\""),
      arguments("corporateName == {value}", "\"*CORPORAT*\""),
      arguments("sftCorporateName = {value}", "\"CORPORATE NAME\""),
      arguments("sftCorporateName == {value}", "\"SFT CORPORATE\""),
      arguments("saftCorporateName = {value} ", "\"NAME SAFT\""),
      arguments("saftCorporateName == {value} ", "\"SAFT CORPORATE NAME\""),

      arguments("corporateNameTitle all {value}", "\"CORPORATE TITLE\""),
      arguments("corporateNameTitle == {value}", "\"A CORPORATE TITLE\""),
      arguments("sftCorporateNameTitle all {value}", "\"CORPORATE TITLE\""),
      arguments("sftCorporateNameTitle == {value}", "\"A SFT CORPORATE TITLE\""),
      arguments("saftCorporateNameTitle all {value}", "\"CORPORATE TITLE\""),
      arguments("saftCorporateNameTitle == {value}", "\"A SAFT CORPORATE TITLE\""),

      arguments("meetingName = {value}", "\"CONFERENCE\""),
      arguments("meetingName == {value}", "\"A CONFERENCE NAME\""),
      arguments("meetingName == {value}", "\"*ONFERENCE*\""),
      arguments("sftMeetingName = {value}", "\"CONFERENCE NAME\""),
      arguments("sftMeetingName == {value}", "\"SFT CONFERENCE\""),
      arguments("saftMeetingName = {value} ", "\"CONFERENCE SAFT\""),
      arguments("saftMeetingName == {value} ", "\"SAFT CONFERENCE NAME\""),

      arguments("meetingNameTitle all {value}", "\"CONFERENCE TITLE\""),
      arguments("meetingNameTitle == {value}", "\"A CONFERENCE TITLE\""),
      arguments("sftMeetingNameTitle all {value}", "\"CONFERENCE TITLE\""),
      arguments("sftMeetingNameTitle == {value}", "\"A SFT CONFERENCE TITLE\""),
      arguments("saftMeetingNameTitle all {value}", "\"CONFERENCE TITLE\""),
      arguments("saftMeetingNameTitle == {value}", "\"A SAFT CONFERENCE TITLE\""),

      arguments("geographicName = {value}", "\"GEOGRAPHIC\""),
      arguments("geographicName == {value}", "\"A GEOGRAPHIC NAME\""),
      arguments("geographicName == {value}", "\"*GRAPH*\""),
      arguments("sftGeographicName = {value}", "\"GEOGRAPHIC NAME\""),
      arguments("sftGeographicName == {value}", "\"SFT GEOGRAPHIC\""),
      arguments("saftGeographicName = {value} ", "\"GEOGRAPHIC SAFT\""),
      arguments("saftGeographicName == {value} ", "\"SAFT GEOGRAPHIC NAME\""),

      arguments("uniformTitle = {value}", "\"UNIFORM\""),
      arguments("uniformTitle == {value}", "\"AN UNIFORM TITLE\""),
      arguments("uniformTitle == {value}", "\"*NIFOR*\""),
      arguments("sftUniformTitle = {value}", "\"UNIFORM TITLE\""),
      arguments("sftUniformTitle == {value}", "\"SFT UNIFORM\""),
      arguments("saftUniformTitle = {value} ", "\"TITLE SAFT\""),
      arguments("saftUniformTitle == {value} ", "\"SAFT UNIFORM TITLE\""),

      arguments("namedEvent all {value}", "\"A NAMED EVENT\""),
      arguments("namedEvent all {value}", "NAMED"),
      arguments("namedEvent == {value}", "\"A NAM*\""),
      arguments("sftNamedEvent = {value}", "\"SFT NAMED EVENT\""),
      arguments("sftNamedEvent == {value}", "\"*NAM*\""),
      arguments("saftNamedEvent = {value}", "\"SAFT EVENT\""),
      arguments("saftNamedEvent == {value}", "\"*SAFT NAM*\""),

      arguments("topicalTerm all {value}", "\"A TOPICAL TERM\""),
      arguments("topicalTerm all {value}", "TOPICAL"),
      arguments("topicalTerm == {value}", "\"A TOP*\""),
      arguments("sftTopicalTerm = {value}", "\"SFT TOPICAL TERM\""),
      arguments("sftTopicalTerm == {value}", "\"*TOP*\""),
      arguments("saftTopicalTerm = {value}", "\"SAFT TERM\""),
      arguments("saftTopicalTerm == {value}", "\"*SAFT TOP*\""),

      arguments("genreTerm all {value}", "\"A GENRE TERM\""),
      arguments("genreTerm all {value}", "GENRE"),
      arguments("genreTerm == {value}", "\"A GEN*\""),
      arguments("sftGenreTerm = {value}", "\"SFT GENRE TERM\""),
      arguments("sftGenreTerm == {value}", "\"*GEN*\""),
      arguments("saftGenreTerm = {value}", "\"SAFT TERM\""),
      arguments("saftGenreTerm == {value}", "\"*SAFT GEN*\""),

      arguments("chronTerm all {value}", "\"A CHRON TERM\""),
      arguments("chronTerm all {value}", "CHRON"),
      arguments("chronTerm == {value}", "\"A CHR*\""),
      arguments("sftChronTerm = {value}", "\"SFT CHRON TERM\""),
      arguments("sftChronTerm == {value}", "\"*CHRON*\""),
      arguments("saftChronTerm = {value}", "\"SAFT CHRON TERM\""),
      arguments("saftChronTerm == {value}", "\"*SAFT CHRON*\""),

      arguments("mediumPerfTerm all {value}", "\"A MEDIUM PERF TERM\""),
      arguments("mediumPerfTerm all {value}", "MEDIUM"),
      arguments("mediumPerfTerm == {value}", "\"A MED*\""),
      arguments("sftMediumPerfTerm = {value}", "\"SFT MEDIUM PERF TERM\""),
      arguments("sftMediumPerfTerm == {value}", "\"*MEDIUM*\""),
      arguments("saftMediumPerfTerm = {value}", "\"SAFT MEDIUM PERF TERM\""),
      arguments("saftMediumPerfTerm == {value}", "\"*SAFT MEDIUM*\""),

      arguments("geographicSubdivision all {value}", "\"A GEOGRAPHIC SUBDIVISION\""),
      arguments("geographicSubdivision all {value}", "GEOGRAPHIC"),
      arguments("geographicSubdivision == {value}", "\"A GEO*\""),
      arguments("sftGeographicSubdivision = {value}", "\"SFT GEOGRAPHIC SUBDIVISION\""),
      arguments("sftGeographicSubdivision == {value}", "\"*GEO*\""),
      arguments("saftGeographicSubdivision = {value}", "\"SAFT GEOGRAPHIC SUBDIVISION\""),
      arguments("saftGeographicSubdivision == {value}", "\"*SAFT GEO*\""),

      arguments("chronSubdivision all {value}", "\"A CHRON SUBDIVISION\""),
      arguments("chronSubdivision all {value}", "CHRON"),
      arguments("chronSubdivision == {value}", "\"A CHR*\""),
      arguments("sftChronSubdivision = {value}", "\"SFT CHRON SUBDIVISION\""),
      arguments("sftChronSubdivision == {value}", "\"*CHRON*\""),
      arguments("saftChronSubdivision = {value}", "\"SAFT CHRON SUBDIVISION\""),
      arguments("saftChronSubdivision == {value}", "\"*SAFT CHRON*\""),

      arguments("formSubdivision all {value}", "\"A FORM SUBDIVISION\""),
      arguments("formSubdivision all {value}", "FORM"),
      arguments("formSubdivision == {value}", "\"A FOR*\""),
      arguments("sftFormSubdivision = {value}", "\"SFT FORM SUBDIVISION\""),
      arguments("sftFormSubdivision == {value}", "\"*FORM*\""),
      arguments("saftFormSubdivision = {value}", "\"SAFT FORM SUBDIVISION\""),
      arguments("saftFormSubdivision == {value}", "\"*SAFT FORM*\""),

      // search by lccn
      arguments(specifyCommonField("lccn = {value}"), "N 2003075732"),
      arguments(specifyCommonField("lccn = {value}"), "N2003075732"),


      arguments(specifyCommonField("identifiers.value == {value}"), "AUTHORITY-IDENTIFIER"),
      arguments(specifyCommonField("identifiers.value all ({value})"),
        "AUTHORITY-IDENTIFIER OR 3114176276 OR 0000-0000"),
      arguments(specifyCommonField("identifiers.value all ({value})"),
        "AUTHORITY-IDENTIFIER AND 3114176276 AND 3745-1086"),
      arguments(specifyCommonField("identifiers.identifierTypeId == {value}"), "D6F3C637-3969-4DC6-9146-6371063F049E")

      );
  }

  private static Authority authority(String headingType, String authRefType, String headingRef,
                                     Integer numberOfTitles) {
    return new Authority()
      .id(getAuthoritySampleId())
      .tenantId(TENANT_ID)
      .shared(false)
      .sourceFileId(getAuthoritySourceFileId())
      .naturalId(getAuthorityNaturalId())
      .headingType(headingType)
      .authRefType(authRefType)
      .headingRef(headingRef)
      .numberOfTitles(numberOfTitles);
  }
}
