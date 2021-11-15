package org.folio.search.controller;

import static java.util.Arrays.stream;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.folio.search.sample.SampleAuthorities.getAuthoritySample;
import static org.folio.search.sample.SampleAuthorities.getAuthoritySampleAsMap;
import static org.folio.search.sample.SampleAuthorities.getAuthoritySampleId;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthoritySearchResult;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.BeanUtils;

@IntegrationTest
class SearchAuthorityIT extends BaseIntegrationTest {

  private static final Set<String> COMMON_FIELDS = Set.of("id", "identifiers", "subjectHeadings", "metadata", "notes");

  @BeforeAll
  static void prepare() {
    setUpTenant(Authority.class, 7, getAuthoritySampleAsMap());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @MethodSource("testDataProvider")
  @DisplayName("search by authorities (single authority found)")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}''")
  void searchByAuthorities_parameterized(String query, String value) throws Exception {
    doSearchByAuthorities(prepareQuery(query, value))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.authorities[0].id", is(getAuthoritySampleId())));
  }

  @CsvSource({
    "cql.allRecords=1,",
    "id={value}, \"\"",
    "id=={value}, 55294032-fcf6-45cc-b6da-4420a61ef72c",
    "id=={value}, 55294032-fcf6-45cc-b6da-*"
  })
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}''")
  @DisplayName("search by authorities (check that they are divided correctly)")
  void searchByAuthorities_parameterized_all(String query, String value) throws Exception {
    var response = doSearchByAuthorities(prepareQuery(query, value)).andExpect(jsonPath("$.totalRecords", is(7)));
    var actual = parseResponse(response, AuthoritySearchResult.class);
    var srcAuthority = getAuthoritySample();
    assertThat(actual.getAuthorities()).isEqualTo(List.of(
      expectedAuthority(srcAuthority, "Personal Name", "personalName", "sftPersonalName", "saftPersonalName"),
      expectedAuthority(srcAuthority, "Corporate Name", "corporateName", "sftCorporateName", "saftCorporateName"),
      expectedAuthority(srcAuthority, "Meeting Name", "meetingName", "sftMeetingName", "saftMeetingName"),
      expectedAuthority(srcAuthority, "Geographic Name", "geographicName", "sftGeographicTerm", "saftGeographicTerm"),
      expectedAuthority(srcAuthority, "Uniform Title", "uniformTitle", "sftUniformTitle", "saftUniformTitle"),
      expectedAuthority(srcAuthority, "Topical", "topicalTerm", "sftTopicalTerm", "saftTopicalTerm"),
      expectedAuthority(srcAuthority, "Genre", "genreTerm", "sftGenreTerm", "saftGenreTerm")
    ));
  }

  private static Stream<Arguments> testDataProvider() {
    return Stream.of(
      arguments("personalName all {value}", "\"Gary A. Wills\""),
      arguments("personalName all {value}", "gary"),
      arguments("personalName == {value}", "\"gary a.*\"")
    );
  }

  private static Authority expectedAuthority(Authority source, String headingType, String... fields) {
    var authority = new Authority().headingType(headingType);
    var fieldsToKeep = Set.of(fields);

    var authorityFields = stream(Authority.class.getDeclaredFields())
      .map(Field::getName)
      .filter(fieldName -> !COMMON_FIELDS.contains(fieldName))
      .filter(fieldName -> !fieldsToKeep.contains(fieldName))
      .toArray(String[]::new);

    BeanUtils.copyProperties(source, authority, authorityFields);
    return authority;
  }
}
