package org.folio.api.search;

import static org.folio.cql2pgjson.model.CqlSort.ASCENDING;
import static org.folio.cql2pgjson.model.CqlSort.DESCENDING;
import static org.folio.support.base.ApiEndpoints.allRecordsSortedBy;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

public abstract class SortAuthorityIT extends BaseIntegrationTest {

  private static final int RECORDS_COUNT = 5;
  private static final String[] IDS = {
    "50720001-0000-4000-8000-000000000001",
    "50720001-0000-4000-8000-000000000002",
    "50720001-0000-4000-8000-000000000003",
    "50720001-0000-4000-8000-000000000004",
    "50720001-0000-4000-8000-000000000005"
  };
  private static final String AUTHORITY_ID_FILTER =
    "id==(" + IDS[0] + " OR " + IDS[1] + " OR " + IDS[2] + " OR " + IDS[3] + " OR " + IDS[4] + ")";

  private static String scoped(String query) {
    return AUTHORITY_ID_FILTER + " AND (" + query + ")";
  }

  @Test
  void canSortAuthoritiesByHeadingRef_asc() throws Exception {
    doSearchByAuthorities(scoped(allRecordsSortedBy("headingRef", ASCENDING)))
      .andExpect(jsonPath("totalRecords", is(RECORDS_COUNT)))
      .andExpect(jsonPath("authorities[0].headingRef", is("111")))
      .andExpect(jsonPath("authorities[1].headingRef", is("aaa")))
      .andExpect(jsonPath("authorities[2].headingRef", is("ccc")))
      .andExpect(jsonPath("authorities[3].headingRef", is("ŚŚŚ")))
      .andExpect(jsonPath("authorities[4].headingRef", is("zzz")));
  }

  @Test
  void canSortAuthoritiesByHeadingRef_desc() throws Exception {
    doSearchByAuthorities(scoped(allRecordsSortedBy("headingRef", DESCENDING)))
      .andExpect(jsonPath("totalRecords", is(RECORDS_COUNT)))
      .andExpect(jsonPath("authorities[0].headingRef", is("zzz")))
      .andExpect(jsonPath("authorities[1].headingRef", is("ŚŚŚ")))
      .andExpect(jsonPath("authorities[2].headingRef", is("ccc")))
      .andExpect(jsonPath("authorities[3].headingRef", is("aaa")))
      .andExpect(jsonPath("authorities[4].headingRef", is("111")));
  }

  @Test
  void canSortAuthoritiesByHeadingType_asc() throws Exception {
    doSearchByAuthorities(scoped(allRecordsSortedBy("headingType", ASCENDING)))
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
    doSearchByAuthorities(scoped(allRecordsSortedBy("headingType", DESCENDING)))
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
    doSearchByAuthorities(scoped(allRecordsSortedBy("authRefType", ASCENDING)))
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
    doSearchByAuthorities(scoped(allRecordsSortedBy("authRefType", DESCENDING)))
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
    attemptSearchByAuthorities(scoped(allRecordsSortedBy("unknownSort", ASCENDING)))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Sort field not found or cannot be used.")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("sortField")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("unknownSort")));
  }
}
