package org.folio.search.controller;

import static org.folio.search.sample.SampleAuthorities.getAuthoritySample;
import static org.folio.search.sample.SampleAuthorities.getAuthoritySampleId;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.stream.Stream;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@IntegrationTest
class SearchAuthorityIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(getAuthoritySample());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @MethodSource("testDataProvider")
  @DisplayName("search by instances (single instance found)")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}''")
  void searchByAuthorities_parameterized(String query, String value) throws Exception {
    doSearchByAuthorities(prepareQuery(query, value))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.authorities[0].id", is(getAuthoritySampleId())));
  }

  private static Stream<Arguments> testDataProvider() {
    return Stream.of(
      arguments("cql.allRecords = 1", ""),
      arguments("id == {value}", getAuthoritySampleId()),
      arguments("id == {value}", "55294032-fcf6-45cc-b6da-*"),
      arguments("personalName all {value}", "\"Gary A. Wills\""),
      arguments("personalName all {value}", "gary"),
      arguments("personalName == {value}", "\"gary a.*\"")
    );
  }
}
