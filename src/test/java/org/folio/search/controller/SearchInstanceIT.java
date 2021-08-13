package org.folio.search.controller;

import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.support.base.ApiEndpoints.instanceIds;
import static org.folio.search.support.base.ApiEndpoints.searchInstancesByQuery;
import static org.folio.search.utils.TestUtils.array;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class SearchInstanceIT extends BaseIntegrationTest {

  @SuppressWarnings("unused")
  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("positiveSearchTestDataProvider")
  @DisplayName("can search by instances (index with only 1 instance)")
  void canSearchByInstances(String testName, String query, Object[] queryArguments,
    ThrowingConsumer<ResultActions> extendedSearchResultMatcher) throws Throwable {
    var searchResult = mockMvc.perform(get(searchInstancesByQuery(query), queryArguments).headers(defaultHeaders()));
    if (extendedSearchResultMatcher != null) {
      extendedSearchResultMatcher.accept(searchResult);
    } else {
      searchResult
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalRecords", is(1)))
        .andExpect(jsonPath("instances[0].id", is(getSemanticWeb().getId())));
    }
  }

  @Test
  void streamInstanceIds() throws Exception {
    mockMvc.perform(get(instanceIds("id=*")).headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("ids[0].id", is(getSemanticWeb().getId())));
  }

  // Test source
  @SuppressWarnings("unused")
  private static Stream<Arguments> positiveSearchTestDataProvider() {
    return Stream.of(
      arguments("search by instance id", "id={value}", array(getSemanticWeb().getId()), null),
      arguments("search by instance id for exactMatch", "id=={value}", array(getSemanticWeb().getId()), null),
      arguments("search by instance id using wildcard", "id={value}", array("5bf370e0*a0a39"), null),
      arguments("search by instance title (title)", "title all {value}", array("semantic"), null),
      arguments("search by instance title (series)", "title all {value}", array("cooperative"), null),
      arguments("search by instance title (series, partial)", "title all {value}", array("cooperate"), null),
      arguments("search by instance title (part of title)", "title all {value}", array("primers"), null),
      arguments("search by instance title (alternative title)", "title all {value}", array("primers"), null),
      arguments("search by title (phrase match)", "title=={value}", array("semantic web"), null),
      arguments("search by title (phrase match - NO MATCH)", "title=={value}", array("web semantic"),
        zeroResultConsumer()),
      arguments("search by title (ASCII folding, match origin)", "title=={value}", array("déjà vu"), null),
      arguments("search by title (ASCII folding)", "title=={value}", array("deja vu"), null),
      arguments("search by title (Unicode folding, match origin)", "title all {value}", array("Algérie"), null),
      // e here should replace e + U+0301 (Combining Acute Accent)
      arguments("search by title (Unicode folding)", "title all {value}", array("algerie"), null),

      arguments("search by instance title (and operator)", "title all {value}", array("system information"), null),
      arguments("search by instance title (zero results)", "title all {value}",
        array("semantic web word"), zeroResultConsumer()),
      arguments("search by instance title (search across 2 fields)", "title all {value}",
        array("systems alternative semantic"), null),

      arguments("search by series using title alias", "title all {value}", array("cooperate"), null),
      arguments("search by identifiers (wildcard)", "identifiers.value all {value}", array("200306*"), null),

      arguments("search by series", "series all {value}", array("Cooperative information systems"), null),

      arguments("search by alternative title",
        "alternativeTitles.alternativeTitle all {value}", array("uniform"), null),
      arguments("search by alternative title",
        "alternativeTitles.alternativeTitle all {value}", array("deja vu"), null),

      arguments("search by uniform title (uniform)", "uniformTitle all {value}", array("uniform"), null),
      arguments("search by uniform title (deja vu)",
        "uniformTitle all {value}", array("deja vu"), zeroResultConsumer()),

      arguments("search by publisher (abbreviate)", "publisher all {value}", array("MIT"), null),
      arguments("search by publisher (abbreviate lowercase)", "publisher all {value}", array("mit"), null),
      arguments("search by publisher (word)", "publisher all {value}", array("press"), null),

      arguments("search by contributors name", "contributors.name all {value}", array("frank"),
        (ThrowingConsumer<ResultActions>) searchResult -> searchResult
          .andExpect(jsonPath("totalRecords", is(1)))
          .andExpect(jsonPath("instances[0].id", is(getSemanticWeb().getId())))
          .andExpect(jsonPath("instances[0].contributors[0].name", is("Antoniou, Grigoris")))
          .andExpect(jsonPath("instances[0].contributors[1].name", is("Van Harmelen, Frank")))),
      arguments("search by contributors name", "contributors.name all {value}", array("franks"), zeroResultConsumer()),
      arguments("search by contributors alias", "contributors all {value}", array("grigoris"), null),

      arguments("search by hrid for exact match", "hrid={value}", array("inst000000000022"), null),
      arguments("search by hrid with wildcard (starts with)", "hrid=={value}", array("inst000*"), null),
      arguments("search by hrid with wildcard (ends with)", "hrid=={value}", array("*00022"), null),
      arguments("search by hrid with wildcard (contains)", "hrid=={value}", array("*00000002*"), null),

      arguments("search by keyword that matches title", "keyword all {value}", array("primer"), null),

      arguments("search by subjects", "subjects all {value}", array("semantic"), null),
      arguments("search by tags", "tags.tagList all {value}", array("book"), null),
      arguments("search by tags (electronic)", "tags.tagList all {value}", array("electronic"), null),
      arguments("search by subjects", "subjects all {value}", array("semantic"), null),

      arguments("search by classification number",
        "classifications.classificationNumber=={value}", array("025.04"), null),
      arguments("search by classification number (using wildcard)",
        "classifications.classificationNumber=={value}", array("025*"), null),

      arguments("search by electronic access (uri)", "electronicAccess.uri==\"{value}\"",
        array("https://testlibrary.sample.com/journal/10.1002/(ISSN)1938-3703"), null),
      arguments("search by electronic access (link text)",
        "electronicAccess.linkText all {value}", array("access"), null),
      arguments("search by electronic access (materials specification)",
        "electronicAccess.materialsSpecification all {value}", array("material"), zeroResultConsumer()),
      arguments("search by electronic access (public note)",
        "electronicAccess.publicNote all {value}", array("online"), null),

      arguments("search by public notes.note", "publicNotes all {value}", array("development"), null),
      arguments("search by public notes.note", "publicNotes all {value}", array("librarian"), zeroResultConsumer()),
      arguments("search by private notes.note", "notes.note == {value}", array("Librarian private note"), null),

      arguments("search by public holdings.notes.note", "holdingPublicNotes all {value}",
        array("bibliographical references"), null),
      arguments("search by private holdings.notes.note using holdingPublicNotes", "holdingPublicNotes == {value}",
        array("librarian private note"), zeroResultConsumer()),
      arguments("search by private holdings.notes.note", "holdings.notes.note == {value}",
        array("Librarian private note for holding"), null),

      arguments("search by public items.notes.note", "itemPublicNotes all {value}",
        array("bibliographical references"), null),
      arguments("search by private items.notes.note using itemPublicNotes", "itemPublicNotes == {value}",
        array("librarian private note for item"), zeroResultConsumer()),
      arguments("search by private items.notes.note", "items.notes.note == {value}",
        array("Librarian private note for item"), null),

      arguments("search by isbn10", "isbn = {value}", array("047144250X"), null),
      arguments("search by isbn10(wildcard)", "isbn = {value}", array("04714*"), null),
      arguments("search by isbn10(wildcard)", "isbn = {value}", array("0471-4*"), null),

      arguments("search by isbn13(normalized)", "isbn = {value}", array("9780471442509"), null),
      arguments("search by isbn13(with hyphens)", "isbn = {value}", array("978-0471-44250-9"), null),
      arguments("search by isbn13(additional information)", "isbn = {value}", array("paper"), null),
      arguments("search by isbn13(wildcard with hyphen)", "isbn = {value}", array("978-0471*"), null),
      arguments("search by isbn13(wildcard without hyphen)", "isbn = {value}", array("9780471*"), null),

      arguments("search by issn(value with hyphen)", "issn = {value}", array("0317-8471"), null),
      arguments("search by issn(value without hyphen)", "issn = {value}", array("03178471"), zeroResultConsumer()),
      arguments("search by issn(wildcard)", "issn = {value}", array("0317*"), null),

      arguments("search by items hrid", "items.hrid = {value}", array("item000000000014"), null),
      arguments("search by items hrid (start with)", "items.hrid = {value}", array("item*"), null),
      arguments("search by items hrid (ends with)", "items.hrid = {value}", array("*00014"), null),
      arguments("search by items hrid (wildcard)", "items.hrid = {value}", array("item*00014"), null),

      arguments("search by items electronic access", "items.electronicAccess==\"{value}\"", array("table"), null),
      arguments("search by items electronic access (uri)", "items.electronicAccess.uri==\"{value}\"",
        array("https://www.loc.gov/catdir/toc/ecip0718/2007020429.html"), null),
      arguments("search by items electronic access (link text)",
        "items.electronicAccess.linkText all {value}", array("links available"), null),
      arguments("search by items electronic access (materials specification)",
        "items.electronicAccess.materialsSpecification all {value}", array("table"), zeroResultConsumer()),
      arguments("search by items electronic access (public note)",
        "items.electronicAccess.publicNote all {value}", array("table of contents"), null),

      arguments("search by items electronic access (uri)", "holdings.electronicAccess==\"{value}\"",
        array("electronicAccess"), null),
      arguments("search by items electronic access (uri)", "holdings.electronicAccess.uri==\"{value}\"",
        array("https://testlibrary.sample.com/holdings?hrid=ho00000000006"), null),
      arguments("search by items electronic access (link text)",
        "holdings.electronicAccess.linkText all {value}", array("link text"), null),
      arguments("search by items electronic access (materials specification)",
        "holdings.electronicAccess.materialsSpecification all {value}", array("specification"), zeroResultConsumer()),
      arguments("search by items electronic access (public note)",
        "holdings.electronicAccess.publicNote all {value}", array("note"), null),

      arguments("search by holding Identifiers hrId (All)",
        "holdingIdentifiers all {value}", array("hold000000000009"), null),
      arguments("search by holding Identifiers formerIds (All)",
        "holdingIdentifiers == {value}", array("1d76ee84-d776-48d2-ab96-140c24e39ac5"), null),
      arguments("search by holding Identifiers formerIds multiple (All)",
        "holdingIdentifiers all {value}", array("9b8ec096-fa2e-451b-8e7a-6d1c977ee946"), null),

      arguments("search by item Identifiers hrId (All)",
        "itemIdentifiers all {value}", array("item000000000014"), null),
      arguments("search by item Identifiers formerId (All)",
        "itemIdentifiers all {value}", array("81ae0f60-f2bc-450c-84c8-5a21096daed9"), null),

      arguments("search by items circulation notes wildcard",
        "items.circulationNotes.note all {value}", array("*Note"), null),
      arguments("search by items circulation notes wildcard",
        "items.circulationNotes.note all {value}", array("test*"), null),
      arguments("search by items circulation notes",
        "items.circulationNotes.note == {value}", array("testNote"), null),
      arguments("search by items circulation notes hyphens are treated",
        "items.circulationNotes.note all {value}", array("first-record"), null),
      arguments("search by items circulation notes case insensitive",
        "items.circulationNotes.note all {value}", array("secondrecord"), null)
    );
  }

  private static ThrowingConsumer<ResultActions> zeroResultConsumer() {
    return searchResult -> searchResult
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(0)));
  }
}
