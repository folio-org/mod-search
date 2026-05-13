package org.folio.api.search;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthoritySearchResult;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public abstract class SearchAuthorityIT extends BaseSharedTest {

  // From authorities.json first record — the "all-fields" authority used to test field-level search
  private static final String AUTHORITY_ID = "55294032-fcf6-45cc-b6da-4420a61ef72c";
  private static final String SOURCE_FILE_ID = "5de462a2-7a90-4467-b77f-b2057d6d69b6";
  private static final String NATURAL_ID = "nbc123435";

  private static final String AUTHORIZED_TYPE = "Authorized";
  private static final String REFERENCE_TYPE = "Reference";
  private static final String AUTH_REF_TYPE = "Auth/Ref";

  @CsvSource({
    // personalName — exact, wildcard, keyword, with authRefType/headingType filters
    "personalName == \"{value}\", Gary A. Wills",
    "personalName == \"{value}\", GARY A. WILLS",
    "personalName == \"{value}\", gary a.*",
    "personalName all \"{value}\", gary",
    "personalName all \"{value}\", Gary A. Wills",
    "personalName == \"{value}\" and authRefType==\"Authorized\", gary",
    "personalName == \"{value}\" and headingType==\"Personal Name\", gary",
    // personalNameTitle
    "personalNameTitle == \"{value}\", a personal title",
    "personalNameTitle all \"{value}\", personal title",
    // sftPersonalName / saftPersonalName
    "sftPersonalName == \"{value}\", sft personal name",
    "sftPersonalName == \"{value}\", *persona*",
    "saftPersonalName == \"{value}\", *saft persona*",
    // corporateName — contains wildcard
    "corporateName == \"{value}\", a corporate name",
    "corporateName == \"{value}\", *corporat*",
    "corporateName = \"{value}\", corporate",
    "sftCorporateName == \"{value}\", sft corporate",
    "saftCorporateName == \"{value}\", saft corporate name",
    // corporateNameTitle
    "corporateNameTitle == \"{value}\", a corporate title",
    // meetingName (conference name)
    "meetingName == \"{value}\", a conference name",
    "meetingName == \"{value}\", *onference*",
    "meetingName = \"{value}\", conference",
    "meetingNameTitle == \"{value}\", a conference title",
    // geographicName
    "geographicName == \"{value}\", a geographic name",
    "geographicName == \"{value}\", *graph*",
    "geographicName = \"{value}\", geographic",
    // uniformTitle
    "uniformTitle == \"{value}\", an uniform title",
    "uniformTitle == \"{value}\", *nifor*",
    "uniformTitle = \"{value}\", uniform",
    // topicalTerm, with headingType filter
    "topicalTerm == \"{value}\", a topical term",
    "topicalTerm == \"{value}\" and headingType==\"Topical\", a top*",
    // genreTerm, with headingType filter
    "genreTerm == \"{value}\", a genre term",
    "genreTerm == \"{value}\" and headingType==\"Genre\", a gen*",
    // namedEvent
    "namedEvent == \"{value}\", a named event",
    "namedEvent all \"{value}\", named",
    // subdivision types
    "generalSubdivision == \"{value}\", a general subdivision",
    "chronSubdivision == \"{value}\", a chr*",
    "chronSubdivision all \"{value}\", chron",
    "formSubdivision == \"{value}\", a for*",
    "geographicSubdivision == \"{value}\", a geo*",
    // chronTerm, mediumPerfTerm
    "chronTerm == \"{value}\", a chr*",
    "mediumPerfTerm == \"{value}\", a med*",
    "mediumPerfTerm all \"{value}\", medium",
    // keyword (cross-field)
    "keyword == \"{value}\", a topical term",
    "keyword == \"{value}\", a corporate title",
    "keyword == \"{value}\", A CORPORATE TITLE",
    "keyword all \"{value}\", a sft corporate title",
    "keyword all \"{value}\", a saft uniform title",
    // identifiers
    "identifiers.value == \"{value}\" and sftPersonalName==\"*personal name\", authority-identifier",
    "identifiers.value == \"{value}\" and sftPersonalName==\"*personal name\", AUTHORITY-IDENTIFIER",
    "identifiers.identifierTypeId==\"{value}\" and personalName==\"gary a.*\", d6f3c637-3969-4dc6-9146-6371063f049e",
    "identifiers.identifierTypeId==\"{value}\" and personalName==\"gary a.*\", D6F3C637-3969-4DC6-9146-6371063F049E",
    "lccn = \"{value}\" and sftPersonalName==\"*personal name\", 2003065165",
    "lccn = \"{value}\" and sftPersonalName==\"*personal name\", n 2003075732",
    "canceledLccn = \"{value}\" and sftPersonalName==\"*personal name\", canceledlccn",
    // subjectHeadings
    "subjectHeadings == \"{value}\" and sftPersonalName==\"*personal name\", a sub*",
    "subjectHeadings all \"{value}\" and sftPersonalName==\"*personal name\", a subject heading"
  })
  @SuppressWarnings("checkstyle:MethodLength")
  @DisplayName("search by authority fields (single authority found)")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}''")
  void searchAuthorities_byField_singleResult(String query, String value) throws Exception {
    doSearchByAuthorities(prepareQuery(query, value))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.authorities[0].id", is(AUTHORITY_ID)));
  }

  @Test
  @DisplayName("search by authorities (no authority found)")
  void searchAuthorities_noResult() throws Exception {
    doSearchByAuthorities(prepareQuery("id==\"{value}\"", "random-val"))
      .andExpect(jsonPath("$.totalRecords", is(0)))
      .andExpect(jsonPath("$.authorities", notNullValue()));
  }

  @CsvSource({
    "id=={value}, 55294032-fcf6-45cc-b6da-4420a61ef72c",
    "id=={value}, 55294032-fcf6-45cc-b6da-*"
  })
  @SuppressWarnings("checkstyle:MethodLength")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}''")
  @DisplayName("authority with all heading types expands into correct heading rows")
  void searchAuthorities_headingExpansion(String query, String value) throws Exception {
    var response = doSearchByAuthorities(prepareQuery(query, value))
      .andExpect(jsonPath("$.totalRecords", is(51)));
    var actual = parseResponse(response, AuthoritySearchResult.class);
    assertThat(actual.getAuthorities()).asInstanceOf(InstanceOfAssertFactories.LIST).containsOnly(
      // personalName headings
      gary("Personal Name", AUTHORIZED_TYPE, "Gary A. Wills"),
      gary("Personal Name", REFERENCE_TYPE, "a sft personal name"),
      gary("Personal Name", AUTH_REF_TYPE, "a saft personal name"),

      // personalNameTitle headings
      gary("Personal Name", AUTHORIZED_TYPE, "a personal title"),
      gary("Personal Name", REFERENCE_TYPE, "a sft personal title"),
      gary("Personal Name", AUTH_REF_TYPE, "a saft personal title"),

      // corporateName headings
      gary("Corporate Name", AUTHORIZED_TYPE, "a corporate name"),
      gary("Corporate Name", REFERENCE_TYPE, "a sft corporate name"),
      gary("Corporate Name", AUTH_REF_TYPE, "a saft corporate name"),

      // corporateNameTitle headings
      gary("Corporate Name", AUTHORIZED_TYPE, "a corporate title"),
      gary("Corporate Name", REFERENCE_TYPE, "a sft corporate title"),
      gary("Corporate Name", AUTH_REF_TYPE, "a saft corporate title"),

      // meetingName (conference name) headings
      gary("Conference Name", AUTHORIZED_TYPE, "a conference name"),
      gary("Conference Name", REFERENCE_TYPE, "a sft conference name"),
      gary("Conference Name", AUTH_REF_TYPE, "a saft conference name"),

      // meetingNameTitle headings
      gary("Conference Name", AUTHORIZED_TYPE, "a conference title"),
      gary("Conference Name", REFERENCE_TYPE, "a sft conference title"),
      gary("Conference Name", AUTH_REF_TYPE, "a saft conference title"),

      // geographicName headings
      gary("Geographic Name", AUTHORIZED_TYPE, "a geographic name"),
      gary("Geographic Name", REFERENCE_TYPE, "a sft geographic name"),
      gary("Geographic Name", AUTH_REF_TYPE, "a saft geographic name"),

      // uniformTitle headings
      gary("Uniform Title", AUTHORIZED_TYPE, "an uniform title"),
      gary("Uniform Title", REFERENCE_TYPE, "a sft uniform title"),
      gary("Uniform Title", AUTH_REF_TYPE, "a saft uniform title"),

      // namedEvent headings
      gary("Named Event", AUTHORIZED_TYPE, "a named event"),
      gary("Named Event", REFERENCE_TYPE, "a sft named event"),
      gary("Named Event", AUTH_REF_TYPE, "a saft named event"),

      // generalSubdivision headings
      gary("General Subdivision", AUTHORIZED_TYPE, "a general subdivision"),
      gary("General Subdivision", REFERENCE_TYPE, "a sft general subdivision"),
      gary("General Subdivision", AUTH_REF_TYPE, "a saft general subdivision"),

      // topicalTerm headings
      gary("Topical", AUTHORIZED_TYPE, "a topical term"),
      gary("Topical", REFERENCE_TYPE, "a sft topical term"),
      gary("Topical", AUTH_REF_TYPE, "a saft topical term"),

      // genreTerm headings
      gary("Genre", AUTHORIZED_TYPE, "a genre term"),
      gary("Genre", REFERENCE_TYPE, "a sft genre term"),
      gary("Genre", AUTH_REF_TYPE, "a saft genre term"),

      // chronTerm headings
      gary("Chronological Term", AUTHORIZED_TYPE, "a chron term"),
      gary("Chronological Term", REFERENCE_TYPE, "a sft chron term"),
      gary("Chronological Term", AUTH_REF_TYPE, "a saft chron term"),

      // mediumPerfTerm headings
      gary("Medium of Performance Term", AUTHORIZED_TYPE, "a medium perf term"),
      gary("Medium of Performance Term", REFERENCE_TYPE, "a sft medium perf term"),
      gary("Medium of Performance Term", AUTH_REF_TYPE, "a saft medium perf term"),

      // geographicSubdivision headings
      gary("Geographic Subdivision", AUTHORIZED_TYPE, "a geographic subdivision"),
      gary("Geographic Subdivision", REFERENCE_TYPE, "a sft geographic subdivision"),
      gary("Geographic Subdivision", AUTH_REF_TYPE, "a saft geographic subdivision"),

      // chronSubdivision headings
      gary("Chronological Subdivision", AUTHORIZED_TYPE, "a chron subdivision"),
      gary("Chronological Subdivision", REFERENCE_TYPE, "a sft chron subdivision"),
      gary("Chronological Subdivision", AUTH_REF_TYPE, "a saft chron subdivision"),

      // formSubdivision headings
      gary("Form Subdivision", AUTHORIZED_TYPE, "a form subdivision"),
      gary("Form Subdivision", REFERENCE_TYPE, "a sft form subdivision"),
      gary("Form Subdivision", AUTH_REF_TYPE, "a saft form subdivision")
    );
  }

  private static Authority gary(String headingType, String authRefType, String headingRef) {
    return new Authority()
      .id(AUTHORITY_ID)
      .tenantId(TENANT_ID)
      .shared(false)
      .sourceFileId(SOURCE_FILE_ID)
      .naturalId(NATURAL_ID)
      .headingType(headingType)
      .authRefType(authRefType)
      .headingRef(headingRef)
      .numberOfTitles(AUTHORIZED_TYPE.equals(authRefType) ? 2 : null);
  }
}
