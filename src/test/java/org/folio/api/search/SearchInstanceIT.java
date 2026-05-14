package org.folio.api.search;

import static org.folio.support.sample.SampleInstances.getSemanticWebId;
import static org.folio.support.sample.SampleInstancesResponse.getInstanceBasicResponseSample;
import static org.folio.support.sample.SampleInstancesResponse.getInstanceFullResponseSample;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.folio.search.domain.dto.InstanceSearchResult;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public abstract class SearchInstanceIT extends BaseSharedTest {

  @CsvFileSource(resources = "/test-resources/instance-search-test-queries.csv",
                 useHeadersInDisplayName = true)
  @DisplayName("search by instances (single instance found)")
  @ParameterizedTest(name = "[{0}] {1}, {2}")
  void searchByInstances_parameterized_singleResult(int index, String query, String value,
                                                    String expectedId) throws Throwable {
    var resolvedId = expectedId == null || expectedId.isBlank() ? getSemanticWebId() : expectedId;
    doSearchByInstances(prepareQuery(query, value))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.instances[0].id", is(resolvedId)));
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
    "title <> {value} and id == \"00000008-0000-4000-8000-000000000000\", A semantic web primer",
    "title all {value}, semantic web word",
    "indexTitle <> {value} and id == \"00000008-0000-4000-8000-000000000000\", Semantic web primer",
    "uniformTitle all {value}, deja vu not exist",
    "uniformTitle all {value}, déjà vu not exist",
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

  @ParameterizedTest(name = "[{index}] {0}, {1}")
  @CsvSource({
    "cql.allRecords = 1, ''",
    "keyword = *, ''",
    "indexTitle <> {value}, unknown value",
    "indexTitle <> {value}, UNKNOWN VALUE",
    "title <> {value}, unknown value",
    "title <> {value}, UNKNOWN VALUE",
    "shared == {value}, false",
    "publication.place all {value}, cambridge",
    "publication.place all {value}, Cambridge",
    "publication.place any {value}, Cambridge mass",
    "publication.place == {value}, Cambridge",
    "publisher all {value}, press",
    "publisher all {value}, PRESS",
    "administrativeNotes any {value}, original pcc",
    "administrativeNotes any {value}, ORIGINAL PCC",
    "instanceFormatIds == {value}, 7f9c4ac0-fa3d-43b7-b978-3bf0be38c4da",
    "instanceFormatIds == {value}, 7F9C4AC0-FA3D-43B7-B978-3BF0BE38C4DA",
    "tenantId = {value}, test_tenant",
    "title any {value}, systems alternative semantic",
    "title any {value}, SYSTEMS ALTERNATIVE SEMANTIC"
  })
  @DisplayName("can search by instances (multiple results found)")
  void searchByInstances_parameterized_multipleResults(String query, String value) throws Throwable {
    doSearchByInstances(prepareQuery(query, '"' + value + '"'))
      .andExpect(jsonPath("$.totalRecords", greaterThan(1)));
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

  @MethodSource("invalidDateSearchQueriesProvider")
  @DisplayName("searchByInvalidDates_parameterized")
  @ParameterizedTest(name = "[{index}] value={1}")
  void searchByInstances_negative_invalidDateFormat(String name, String value) throws Exception {
    attemptSearchByInstances("(" + name + "==" + value + ")")
      .andExpect(status().isUnprocessableContent())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Invalid date format")))
      .andExpect(jsonPath("$.errors[0].type", is("ValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is(name)))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(value)));
  }

  @ValueSource(strings = {
    // instance field values
    "00000008-0000-4000-8000-000000000000",
    "A sem\\\\ntic web primer",
    "Cooperative information systems",
    "0262012103",
    "2003065165",
    "Cambridge, Mass.",
    "Ant\\\\niou, Grigoris matthew",
    "Ant\\\\niou matthew",
    "HD1691 .I5 1967",
    "Cambridge, Mass.",
    "1\\\\9u",
    "*\\9u",
    "1*9u",
    "1\\\\9*",
    "1444",
    "144*",
    "1*44",
    "*444",

    // holding field values
    "00000014-0000-4000-9000-000000000000",
    "ho10000000006",
    "TK5105.88815 . A58 2004 FT MEADE",

    // item field values
    "00000004-0000-4000-a000-000000000000",
    "TK5105.88815 . A58 2004 FT MEADE",
    "item000000000014",
    "item_accession_number"
  })
  @ParameterizedTest(name = "[{index}] cql.all='{query}', query=''{0}''")
  void canSearchByAllFieldValues_positive(String cqlQuery) throws Throwable {
    doSearchByInstances(prepareQuery("cql.all=\"{value}\"", cqlQuery), 1, 0)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
  }

  @ValueSource(strings = {
    "00000008-0000-4000-8000-000000000000",
    "A sem\\\\ntic web primer",
    "déjà vu",
    "Cooperative information systems",
    "0262012103",
    "2003065165",
    "Ant\\\\niou, Grigoris matthew",
    "HD1691 .I5 1967",
    "Cambridge, Mass.",
    "2020-12-08T15:47:13.625+0000",
    "1\\\\9u",
    "*\\9u",
    "1*9u",
    "1\\\\9*",
    "1444",
    "144*",
    "1*44",
    "*444"
  })
  @ParameterizedTest(name = "[{index}] cql.allInstances='{query}', query=''{0}''")
  void canSearchByInstanceFieldValues_positive(String cqlQuery) throws Throwable {
    doSearchByInstances(prepareQuery("cql.allInstances=\"{value}\"", cqlQuery), 1, 0)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
  }

  @ValueSource(strings = {
    "00000014-0000-4000-9000-000000000000",
    "ho10000000006",
    "TK5105.88815 . A58 2004 FT MEADE"
  })
  @ParameterizedTest(name = "[{index}] cql.allHoldings='{query}', query=''{0}''")
  void canSearchByHoldingFieldValues_positive(String cqlQuery) throws Throwable {
    doSearchByInstances(prepareQuery("cql.allHoldings=\"{value}\"", cqlQuery), 1, 0)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
  }

  @ValueSource(strings = {
    "00000004-0000-4000-a000-000000000000",
    "TK5105.88815 . A58 2004 FT MEADE",
    "item000000000014",
    "item_accession_number"
  })
  @ParameterizedTest(name = "[{index}] cql.allItems='{query}', query=''{0}''")
  void canSearchByItemFieldValues_positive(String cqlQuery) throws Throwable {
    doSearchByInstances(prepareQuery("cql.allItems=\"{value}\"", cqlQuery), 1, 0)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
  }

  private static Stream<Arguments> invalidDateSearchQueriesProvider() {
    return Stream.of(
      arguments("metadata.createdDate", "2022-6-27"),
      arguments("metadata.updatedDate", "2022-06-1"),

      arguments("holdings.metadata.createdDate", "2022-15-01"),
      arguments("holdings.metadata.updatedDate", "2022-06-40"),

      arguments("item.metadata.updatedDate", "invalidDate")
    );
  }
}
