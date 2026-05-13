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
  private static final String AUTHORITY_ID = "00000001-0000-4000-a000-000000000000";
  private static final String SOURCE_FILE_ID = "5de462a2-7a90-4467-b77f-b2057d6d69b6";
  private static final String NATURAL_ID = "nb1985670488";

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
    "personalNameTitle == \"{value}\", Orwell, George",
    "personalNameTitle all \"{value}\", Orwell, George",
    // sftPersonalName / saftPersonalName
    "sftPersonalName == \"{value}\", sft personal name",
    "sftPersonalName == \"{value}\", *persona*",
    "saftPersonalName == \"{value}\", *saft persona*",
    // corporateName — contains wildcard
    "corporateName == \"{value}\", Oxford University Press",
    "corporateName == \"{value}\", *University*",
    "corporateName = \"{value}\", university",
    "sftCorporateName == \"{value}\", sft corporate",
    "saftCorporateName == \"{value}\", saft corporate name",
    // corporateNameTitle
    "corporateNameTitle == \"{value}\", Disney",
    // meetingName (conference name)
    "meetingName == \"{value}\", DouDay conference",
    "meetingName == \"{value}\", *Day*",
    "meetingName = \"{value}\", DouDay",
    "meetingNameTitle == \"{value}\", Comic-Con",
    // geographicName
    "geographicName == \"{value}\", Asia Pacific",
    "geographicName == \"{value}\", *ASIA*",
    "geographicName = \"{value}\", asia",
    // uniformTitle
    "uniformTitle == \"{value}\", Harry Potter",
    "uniformTitle == \"{value}\", *Potter*",
    "uniformTitle = \"{value}\", potter",
    // topicalTerm, with headingType filter
    "topicalTerm == \"{value}\", Fantasy literature",
    "topicalTerm == \"{value}\" and headingType==\"Topical\", Fantasy*",
    // genreTerm, with headingType filter
    "genreTerm == \"{value}\", Poetry collections",
    "genreTerm == \"{value}\" and headingType==\"Genre\", Poetry*",
    // namedEvent
    "namedEvent == \"{value}\", Eruption of Vesuvius",
    "namedEvent all \"{value}\", Eruption",
    // subdivision types
    "generalSubdivision == \"{value}\", Periodicals",
    "chronSubdivision == \"{value}\", Renaissance*",
    "chronSubdivision all \"{value}\", Renaissance period",
    "formSubdivision == \"{value}\", Manuscripts*",
    "geographicSubdivision == \"{value}\", River vall*",
    // chronTerm, mediumPerfTerm
    "chronTerm == \"{value}\", Early Modern*",
    "mediumPerfTerm == \"{value}\", Orchestr*",
    "mediumPerfTerm all \"{value}\", Orchestra",
    // keyword (cross-field)
    "keyword == \"{value}\", Orchestra",
    "keyword == \"{value}\", Orchestra",
    "keyword == \"{value}\", ORCHESTRA",
    "keyword all \"{value}\", a sft corporate title",
    "keyword all \"{value}\", a saft uniform title",
    // identifiers
    "identifiers.value == \"{value}\" and sftPersonalName==\"*personal name\", nb1985670488",
    "identifiers.value == \"{value}\" and sftPersonalName==\"*personal name\", NB1985670488",
    "identifiers.identifierTypeId==\"{value}\" and personalName==\"gary a.*\", d6f3c637-3969-4dc6-9146-6371063f049e",
    "identifiers.identifierTypeId==\"{value}\" and personalName==\"gary a.*\", D6F3C637-3969-4DC6-9146-6371063F049E",
    "lccn = \"{value}\" and sftPersonalName==\"*personal name\", nb1985670488",
    "lccn = \"{value}\" and sftPersonalName==\"*personal name\", nb1985*",
    "canceledLccn = \"{value}\" and sftPersonalName==\"*personal name\", (OCoLC)fst00010001",
    // subjectHeadings
    "subjectHeadings == \"{value}\" and sftPersonalName==\"*personal name\", a",
    "subjectHeadings all \"{value}\" and sftPersonalName==\"*personal name\", a"
  })
  @SuppressWarnings("checkstyle:MethodLength")
  @DisplayName("search by authority fields (single authority found)")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}''")
  void searchAuthorities_byField_singleResult(String query, String value) throws Exception {
    doSearchByAuthorities(prepareQuery(query, value))
      .andExpect(jsonPath("$.totalRecords", is(1)));
  }

  @Test
  @DisplayName("search by authorities (no authority found)")
  void searchAuthorities_noResult() throws Exception {
    doSearchByAuthorities(prepareQuery("id==\"{value}\"", "random-val"))
      .andExpect(jsonPath("$.totalRecords", is(0)))
      .andExpect(jsonPath("$.authorities", notNullValue()));
  }

  @CsvSource({
    "id=={value}, 00000001-0000-4000-a000-000000000000",
    "id=={value}, 00000001-0000-4000-a000-*"
  })
  @SuppressWarnings("checkstyle:MethodLength")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}''")
  @DisplayName("authority with all heading types expands into correct heading rows")
  void searchAuthorities_headingExpansion(String query, String value) throws Exception {
    var response = doSearchByAuthorities(prepareQuery(query, value))
      .andExpect(jsonPath("$.totalRecords", is(35)));
    var actual = parseResponse(response, AuthoritySearchResult.class);
    assertThat(actual.getAuthorities()).asInstanceOf(InstanceOfAssertFactories.LIST).containsOnly(
      // personalName headings
      gary("Personal Name", AUTHORIZED_TYPE, "Gary A. Wills"),
      gary("Personal Name", REFERENCE_TYPE, "a sft personal name"),
      gary("Personal Name", AUTH_REF_TYPE, "a saft personal name"),

      // personalNameTitle headings
      gary("Personal Name", REFERENCE_TYPE, "a sft personal title"),
      gary("Personal Name", AUTH_REF_TYPE, "a saft personal title"),

      // corporateName headings
      gary("Corporate Name", REFERENCE_TYPE, "a sft corporate name"),
      gary("Corporate Name", AUTH_REF_TYPE, "a saft corporate name"),

      // corporateNameTitle headings
      gary("Corporate Name", REFERENCE_TYPE, "a sft corporate title"),
      gary("Corporate Name", AUTH_REF_TYPE, "a saft corporate title"),

      // meetingName (conference name) headings
      gary("Conference Name", REFERENCE_TYPE, "a sft conference name"),
      gary("Conference Name", AUTH_REF_TYPE, "a saft conference name"),

      // meetingNameTitle headings
      gary("Conference Name", REFERENCE_TYPE, "a sft conference title"),
      gary("Conference Name", AUTH_REF_TYPE, "a saft conference title"),

      // geographicName headings
      gary("Geographic Name", REFERENCE_TYPE, "a sft geographic name"),
      gary("Geographic Name", AUTH_REF_TYPE, "a saft geographic name"),

      // uniformTitle headings
      gary("Uniform Title", REFERENCE_TYPE, "a sft uniform title"),
      gary("Uniform Title", AUTH_REF_TYPE, "a saft uniform title"),

      // namedEvent headings
      gary("Named Event", REFERENCE_TYPE, "a sft named event"),
      gary("Named Event", AUTH_REF_TYPE, "a saft named event"),

      // generalSubdivision headings
      gary("General Subdivision", REFERENCE_TYPE, "a sft general subdivision"),
      gary("General Subdivision", AUTH_REF_TYPE, "a saft general subdivision"),

      // topicalTerm headings
      gary("Topical", REFERENCE_TYPE, "a sft topical term"),
      gary("Topical", AUTH_REF_TYPE, "a saft topical term"),

      // genreTerm headings
      gary("Genre", REFERENCE_TYPE, "a sft genre term"),
      gary("Genre", AUTH_REF_TYPE, "a saft genre term"),

      // chronTerm headings
      gary("Chronological Term", REFERENCE_TYPE, "a sft chron term"),
      gary("Chronological Term", AUTH_REF_TYPE, "a saft chron term"),

      // mediumPerfTerm headings
      gary("Medium of Performance Term", REFERENCE_TYPE, "a sft medium perf term"),
      gary("Medium of Performance Term", AUTH_REF_TYPE, "a saft medium perf term"),

      // geographicSubdivision headings
      gary("Geographic Subdivision", REFERENCE_TYPE, "a sft geographic subdivision"),
      gary("Geographic Subdivision", AUTH_REF_TYPE, "a saft geographic subdivision"),

      // chronSubdivision headings
      gary("Chronological Subdivision", REFERENCE_TYPE, "a sft chron subdivision"),
      gary("Chronological Subdivision", AUTH_REF_TYPE, "a saft chron subdivision"),

      // formSubdivision headings
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
      .numberOfTitles(AUTHORIZED_TYPE.equals(authRefType) ? 0 : null);
  }
}
