package org.folio.api.search;

import static org.folio.support.sample.SampleInstances.getSemanticWebAsMap;
import static org.folio.support.sample.SampleInstances.getSemanticWebId;
import static org.folio.support.sample.SampleInstances.getSemanticWebMatchers;
import static org.folio.support.sample.SampleInstancesResponse.getInstanceBasicResponseSample;
import static org.folio.support.sample.SampleInstancesResponse.getInstanceFullResponseSample;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;

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

  @CsvFileSource(resources = "/test-resources/instance-search-test-queries.csv",
                 useHeadersInDisplayName = true)
  @DisplayName("search by instances (single instance found)")
  @ParameterizedTest(name = "[{0}] {1}, {2}")
  void searchByInstances_parameterized_singleResult(int index, String query, String value) throws Throwable {
    doSearchByInstances(prepareQuery(query, value))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.instances[0].id", is(getSemanticWebId())));
  }

  @Test
  @DisplayName("search by instances (no instance found)")
  void searchByInstances_parameterized_noResult() throws Throwable {
    doSearchByInstances(prepareQuery("id=\"{value}\"", "random-val"))
      .andExpect(jsonPath("$.totalRecords", is(0)))
      .andExpect(jsonPath("$.instances", notNullValue()));
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
    "issn = {value}, 03178472",
    "oclc = {value}, 0262012103",
    "lccn = {value}, canceledlccn",
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
  void responseContainsRequestedProperties() throws Exception {
    doSearchByInstances(prepareQuery("id=={value}", getSemanticWebId()), "subjects.value,items.volume")
      .andExpect(jsonPath("$.instances[0].subjects[0].value", is("Semantic Web")))
      // subjects.authorityId is not requested
      .andExpect(jsonPath("$.instances[0].subjects[0].authorityId").doesNotExist())
      // items.volume is not indexed
      .andExpect(jsonPath("$.instances[0].items").doesNotExist());
  }

  @Test
  void responseContainsAllInstanceProperties() {
    var expected = getInstanceFullResponseSample();
    var response = doSearchByInstances(prepareQuery("id=={value}", getSemanticWebId()), true);

    var actual = parseResponse(response, InstanceSearchResult.class);

    Assertions.assertThat(actual).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(expected);
  }
}
