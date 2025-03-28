package org.folio.api.search;

import static org.folio.cql2pgjson.model.CqlSort.ASCENDING;
import static org.folio.cql2pgjson.model.CqlSort.DESCENDING;
import static org.folio.support.base.ApiEndpoints.allRecordsSortedBy;
import static org.folio.support.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.Collections;
import java.util.stream.IntStream;
import org.folio.search.domain.dto.Authority;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class SortAuthorityIT extends BaseIntegrationTest {

  private static final int RECORDS_COUNT = 5;

  @BeforeAll
  static void prepare() {
    setUpTenant(RECORDS_COUNT, authorities());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void canSortAuthoritiesByHeadingRef_asc() throws Exception {
    doSearchByAuthorities(allRecordsSortedBy("headingRef", ASCENDING))
      .andExpect(jsonPath("totalRecords", is(RECORDS_COUNT)))
      .andExpect(jsonPath("authorities[0].headingRef", is("111")))
      .andExpect(jsonPath("authorities[1].headingRef", is("aaa")))
      .andExpect(jsonPath("authorities[2].headingRef", is("ccc")))
      .andExpect(jsonPath("authorities[3].headingRef", is("ŚŚŚ")))
      .andExpect(jsonPath("authorities[4].headingRef", is("zzz")));
  }

  @Test
  void canSortAuthoritiesByHeadingRef_desc() throws Exception {
    doSearchByAuthorities(allRecordsSortedBy("headingRef", DESCENDING))
      .andExpect(jsonPath("totalRecords", is(RECORDS_COUNT)))
      .andExpect(jsonPath("authorities[0].headingRef", is("zzz")))
      .andExpect(jsonPath("authorities[1].headingRef", is("ŚŚŚ")))
      .andExpect(jsonPath("authorities[2].headingRef", is("ccc")))
      .andExpect(jsonPath("authorities[3].headingRef", is("aaa")))
      .andExpect(jsonPath("authorities[4].headingRef", is("111")));
  }

  @Test
  void canSortAuthoritiesByHeadingType_asc() throws Exception {
    doSearchByAuthorities(allRecordsSortedBy("headingType", ASCENDING))
      .andExpect(jsonPath("totalRecords", is(RECORDS_COUNT)))
      .andExpect(jsonPath("authorities[0].headingType", is("Corporate Name")))
      .andExpect(jsonPath("authorities[1].headingType", is("Genre")))

      .andExpect(jsonPath("authorities[2].headingType", is("Personal Name")))
      .andExpect(jsonPath("authorities[2].headingRef", is("111")))

      .andExpect(jsonPath("authorities[3].headingType", is("Personal Name")))
      .andExpect(jsonPath("authorities[3].headingRef", is("zzz")))

      .andExpect(jsonPath("authorities[4].headingType", is("Uniform Title")));
  }

  @Test
  void canSortAuthoritiesByHeadingType_desc() throws Exception {
    doSearchByAuthorities(allRecordsSortedBy("headingType", DESCENDING))
      .andExpect(jsonPath("totalRecords", is(RECORDS_COUNT)))
      .andExpect(jsonPath("authorities[0].headingType", is("Uniform Title")))

      .andExpect(jsonPath("authorities[1].headingType", is("Personal Name")))
      .andExpect(jsonPath("authorities[1].headingRef", is("zzz")))

      .andExpect(jsonPath("authorities[2].headingType", is("Personal Name")))
      .andExpect(jsonPath("authorities[2].headingRef", is("111")))

      .andExpect(jsonPath("authorities[3].headingType", is("Genre")))
      .andExpect(jsonPath("authorities[4].headingType", is("Corporate Name")));
  }

  @Test
  void canSortAuthoritiesByAuthRefType_asc() throws Exception {
    doSearchByAuthorities(allRecordsSortedBy("authRefType", ASCENDING))
      .andExpect(jsonPath("totalRecords", is(RECORDS_COUNT)))
      .andExpect(jsonPath("authorities[0].authRefType", is("Auth/Ref")))

      .andExpect(jsonPath("authorities[1].authRefType", is("Authorized")))
      .andExpect(jsonPath("authorities[1].headingRef", is("111")))

      .andExpect(jsonPath("authorities[2].authRefType", is("Authorized")))
      .andExpect(jsonPath("authorities[2].headingRef", is("aaa")))

      .andExpect(jsonPath("authorities[3].authRefType", is("Authorized")))
      .andExpect(jsonPath("authorities[3].headingRef", is("zzz")))

      .andExpect(jsonPath("authorities[4].authRefType", is("Reference")));
  }

  @Test
  void canSortAuthoritiesByAuthRefType_desc() throws Exception {
    doSearchByAuthorities(allRecordsSortedBy("authRefType", DESCENDING))
      .andExpect(jsonPath("totalRecords", is(RECORDS_COUNT)))
      .andExpect(jsonPath("authorities[0].authRefType", is("Reference")))

      .andExpect(jsonPath("authorities[1].authRefType", is("Authorized")))
      .andExpect(jsonPath("authorities[1].headingRef", is("zzz")))

      .andExpect(jsonPath("authorities[2].authRefType", is("Authorized")))
      .andExpect(jsonPath("authorities[2].headingRef", is("aaa")))

      .andExpect(jsonPath("authorities[3].authRefType", is("Authorized")))
      .andExpect(jsonPath("authorities[3].headingRef", is("111")))

      .andExpect(jsonPath("authorities[4].authRefType", is("Auth/Ref")));
  }

  @Test
  void search_negative_invalidSortOption() throws Exception {
    attemptSearchByAuthorities(allRecordsSortedBy("unknownSort", ASCENDING))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Sort field not found or cannot be used.")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("sortField")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("unknownSort")));
  }

  private static Authority[] authorities() {
    var authorities = IntStream.range(0, RECORDS_COUNT)
      .mapToObj(i -> new Authority().id(randomId()))
      .toArray(Authority[]::new);

    authorities[0]
      .personalName("111");

    authorities[1]
      .corporateName("aaa");

    authorities[2]
      .sftUniformTitle(Collections.singletonList("ŚŚŚ"));

    authorities[3]
      .saftGenreTerm(Collections.singletonList("ccc"));

    authorities[4]
      .personalName("zzz");

    return authorities;
  }
}
