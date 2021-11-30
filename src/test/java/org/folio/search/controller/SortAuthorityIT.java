package org.folio.search.controller;

import static org.folio.cql2pgjson.model.CqlSort.ASCENDING;
import static org.folio.cql2pgjson.model.CqlSort.DESCENDING;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.Collections;
import java.util.stream.IntStream;
import org.folio.cql2pgjson.model.CqlSort;
import org.folio.search.domain.dto.Authority;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class SortAuthorityIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(4, authorities());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void canSortAuthoritiesByHeadingType_asc() throws Exception {
    doSearchByAuthorities(allAuthoritiesSortedBy("headingType", ASCENDING))
      .andExpect(jsonPath("totalRecords", is(4)))
      .andExpect(jsonPath("authorities[0].headingType", is("Corporate Name")))
      .andExpect(jsonPath("authorities[1].headingType", is("Other")))
      .andExpect(jsonPath("authorities[2].headingType", is("Personal Name")))
      .andExpect(jsonPath("authorities[3].headingType", is("Uniform Title")));
  }

  @Test
  void canSortAuthoritiesByHeadingType_desc() throws Exception {
    doSearchByAuthorities(allAuthoritiesSortedBy("headingType", DESCENDING))
      .andExpect(jsonPath("totalRecords", is(4)))
      .andExpect(jsonPath("authorities[0].headingType", is("Uniform Title")))
      .andExpect(jsonPath("authorities[1].headingType", is("Personal Name")))
      .andExpect(jsonPath("authorities[2].headingType", is("Other")))
      .andExpect(jsonPath("authorities[3].headingType", is("Corporate Name")));
  }

  @Test
  void canSortAuthoritiesByHeadingRef_asc() throws Exception {
    doSearchByAuthorities(allAuthoritiesSortedBy("headingRef", ASCENDING))
      .andExpect(jsonPath("totalRecords", is(4)))
      .andExpect(jsonPath("authorities[0].headingRef", is("111")))
      .andExpect(jsonPath("authorities[1].headingRef", is("aaa")))
      .andExpect(jsonPath("authorities[2].headingRef", is("bbb")))
      .andExpect(jsonPath("authorities[3].headingRef", is("ccc")));
  }

  @Test
  void canSortAuthoritiesByHeadingRef_desc() throws Exception {
    doSearchByAuthorities(allAuthoritiesSortedBy("headingRef", DESCENDING))
      .andExpect(jsonPath("totalRecords", is(4)))
      .andExpect(jsonPath("authorities[0].headingRef", is("ccc")))
      .andExpect(jsonPath("authorities[1].headingRef", is("bbb")))
      .andExpect(jsonPath("authorities[2].headingRef", is("aaa")))
      .andExpect(jsonPath("authorities[3].headingRef", is("111")));
  }

  @Test
  void canSortAuthoritiesByAuthRefType_asc() throws Exception {
    doSearchByAuthorities(allAuthoritiesSortedBy("authRefType", ASCENDING))
      .andExpect(jsonPath("totalRecords", is(4)))
      .andExpect(jsonPath("authorities[0].authRefType", is("Auth/Ref")))
      .andExpect(jsonPath("authorities[1].authRefType", is("Authorized")))
      .andExpect(jsonPath("authorities[2].authRefType", is("Authorized")))
      .andExpect(jsonPath("authorities[3].authRefType", is("Reference")));
  }

  @Test
  void canSortAuthoritiesByAuthRefType_desc() throws Exception {
    doSearchByAuthorities(allAuthoritiesSortedBy("authRefType", DESCENDING))
      .andExpect(jsonPath("totalRecords", is(4)))
      .andExpect(jsonPath("authorities[0].authRefType", is("Reference")))
      .andExpect(jsonPath("authorities[1].authRefType", is("Authorized")))
      .andExpect(jsonPath("authorities[2].authRefType", is("Authorized")))
      .andExpect(jsonPath("authorities[3].authRefType", is("Auth/Ref")));
  }

  @Test
  void search_negative_invalidSortOption() throws Exception {
    attemptSearchByAuthorities(allAuthoritiesSortedBy("unknownSort", ASCENDING))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Sort field not found or cannot be used.")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("sortField")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("unknownSort")));
  }

  private static Authority[] authorities() {
    var authorities = IntStream.range(0, 4)
      .mapToObj(i -> new Authority().id(randomId()))
      .toArray(Authority[]::new);

    authorities[0]
      .personalName("111");

    authorities[1]
      .corporateName("aaa");

    authorities[2]
      .sftUniformTitle(Collections.singletonList("bbb"));

    authorities[3]
      .saftGenreTerm(Collections.singletonList("ccc"));

    return authorities;
  }

  private static String allAuthoritiesSortedBy(String sort, CqlSort order) {
    return String.format("cql.allRecords=1 sortBy %s/sort.%s", sort, order);
  }
}
