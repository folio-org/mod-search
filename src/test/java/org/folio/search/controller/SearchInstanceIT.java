package org.folio.search.controller;

import static org.folio.search.sample.SampleInstances.getSemanticWeb;
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

      arguments("search by publisher (abbreviate)", "publisher all {value}", array("MIT"), null),
      arguments("search by publisher (abbreviate lowercase)", "publisher all {value}", array("mit"), null),
      arguments("search by publisher (word)", "publisher all {value}", array("press"), null),

      arguments("search by contributors name", "contributors.name all {value}", array("frank"),
        (ThrowingConsumer<ResultActions>) searchResult -> searchResult
          .andExpect(jsonPath("totalRecords", is(1)))
          .andExpect(jsonPath("instances[0].id", is(getSemanticWeb().getId())))
          .andExpect(jsonPath("instances[0].contributors[0].name", is("Antoniou, Grigoris")))
          .andExpect(jsonPath("instances[0].contributors[1].name", is("Van Harmelen, Frank")))),
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
        array("http://testlibrary.sample.com/journal/10.1002/(ISSN)1938-3703"), null),
      arguments("search by electronic access (link text)",
        "electronicAccess.linkText all {value}", array("access"), null),
      arguments("search by electronic access (materials specification)",
        "electronicAccess.materialsSpecification all {value}", array("material"), null),
      arguments("search by electronic access (public note)",
        "electronicAccess.publicNote all {value}", array("online"), null),

      arguments("search by notes.note", "publicNotes all {value}", array("development"), null),
      arguments("search by notes.note", "publicNotes all {value}", array("librarian"), zeroResultConsumer()),

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
      arguments("search by issn(wildcard)", "issn = {value}", array("0317*"), null)
    );
  }

  private static ThrowingConsumer<ResultActions> zeroResultConsumer() {
    return searchResult -> searchResult
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(0)));
  }
}
