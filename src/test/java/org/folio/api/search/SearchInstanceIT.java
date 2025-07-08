package org.folio.api.search;

import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.sample.SampleInstances.getSemanticWebAsMap;
import static org.folio.support.sample.SampleInstances.getSemanticWebId;
import static org.folio.support.sample.SampleInstances.getSemanticWebMatchers;
import static org.folio.support.sample.SampleInstancesResponse.getInstanceBasicResponseSample;
import static org.folio.support.sample.SampleInstancesResponse.getInstanceFullResponseSample;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceSearchResult;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

@IntegrationTest
class SearchInstanceIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(Instance.class, getSemanticWebMatchers(), getSemanticWebAsMap());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @MethodSource("testDataProvider")
  @DisplayName("search by instances (single instance found)")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}''")
  void searchByInstances_parameterized_singleResult(String query, String value) throws Throwable {
    doSearchByInstances(prepareQuery(query, value))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.instances[0].id", is(getSemanticWebId())));
  }

  @MethodSource("testCaseInsensitiveDataProvider")
  @DisplayName("search by instances case insensitive (single instance found)")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}''")
  void searchByInstancesCaseInsensitive_parameterized_singleResult(String query, String value) throws Throwable {
    doSearchByInstances(prepareQuery(query, value))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.instances[0].id", is(getSemanticWebId())));
  }

  @MethodSource("testIssnDataProvider")
  @DisplayName("search by instances case insensitive ISSN with trailing X")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}''")
  void searchByInstancesCaseInsensitiveIssn_parameterized_singleResult(String query, String value) throws Throwable {
    doSearchByInstances(prepareQuery(query, value))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.instances[0].id", is(getSemanticWebId())));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @CsvSource({
    "title == {value}, web semantic",
    "title <> {value}, A semantic web primer",
    "title all {value}, semantic web word",
    "indexTitle <> {value}, Semantic web primer",
    "uniformTitle all {value}, deja vu",
    "uniformTitle all {value}, déjà vu",
    "contributors.name all {value}, franks",
    "contributors.authorityId == {value}, 11110000-fcf6-45cc-b6da-4420a61ef72c",
    "authorityId == {value}, 11110000-fcf6-45cc-b6da-4420a61ef72c",
    "electronicAccess.materialsSpecification all {value}, material",
    "items.electronicAccess.materialsSpecification all {value}, table",
    "item.electronicAccess.materialsSpecification all {value}, table",
    "holdings.electronicAccess.materialsSpecification all {value}, specification",
    "publicNotes == {value}, librarian",
    "itemPublicNotes == {value}, private note for item",
    "itemPublicNotes == {value}, private circulation note",
    "holdingsPublicNotes == {value}, librarian private note",
    "issn = {value}, 03178471",
    "oclc = {value}, 0262012103",
    "(keyword all {value}), 0747-0088"
  })
  @DisplayName("can search by instances (nothing found)")
  void searchByInstances_parameterized_zeroResults(String query, String value) throws Throwable {
    doSearchByInstances(prepareQuery(query, '"' + value + '"')).andExpect(jsonPath("$.totalRecords", is(0)));
  }

  @Test
  void search_negative_unknownField() throws Exception {
    attemptSearchByInstances("unknownField all book")
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Invalid search field provided in the CQL query")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("field")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("unknownField")));
  }

  @Test
  void responseContainsOnlyBasicInstanceProperties() {
    var expected = getInstanceBasicResponseSample();
    var response = doSearchByInstances(prepareQuery("id=={value}", getSemanticWebId()));

    var actual = parseResponse(response, InstanceSearchResult.class);

    Assertions.assertThat(actual).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(expected);
  }

  @Test
  void responseContainsAllInstanceProperties() {
    var expected = getInstanceFullResponseSample();
    var response = doSearchByInstances(prepareQuery("id=={value}", getSemanticWebId()), true);

    var actual = parseResponse(response, InstanceSearchResult.class);

    Assertions.assertThat(actual).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(expected);
  }

  private static Stream<Arguments> testDataProvider() {
    return Stream.of(
      arguments("cql.allRecords = 1", getSemanticWebId()),
      arguments("id = {value}", "\"\""),
      arguments("id = {value}", getSemanticWebId()),
      arguments("id = {value}", "5bf370e0*a0a39"),
      arguments("id == {value}", getSemanticWebId()),

      arguments("tenantId = {value}", TENANT_ID),

      arguments("shared == {value}", "false"),

      arguments("title <> {value}", "unknown value"),
      arguments("indexTitle <> {value}", "unknown value"),

      arguments("title all {value}", "semantic"),
      arguments("title all {value}", "primers"),
      arguments("title all {value}", "cherchell"),
      arguments("title all {value}", "cooperate"),
      arguments("title all {value}", "cooperative"),
      arguments("title any {value}", "semantic web word"),
      arguments("title all {value}", "system information"),
      // search by instance title (search across 2 fields)
      arguments("title any {value}", "systems alternative semantic"),

      //phrase matching
      arguments("title == {value}", "semantic web"),
      arguments("title == {value}", "a semantic web primer"),
      arguments("title == {value}", "cooperative information systems"),

      // ASCII folding
      arguments("title all {value}", "deja vu"),
      arguments("title all {value}", "déjà vu"),
      arguments("title all {value}", "Algérie"),
      arguments("title all {value}", "Der Preis der Verfuhrung"),
      arguments("title all {value}", "Der Preis der Verführung"),
      arguments("title all {value}", "Der Preis der Verfuhrung*"),
      arguments("title all {value}", "Der Preis der Verführung*"),
      // e here should replace e + U + 0301 (Combining Acute Accent)
      arguments("title all {value}", "algerie"),

      arguments("series all {value}", "Cooperative information systems"),
      arguments("series all {value}", "cooperate"),
      arguments("series all {value}", "cooperative"),

      arguments("alternativeTitles.alternativeTitle == {value}", "An alternative title"),
      arguments("alternativeTitles.alternativeTitle all {value}", "uniform"),
      arguments("alternativeTitles.alternativeTitle all {value}", "deja vu"),
      arguments("alternativeTitles.alternativeTitle all {value}", "déjà vu"),
      arguments("alternativeTitles.alternativeTitle all {value}", "pangok"),
      arguments("alternativeTitles.alternativeTitle all {value}", "pang'ok"),
      arguments("alternativeTitles.alternativeTitle all {value}", "pang ok"),
      arguments("alternativeTitles.alternativeTitle all {value}", "bangk'asyurangsŭ"),
      arguments("alternativeTitles.alternativeTitle all {value}", "asyurangsŭ"),

      arguments("uniformTitle all {value}", "uniform"),

      arguments("identifiers.value == {value}", "0262012103"),
      arguments("identifiers.value all {value}", "200306*"),
      arguments("identifiers.value all ({value})", "047144250X or 2003065165 or 0000-0000"),
      arguments("identifiers.value all ({value})", "047144250X and 2003065165 and 0317-8471"),
      arguments("identifiers.identifierTypeId == {value}", "c858e4f2-2b6b-4385-842b-60732ee14abb"),
      arguments("identifiers.identifierTypeId == 8261054f-be78-422d-bd51-4ed9f33c3422 "
                + "and identifiers.value == {value}", "0262012103"),

      arguments("publisher all {value}", "MIT"),
      arguments("publisher all {value}", "mit"),
      arguments("publisher all {value}", "press"),

      arguments("publication.place all {value}", "cambridge"),
      arguments("publication.place all {value}", "Cambridge"),
      arguments("publication.place all {value}", "mass"),
      arguments("publication.place all {value}", "Mass."),
      arguments("publication.place any {value}", "Cambridge mass"),
      arguments("publication.place all {value}", "Cambridge mass"),
      arguments("publication.place = {value}", "\"Cambridge, Mass.\""),
      arguments("publication.place == {value}", "Cambridge"),
      arguments("publication.place ==/string {value}", "\"Cambridge, Mass.\""),
      arguments("publication.place = {value}", "Cambridge, Ma*"),
      arguments("publication.place = {value}", "\"*mbridge, Mass.\""),

      arguments("contributors all {value}", "frank"),
      arguments("contributors all {value}", "Frank"),
      arguments("contributors all {value}", "grigoris"),
      arguments("contributors all {value}", "Grigoris Ant\\\\niou"),
      arguments("contributors any {value}", "Grigoris frank"),
      arguments("contributors all {value}", "Van Harmelen, Frank"),
      arguments("contributors == {value}", "Van Harmelen, Frank"),
      arguments("contributors ==/string {value}", "Van Harmelen, Frank"),
      arguments("contributors = {value}", "Van Harmelen, Fr*"),
      arguments("contributors = {value}", "*rmelen, Frank"),

      arguments("contributors.name = {value}", "Van Harmelen, Frank"),
      arguments("contributors.name == {value}", "Van Harmelen"),
      arguments("contributors.name ==/string {value}", "Van Harmelen, Frank"),
      arguments("contributors.name = {value}", "Van Harmelen, Fr*"),
      arguments("contributors.name = {value}", "Ant\\\\n*"),
      arguments("contributors.name = {value}", "*rmelen, Frank"),

      arguments("contributors.authorityId == {value}", "55294032-fcf6-45cc-b6da-4420a61ef72c"),
      arguments("authorityId == {value}", "55294032-fcf6-45cc-b6da-4420a61ef72c"),

      arguments("hrid == {value}", "inst000000000022"),
      arguments("hrid == {value}", "inst00*"),
      arguments("hrid == {value}", "*00022"),
      arguments("hrid == {value}", "*00000002*"),

      arguments("keyword = *", ""),
      arguments("keyword all {value}", "semantic web primer"),
      arguments("keyword all {value}", "semantic Ant\\\\niou ocm0012345 047144250X"),
      arguments("subjects all {value}", "semantic"),
      arguments("subjects ==/string {value}", "semantic web"),

      arguments("tags.tagList all {value}", "book"),
      arguments("tags.tagList all {value}", "electronic"),

      arguments("classifications.classificationNumber=={value}", "025.04"),
      arguments("classifications.classificationNumber=={value}", "025*"),

      // search by normalized classification number
      arguments("normalizedClassificationNumber==\"{value}\"", "HD1691 .I5 1967"),
      arguments("normalizedClassificationNumber==\"{value}\"", "hd1691 .I5 1967"), // Case sensitivity
      arguments("normalizedClassificationNumber==\"{value}\"", "*1691 .I5 1967"), // Leading wildcard
      arguments("normalizedClassificationNumber==\"{value}\"", "HD1691*"), // Trailing wildcard
      arguments("normalizedClassificationNumber==\"{value}\"", "*1691*"), // Leading and trailing wildcard
      arguments("normalizedClassificationNumber==\"{value}\"", "HD1691.I51967"), // Spaces internal
      arguments("normalizedClassificationNumber==\"{value}\"", "  HD1691 .I5 1967"), // Spaces leading
      arguments("normalizedClassificationNumber==\"{value}\"", "HD1691 .I5 1967   "), // Spaces trailing
      arguments("normalizedClassificationNumber==\"{value}\"", "HD1691 I5 1967"), // Special characters
      arguments("normalizedClassificationNumber==\"{value}\"", "HD1691I51967"), // Special characters and spaces

      arguments("electronicAccess.uri==\"{value}\"", "https://testlibrary.sample.com/journal/10.1002/(ISSN)1938-3703"),
      arguments("electronicAccess.linkText all {value}", "access"),
      arguments("electronicAccess.publicNote all {value}", "online"),
      arguments("electronicAccess.relationshipId == \"{value}\"", "a1c0b4f2-3d8e-4b5f-8a7c-6d9e0f2b1c3d"),
      arguments("instanceFormatIds == {value}", "7f9c4ac0-fa3d-43b7-b978-3bf0be38c4da"),

      arguments("administrativeNotes == {value}", "original + pcc"),
      arguments("administrativeNotes any {value}", "original pcc"),

      arguments("publicNotes all {value}", "development"),
      arguments("notes.note == {value}", "Librarian private note"),
      arguments("notes.note == {value}", "The development of the Semantic Web,"),
      arguments("items.effectiveCallNumberComponents.typeId = {value}", "\"512173a7-bd09-490e-b773-17d83f2b63fe\""),

      // search by isbn10
      arguments("isbn = {value}", "047144250X"),
      arguments("isbn = {value}", "04714*"),
      arguments("isbn = {value}", "0471-4*"),

      // search by isbn13
      arguments("isbn = {value}", "9780471442509"),
      arguments("isbn = {value}", "978-0471-44250-9"),
      arguments("isbn = {value}", "paper"),
      arguments("isbn = {value}", "978-0471*"),
      arguments("isbn = {value}", "9780471*"),

      arguments("issn = {value}", "0317-8471"),
      arguments("issn = {value}", "0317*"),

      // search by oclc
      arguments("oclc = {value}", "12345 800630"),
      arguments("oclc = {value}", "ocm60710867"),
      arguments("oclc = {value}", "60710*"),

      // search by lccn
      arguments("lccn = {value}", "2003065165"),
      arguments("lccn = {value}", "*65165"),
      arguments("lccn = {value}", "n 2003075732"),
      arguments("lccn = {value}", "N2003075732"),
      arguments("lccn = {value}", "*75732"),
      arguments("lccn = {value}", "20030*"),

      // search by item fields
      arguments("item.hrid = {value}", "item000000000014"),
      arguments("item.hrid = {value}", "item*"),
      arguments("item.hrid = {value}", "*00014"),
      arguments("item.hrid = {value}", "item*00014"),

      arguments("itemPublicNotes all {value}", "bibliographical references"),
      arguments("itemPublicNotes all {value}", "public circulation note"),

      arguments("itemIdentifiers all {value}", "item000000000014"),
      arguments("itemIdentifiers all {value}", "81ae0f60-f2bc-450c-84c8-5a21096daed9"),
      arguments("itemIdentifiers all {value}", "item_accession_number"),
      arguments("itemIdentifiers all {value}", "7212ba6a-8dcf-45a1-be9a-ffaa847c4423"),
      arguments("itemIdentifiers all {value}", "itemIdentifierFieldValue"),

      arguments("item.administrativeNotes == {value}", "need attention"),
      arguments("item.administrativeNotes all {value}", "v1.1"),

      arguments("item.notes.note == {value}", "Librarian public note for item"),
      arguments("item.notes.note == {value}", "Librarian private note for item"),
      arguments("item.notes.note == {value}", "testCirculationNote"),
      arguments("item.notes.note == {value}", "private circulation note"),

      arguments("item.circulationNotes.note == {value}", "testCirculationNote"),
      arguments("item.circulationNotes.note all {value}", "public circulation note"),
      arguments("item.circulationNotes.note all {value}", "private circulation note"),
      arguments("item.circulationNotes.note all {value}", "*Note"),
      arguments("item.circulationNotes.note all {value}", "private circulation*"),

      arguments("item.electronicAccess==\"{value}\"", "table"),
      arguments("item.electronicAccess.uri==\"{value}\"", "https://www.loc.gov/catdir/toc/ecip0718/2007020429.html"),
      arguments("item.electronicAccess.linkText all {value}", "links available"),
      arguments("item.electronicAccess.publicNote all {value}", "table of contents"),
      arguments("item.electronicAccess.relationshipId == \"{value}\"", "3b430592-2e09-4b48-9a0c-0636d66b9fb3"),

      // Search by item fields (Backward compatibility)
      arguments("items.hrid = {value}", "item000000000014"),
      arguments("items.hrid = {value}", "item*"),
      arguments("items.hrid = {value}", "*00014"),
      arguments("items.hrid = {value}", "item*00014"),

      arguments("items.notes.note == {value}", "Librarian public note for item"),
      arguments("items.notes.note == {value}", "Librarian private note for item"),
      arguments("items.notes.note == {value}", "testCirculationNote"),
      arguments("items.notes.note == {value}", "private circulation note"),

      arguments("items.tags.tagList all {value}", "item-tag"),

      arguments("items.circulationNotes.note == {value}", "testCirculationNote"),
      arguments("items.circulationNotes.note all {value}", "public circulation note"),
      arguments("items.circulationNotes.note all {value}", "private circulation note"),
      arguments("items.circulationNotes.note all {value}", "*Note"),
      arguments("items.circulationNotes.note all {value}", "private circulation*"),

      arguments("items.electronicAccess==\"{value}\"", "table"),
      arguments("items.electronicAccess.uri==\"{value}\"", "https://www.loc.gov/catdir/toc/ecip0718/2007020429.html"),
      arguments("items.electronicAccess.linkText all {value}", "links available"),
      arguments("items.electronicAccess.publicNote all {value}", "table of contents"),

      // search by holding fields
      arguments("holdings.administrativeNotes == {value}", "for deletion"),
      arguments("holdings.administrativeNotes all {value}", "v2.0"),
      arguments("holdingsPublicNotes all {value}", "bibliographical references"),
      arguments("holdings.notes.note == {value}", "Librarian public note for holding"),
      arguments("holdings.notes.note == {value}", "Librarian private note for holding"),
      arguments("holdings.tags.tagList == {value}", "holdings-tag"),

      arguments("holdings.electronicAccess==\"{value}\"", "electronicAccess"),
      arguments("holdings.electronicAccess.uri==\"{value}\"", "https://testlibrary.sample.com/holdings?hrid=ho0000006"),
      arguments("holdings.electronicAccess.linkText all {value}", "link text"),
      arguments("holdings.electronicAccess.publicNote all {value}", "note"),
      arguments("holdings.electronicAccess.relationshipId == \"{value}\"", "3b430592-2e09-4b48-9a0c-0636d66b9fb3"),

      arguments("holdingsIdentifiers all {value}", "hold000000000009"),
      arguments("holdingsIdentifiers == {value}", "1d76ee84-d776-48d2-ab96-140c24e39ac5"),
      arguments("holdingsIdentifiers all {value}", "9b8ec096-fa2e-451b-8e7a-6d1c977ee946"),
      arguments("holdingsIdentifiers all {value}", "e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19"),

      //search by multiple different parameters
      arguments("(keyword all \"{value}\")", "wolves matthew 9781609383657"),
      arguments("(keyword all \"{value}\")", "A semantic web primer : wolves"),
      arguments("(keyword all \"{value}\")", "A semantic web primer & wolves"),
      arguments("(keyword all \"{value}\")", "A semantic web primer / wolves"),
      arguments("keyword == {value}", "Van Harmelen, Frank"),
      arguments("keyword ==/string {value}", "0262012103"),
      arguments("(title all \"{value}\")", "A semantic web primer : 0747-0850")
    );
  }

  private static Stream<Arguments> testCaseInsensitiveDataProvider() {
    return Stream.of(
      arguments("title <> {value}", "UNKNOWN VALUE"),
      arguments("indexTitle <> {value}", "UNKNOWN VALUE"),

      arguments("title all {value}", "SEMANTIC"),
      arguments("title all {value}", "PRIMERS"),
      arguments("title all {value}", "CHERCHELL"),
      arguments("title all {value}", "COOPERATE"),
      arguments("title all {value}", "COOPERATIVE"),
      arguments("title any {value}", "SEMANTIC WEB WORD"),
      arguments("title all {value}", "SYSTEM INFORMATION"),
      // search by instance title (search across 2 fields)
      arguments("title any {value}", "SYSTEMS ALTERNATIVE SEMANTIC"),

      //phrase matching
      arguments("title == {value}", "SEMANTIC WEB"),
      arguments("title == {value}", "A SEMANTIC WEB PRIMER"),
      arguments("title == {value}", "COOPERATIVE INFORMATION SYSTEMS"),

      // ASCII folding
      arguments("title all {value}", "DEJA VU"),
      arguments("title all {value}", "DÉJÀ VU"),
      arguments("title all {value}", "ALGÉRIE"),
      // e here should replace e + U + 0301 (Combining Acute Accent)
      arguments("title all {value}", "ALGERIE"),

      arguments("series all {value}", "COOPERATIVE INFORMATION SYSTEMS"),
      arguments("series all {value}", "COOPERATE"),
      arguments("series all {value}", "COOPERATIVE"),

      arguments("alternativeTitles.alternativeTitle == {value}", "AN ALTERNATIVE TITLE"),
      arguments("alternativeTitles.alternativeTitle all {value}", "UNIFORM"),
      arguments("alternativeTitles.alternativeTitle all {value}", "DEJA VU"),
      arguments("alternativeTitles.alternativeTitle all {value}", "DÉJÀ VU"),
      arguments("alternativeTitles.alternativeTitle all {value}", "PANGOK"),
      arguments("alternativeTitles.alternativeTitle all {value}", "PANG'OK"),
      arguments("alternativeTitles.alternativeTitle all {value}", "PANG OK"),
      arguments("alternativeTitles.alternativeTitle all {value}", "BANGK'ASYURANGSŬ"),
      arguments("alternativeTitles.alternativeTitle all {value}", "ASYURANGSŬ"),

      arguments("uniformTitle all {value}", "UNIFORM"),

      arguments("identifiers.value == {value}", "0262012103"),
      arguments("identifiers.value all {value}", "200306*"),
      arguments("identifiers.value all ({value})", "047144250X OR 2003065165 OR 0000-0000"),
      arguments("identifiers.value all ({value})", "047144250X AND 2003065165 AND 0317-8471"),
      arguments("identifiers.identifierTypeId == {value}", "C858E4F2-2B6B-4385-842B-60732EE14ABB"),
      arguments("identifiers.identifierTypeId == 8261054F-BE78-422D-BD51-4ED9F33C3422 "
                + "AND identifiers.value == {value}", "0262012103"),

      arguments("publisher all {value}", "MIT"),
      arguments("publisher all {value}", "MIT"),
      arguments("publisher all {value}", "PRESS"),

      arguments("contributors all {value}", "FRANK"),
      arguments("contributors all {value}", "FRANK"),
      arguments("contributors all {value}", "GRIGORIS"),
      arguments("contributors all {value}", "GRIGORIS ANT\\\\NIOU"),
      arguments("contributors any {value}", "GRIGORIS FRANK"),
      arguments("contributors all {value}", "VAN HARMELEN, FRANK"),
      arguments("contributors == {value}", "VAN HARMELEN, FRANK"),
      arguments("contributors ==/string {value}", "VAN HARMELEN, FRANK"),
      arguments("contributors = {value}", "VAN HARMELEN, FR*"),
      arguments("contributors = {value}", "*RMELEN, FRANK"),

      arguments("contributors.name = {value}", "VAN HARMELEN, FRANK"),
      arguments("contributors.name == {value}", "VAN HARMELEN"),
      arguments("contributors.name ==/string {value}", "VAN HARMELEN, FRANK"),
      arguments("contributors.name = {value}", "VAN HARMELEN, FR*"),
      arguments("contributors.name = {value}", "ANT\\\\N*"),
      arguments("contributors.name = {value}", "*RMELEN, FRANK"),

      arguments("dates.date1 == {value}", "*9u"),
      arguments("dates.date1 == {value}", "*\\\\9*"),
      arguments("dates.date1 == {value}", "1\\\\*"),

      arguments("dates.date2 == {value}", "*22"),
      arguments("dates.date2 == {value}", "*02*"),
      arguments("dates.date2 == {value}", "20*"),
      arguments("dates.date2 > {value}", " 2021"),

      arguments("contributors.authorityId == {value}", "55294032-FCF6-45CC-B6DA-4420A61EF72C"),
      arguments("authorityId == {value}", "55294032-FCF6-45CC-B6DA-4420A61EF72C"),

      arguments("hrid == {value}", "INST000000000022"),
      arguments("hrid == {value}", "INST00*"),
      arguments("hrid == {value}", "*00022"),
      arguments("hrid == {value}", "*00000002*"),

      arguments("keyword = *", ""),
      arguments("keyword all {value}", "SEMANTIC WEB PRIMER"),
      arguments("keyword all {value}", "SEMANTIC ANT\\\\NIOU OCM0012345 047144250X"),
      arguments("subjects all {value}", "SEMANTIC"),
      arguments("subjects ==/string {value}", "SEMANTIC WEB"),

      arguments("tags.tagList all {value}", "BOOK"),
      arguments("tags.tagList all {value}", "ELECTRONIC"),

      arguments("classifications.classificationNumber=={value}", "025.04"),
      arguments("classifications.classificationNumber=={value}", "025*"),

      arguments("electronicAccess.uri==\"{value}\"", "HTTPS://TESTLIBRARY.SAMPLE.COM/JOURNAL/10.1002/(ISSN)1938-3703"),
      arguments("electronicAccess.linkText all {value}", "ACCESS"),
      arguments("electronicAccess.publicNote all {value}", "ONLINE"),
      arguments("instanceFormatIds == {value}", "7F9C4AC0-FA3D-43B7-B978-3BF0BE38C4DA"),

      arguments("administrativeNotes == {value}", "ORIGINAL + PCC"),
      arguments("administrativeNotes any {value}", "ORIGINAL PCC"),

      arguments("publicNotes all {value}", "DEVELOPMENT"),
      arguments("notes.note == {value}", "LIBRARIAN PRIVATE NOTE"),
      arguments("notes.note == {value}", "THE DEVELOPMENT OF THE SEMANTIC WEB,"),

      // search by isbn10
      arguments("isbn = {value}", "047144250X"),
      arguments("isbn = {value}", "04714*"),
      arguments("isbn = {value}", "0471-4*"),

      // search by isbn13
      arguments("isbn = {value}", "9780471442509"),
      arguments("isbn = {value}", "978-0471-44250-9"),
      arguments("isbn = {value}", "PAPER"),
      arguments("isbn = {value}", "978-0471*"),
      arguments("isbn = {value}", "9780471*"),

      arguments("issn = {value}", "0317-8471"),
      arguments("issn = {value}", "0317*"),

      // search by oclc
      arguments("oclc = {value}", "12345 800630"),
      arguments("oclc = {value}", "OCM60710867"),
      arguments("oclc = {value}", "60710*"),

      // search by lccn
      arguments("lccn = {value}", "2003065165"),
      arguments("lccn = {value}", "*65165"),
      arguments("lccn = {value}", "N 2003075732"),
      arguments("lccn = {value}", "N2003075732"),
      arguments("lccn = {value}", "*75732"),
      arguments("lccn = {value}", "20030*"),

      // search by item fields
      arguments("item.hrid = {value}", "ITEM000000000014"),
      arguments("item.hrid = {value}", "ITEM*"),
      arguments("item.hrid = {value}", "*00014"),
      arguments("item.hrid = {value}", "ITEM*00014"),

      arguments("itemPublicNotes all {value}", "BIBLIOGRAPHICAL REFERENCES"),
      arguments("itemPublicNotes all {value}", "PUBLIC CIRCULATION NOTE"),

      arguments("itemIdentifiers all {value}", "ITEM000000000014"),
      arguments("itemIdentifiers all {value}", "81AE0F60-F2BC-450C-84C8-5A21096DAED9"),
      arguments("itemIdentifiers all {value}", "ITEM_ACCESSION_NUMBER"),
      arguments("itemIdentifiers all {value}", "7212BA6A-8DCF-45A1-BE9A-FFAA847C4423"),
      arguments("itemIdentifiers all {value}", "ITEMIDENTIFIERFIELDVALUE"),

      arguments("item.administrativeNotes == {value}", "NEED ATTENTION"),
      arguments("item.administrativeNotes all {value}", "V1.1"),

      arguments("item.notes.note == {value}", "LIBRARIAN PUBLIC NOTE FOR ITEM"),
      arguments("item.notes.note == {value}", "LIBRARIAN PRIVATE NOTE FOR ITEM"),
      arguments("item.notes.note == {value}", "TESTCIRCULATIONNOTE"),
      arguments("item.notes.note == {value}", "PRIVATE CIRCULATION NOTE"),

      arguments("item.circulationNotes.note == {value}", "TESTCIRCULATIONNOTE"),
      arguments("item.circulationNotes.note all {value}", "PUBLIC CIRCULATION NOTE"),
      arguments("item.circulationNotes.note all {value}", "PRIVATE CIRCULATION NOTE"),
      arguments("item.circulationNotes.note all {value}", "*NOTE"),
      arguments("item.circulationNotes.note all {value}", "PRIVATE CIRCULATION*"),

      arguments("item.electronicAccess==\"{value}\"", "TABLE"),
      arguments("item.electronicAccess.uri==\"{value}\"", "HTTPS://WWW.LOC.GOV/CATDIR/TOC/ECIP0718/2007020429.HTML"),
      arguments("item.electronicAccess.linkText all {value}", "LINKS AVAILABLE"),
      arguments("item.electronicAccess.publicNote all {value}", "TABLE OF CONTENTS"),

      // Search by item fields (Backward compatibility)
      arguments("items.hrid = {value}", "ITEM000000000014"),
      arguments("items.hrid = {value}", "ITEM*"),
      arguments("items.hrid = {value}", "*00014"),
      arguments("items.hrid = {value}", "ITEM*00014"),

      arguments("items.notes.note == {value}", "LIBRARIAN PUBLIC NOTE FOR ITEM"),
      arguments("items.notes.note == {value}", "LIBRARIAN PRIVATE NOTE FOR ITEM"),
      arguments("items.notes.note == {value}", "TESTCIRCULATIONNOTE"),
      arguments("items.notes.note == {value}", "PRIVATE CIRCULATION NOTE"),

      arguments("items.tags.tagList all {value}", "ITEM-TAG"),

      arguments("items.circulationNotes.note == {value}", "TESTCIRCULATIONNOTE"),
      arguments("items.circulationNotes.note all {value}", "PUBLIC CIRCULATION NOTE"),
      arguments("items.circulationNotes.note all {value}", "PRIVATE CIRCULATION NOTE"),
      arguments("items.circulationNotes.note all {value}", "*NOTE"),
      arguments("items.circulationNotes.note all {value}", "PRIVATE CIRCULATION*"),

      arguments("items.electronicAccess==\"{value}\"", "TABLE"),
      arguments("items.electronicAccess.uri==\"{value}\"", "HTTPS://WWW.LOC.GOV/CATDIR/TOC/ECIP0718/2007020429.HTML"),
      arguments("items.electronicAccess.linkText all {value}", "LINKS AVAILABLE"),
      arguments("items.electronicAccess.publicNote all {value}", "TABLE OF CONTENTS"),

      // search by holding fields
      arguments("holdings.administrativeNotes == {value}", "FOR DELETION"),
      arguments("holdings.administrativeNotes all {value}", "V2.0"),
      arguments("holdingsPublicNotes all {value}", "BIBLIOGRAPHICAL REFERENCES"),
      arguments("holdings.notes.note == {value}", "LIBRARIAN PUBLIC NOTE FOR HOLDING"),
      arguments("holdings.notes.note == {value}", "LIBRARIAN PRIVATE NOTE FOR HOLDING"),
      arguments("holdings.tags.tagList == {value}", "HOLDINGS-TAG"),

      arguments("holdings.electronicAccess==\"{value}\"", "ELECTRONICACCESS"),
      arguments("holdings.electronicAccess.uri==\"{value}\"", "HTTPS://TESTLIBRARY.SAMPLE.COM/HOLDINGS?HRID=HO0000006"),
      arguments("holdings.electronicAccess.linkText all {value}", "LINK TEXT"),
      arguments("holdings.electronicAccess.publicNote all {value}", "NOTE"),

      arguments("holdingsIdentifiers all {value}", "HOLD000000000009"),
      arguments("holdingsIdentifiers == {value}", "1D76EE84-D776-48D2-AB96-140C24E39AC5"),
      arguments("holdingsIdentifiers all {value}", "9B8EC096-FA2E-451B-8E7A-6D1C977EE946"),
      arguments("holdingsIdentifiers all {value}", "E3FF6133-B9A2-4D4C-A1C9-DC1867D4DF19"),

      //search by multiple different parameters
      arguments("(keyword all \"{value}\")", "WOLVES MATTHEW 9781609383657"),
      arguments("(keyword all \"{value}\")", "A SEMANTIC WEB PRIMER : WOLVES"),
      arguments("(keyword all \"{value}\")", "A SEMANTIC WEB PRIMER & WOLVES"),
      arguments("(keyword all \"{value}\")", "A SEMANTIC WEB PRIMER / WOLVES"),
      arguments("keyword == {value}", "VAN HARMELEN, FRANK"),
      arguments("keyword ==/string {value}", "0262012103"),
      arguments("(title all \"{value}\")", "A SEMANTIC WEB PRIMER : 0747-0850")
    );
  }

  private static Stream<Arguments> testIssnDataProvider() {
    return Stream.of(
      arguments("issn = {value}", "0040-781X"),
      arguments("issn = {value}", "0040-781x"),
      arguments("issn = {value}", "*0-781X"),
      arguments("issn = {value}", "*0-781x"),
      arguments("issn = {value}", "**0-781X"),
      arguments("issn = {value}", "**0-781x"),
      arguments("issn = {value}", "***0-***X"),
      arguments("issn = {value}", "***0-***x"),
      arguments("issn = {value}", "00*0-*8*X"),
      arguments("issn = {value}", "00*0-*8*x"),
      arguments("issn = {value}", "***0-***X"),
      arguments("issn = {value}", "***0-***x"),
      arguments("issn = {value}", "***0-*X"),
      arguments("issn = {value}", "***0-**x"),
      arguments("issn = {value}", "0**-**x"),
      arguments("issn = {value}", "0*-*x"),
      arguments("issn = {value}", "*X"),
      arguments("issn = {value}", "*x"),
      arguments("issn = {value}", "0040-781*")
    );
  }
}
