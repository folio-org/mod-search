package org.folio.search.controller;

import static org.folio.search.sample.SampleInstances.getSemanticWebAsMap;
import static org.folio.search.sample.SampleInstances.getSemanticWebId;
import static org.folio.search.support.base.ApiEndpoints.instanceIds;
import static org.folio.search.utils.TestUtils.randomId;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.folio.search.domain.dto.Instance;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
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
    setUpTenant(Instance.class, getSemanticWebAsMap());
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

  @ParameterizedTest(name = "[{index}] {0}")
  @CsvSource({
    "title == {value}, web semantic",
    "title <> {value}, A semantic web primer",
    "title all {value}, semantic web word",
    "indexTitle <> {value}, Semantic web primer",
    "uniformTitle all {value}, deja vu",
    "uniformTitle all {value}, déjà vu",
    "contributors.name all {value}, franks",
    "electronicAccess.materialsSpecification all {value}, material",
    "items.electronicAccess.materialsSpecification all {value}, table",
    "item.electronicAccess.materialsSpecification all {value}, table",
    "holdings.electronicAccess.materialsSpecification all {value}, specification",
    "publicNotes == {value}, librarian",
    "itemPublicNotes == {value}, private note for item",
    "itemPublicNotes == {value}, private circulation note",
    "holdingsPublicNotes == {value}, librarian private note",
    "issn = {value}, 03178471",
    "oclc = {value}, 0262012103"
  })
  @DisplayName("can search by instances (nothing found)")
  void searchByInstances_parameterized_zeroResults(String query, String value) throws Throwable {
    doSearchByInstances(prepareQuery(query, '"' + value + '"')).andExpect(jsonPath("$.totalRecords", is(0)));
  }

  @Test
  void streamInstanceIds() throws Exception {
    doGet(instanceIds("cql.allRecords=1"))
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("ids[0].id", is(getSemanticWebId())));
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

  private static Stream<Arguments> testDataProvider() {
    return Stream.of(
      arguments("cql.allRecords = 1", getSemanticWebId()),
      arguments("id = {value}", "\"\""),
      arguments("id = {value}", getSemanticWebId()),
      arguments("id = {value}", "5bf370e0*a0a39"),
      arguments("id == {value}", getSemanticWebId()),

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

      arguments("publisher all {value}", "MIT"),
      arguments("publisher all {value}", "mit"),
      arguments("publisher all {value}", "press"),

      arguments("contributors all {value}", "Frank"),
      arguments("contributors all {value}", "grigoris"),
      arguments("contributors all {value}", "Grigoris Antoniou"),
      arguments("contributors any {value}", "Grigoris frank"),
      arguments("contributors all {value}", "Van Harmelen, Frank"),
      arguments("contributors == {value}", "Van Harmelen, Frank"),
      arguments("contributors = {value}", "Van Harmelen, Fr*"),
      arguments("contributors = {value}", "*rmelen, Frank"),

      arguments("contributors.name = {value}", "Van Harmelen, Frank"),
      arguments("contributors.name == {value}", "Van Harmelen, Frank"),
      arguments("contributors.name = {value}", "Van Harmelen, Fr*"),
      arguments("contributors.name = {value}", "Anton*"),
      arguments("contributors.name = {value}", "*rmelen, Frank"),

      arguments("hrid == {value}", "inst000000000022"),
      arguments("hrid == {value}", "inst00*"),
      arguments("hrid == {value}", "*00022"),
      arguments("hrid == {value}", "*00000002*"),

      arguments("keyword = *", ""),
      arguments("keyword all {value}", "semantic web primer"),
      arguments("subjects all {value}", "semantic"),

      arguments("tags.tagList all {value}", "book"),
      arguments("tags.tagList all {value}", "electronic"),

      arguments("classifications.classificationNumber=={value}", "025.04"),
      arguments("classifications.classificationNumber=={value}", "025*"),

      arguments("electronicAccess.uri==\"{value}\"", "https://testlibrary.sample.com/journal/10.1002/(ISSN)1938-3703"),
      arguments("electronicAccess.linkText all {value}", "access"),
      arguments("electronicAccess.publicNote all {value}", "online"),
      arguments("instanceFormatIds == {value}", "7f9c4ac0-fa3d-43b7-b978-3bf0be38c4da"),

      arguments("publicNotes all {value}", "development"),
      arguments("notes.note == {value}", "Librarian private note"),
      arguments("notes.note == {value}", "The development of the Semantic Web,"),
      arguments("callNumber = {value}", "\"\""),

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

      // Search by item fields (Backward compatibility)
      arguments("items.hrid = {value}", "item000000000014"),
      arguments("items.hrid = {value}", "item*"),
      arguments("items.hrid = {value}", "*00014"),
      arguments("items.hrid = {value}", "item*00014"),

      arguments("items.notes.note == {value}", "Librarian public note for item"),
      arguments("items.notes.note == {value}", "Librarian private note for item"),
      arguments("items.notes.note == {value}", "testCirculationNote"),
      arguments("items.notes.note == {value}", "private circulation note"),

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
      arguments("holdingsPublicNotes all {value}", "bibliographical references"),
      arguments("holdings.notes.note == {value}", "Librarian public note for holding"),
      arguments("holdings.notes.note == {value}", "Librarian private note for holding"),

      arguments("holdings.electronicAccess==\"{value}\"", "electronicAccess"),
      arguments("holdings.electronicAccess.uri==\"{value}\"", "https://testlibrary.sample.com/holdings?hrid=ho0000006"),
      arguments("holdings.electronicAccess.linkText all {value}", "link text"),
      arguments("holdings.electronicAccess.publicNote all {value}", "note"),

      arguments("holdingsIdentifiers all {value}", "hold000000000009"),
      arguments("holdingsIdentifiers == {value}", "1d76ee84-d776-48d2-ab96-140c24e39ac5"),
      arguments("holdingsIdentifiers all {value}", "9b8ec096-fa2e-451b-8e7a-6d1c977ee946"),
      arguments("holdingsIdentifiers all {value}", "e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19")
    );
  }
}
