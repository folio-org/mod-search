package org.folio.search.controller;

import org.folio.search.domain.dto.Instance;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.folio.search.sample.SampleInstances.getSemanticWebAsMap;
import static org.folio.search.sample.SampleInstances.getSemanticWebId;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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


  private static Stream<Arguments> testDataProvider() {
    return Stream.of(
      arguments("alternativeTitles.alternativeTitle == (\"An Uniform title\" OR \"An alternative title\") "
                + "AND alternativeTitles.alternativeTitleTypeId == {value}", "9d968396-0cce-4e9f-8867-c4d04c01f535")
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
