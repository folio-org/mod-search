package org.folio.api.search;

import static org.folio.cql2pgjson.model.CqlSort.ASCENDING;
import static org.folio.cql2pgjson.model.CqlSort.DESCENDING;
import static org.folio.search.model.index.AuthRefType.AUTHORIZED;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.cql2pgjson.model.CqlSort;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.Test;

/**
 * Tests authority sorting. All queries are scoped to {@code subjectHeadings=z} to target
 * the dedicated subset of test authorities that carry that subject heading.
 */
public abstract class SortAuthorityIT extends BaseSharedTest {

  private static final int RECORDS_COUNT = 5;

  private static final String BACH_HEADING = "Bach, Johann Sebastian";
  private static final String CAMBRIDGE_HEADING = "Cambridge Press";
  private static final String SPIEWNIK_HEADING = "Śpiewnik staropolski";
  private static final String SUSPENSE_HEADING = "Suspense fiction";
  private static final String ZEROMSKI_HEADING = "Żeromski, Stefan";

  private static final String CORPORATE_NAME_TYPE = "Corporate Name";
  private static final String GENRE_TYPE = "Genre";
  private static final String PERSONAL_NAME_TYPE = "Personal Name";
  private static final String UNIFORM_TITLE_TYPE = "Uniform Title";

  private static String scopedQuerySortedBy(String sort, CqlSort order) {
    return String.format("subjectHeadings=z sortBy %s/sort.%s", sort, order);
  }

  @Test
  void canSortAuthoritiesByHeadingRef_asc() throws Exception {
    doSearchByAuthorities(scopedQuerySortedBy("headingRef", ASCENDING))
      .andExpect(jsonPath("totalRecords", is(RECORDS_COUNT)))
      .andExpect(jsonPath("authorities[0].headingRef", is(BACH_HEADING)))
      .andExpect(jsonPath("authorities[1].headingRef", is(CAMBRIDGE_HEADING)))
      .andExpect(jsonPath("authorities[2].headingRef", is(SPIEWNIK_HEADING)))
      .andExpect(jsonPath("authorities[3].headingRef", is(SUSPENSE_HEADING)))
      .andExpect(jsonPath("authorities[4].headingRef", is(ZEROMSKI_HEADING)));
  }

  @Test
  void canSortAuthoritiesByHeadingRef_desc() throws Exception {
    doSearchByAuthorities(scopedQuerySortedBy("headingRef", DESCENDING))
      .andExpect(jsonPath("totalRecords", is(RECORDS_COUNT)))
      .andExpect(jsonPath("authorities[0].headingRef", is(ZEROMSKI_HEADING)))
      .andExpect(jsonPath("authorities[1].headingRef", is(SUSPENSE_HEADING)))
      .andExpect(jsonPath("authorities[2].headingRef", is(SPIEWNIK_HEADING)))
      .andExpect(jsonPath("authorities[3].headingRef", is(CAMBRIDGE_HEADING)))
      .andExpect(jsonPath("authorities[4].headingRef", is(BACH_HEADING)));
  }

  @Test
  void canSortAuthoritiesByHeadingType_asc() throws Exception {
    doSearchByAuthorities(scopedQuerySortedBy("headingType", ASCENDING))
      .andExpect(jsonPath("totalRecords", is(RECORDS_COUNT)))
      .andExpect(jsonPath("authorities[0].headingType", is(CORPORATE_NAME_TYPE)))
      .andExpect(jsonPath("authorities[1].headingType", is(GENRE_TYPE)))
      .andExpect(jsonPath("authorities[2].headingType", is(PERSONAL_NAME_TYPE)))
      .andExpect(jsonPath("authorities[2].headingRef", is(BACH_HEADING)))
      .andExpect(jsonPath("authorities[3].headingType", is(PERSONAL_NAME_TYPE)))
      .andExpect(jsonPath("authorities[3].headingRef", is(ZEROMSKI_HEADING)))
      .andExpect(jsonPath("authorities[4].headingType", is(UNIFORM_TITLE_TYPE)));
  }

  @Test
  void canSortAuthoritiesByHeadingType_desc() throws Exception {
    doSearchByAuthorities(scopedQuerySortedBy("headingType", DESCENDING))
      .andExpect(jsonPath("totalRecords", is(RECORDS_COUNT)))
      .andExpect(jsonPath("authorities[0].headingType", is(UNIFORM_TITLE_TYPE)))
      .andExpect(jsonPath("authorities[1].headingType", is(PERSONAL_NAME_TYPE)))
      .andExpect(jsonPath("authorities[1].headingRef", is(ZEROMSKI_HEADING)))
      .andExpect(jsonPath("authorities[2].headingType", is(PERSONAL_NAME_TYPE)))
      .andExpect(jsonPath("authorities[2].headingRef", is(BACH_HEADING)))
      .andExpect(jsonPath("authorities[3].headingType", is(GENRE_TYPE)))
      .andExpect(jsonPath("authorities[4].headingType", is(CORPORATE_NAME_TYPE)));
  }

  @Test
  void canSortAuthoritiesByAuthRefType_asc() throws Exception {
    doSearchByAuthorities(scopedQuerySortedBy("authRefType", ASCENDING))
      .andExpect(jsonPath("totalRecords", is(RECORDS_COUNT)))
      .andExpect(jsonPath("authorities[0].authRefType", is(AUTHORIZED.getTypeValue())))
      .andExpect(jsonPath("authorities[0].headingRef", is(BACH_HEADING)))
      .andExpect(jsonPath("authorities[1].authRefType", is(AUTHORIZED.getTypeValue())))
      .andExpect(jsonPath("authorities[1].headingRef", is(CAMBRIDGE_HEADING)))
      .andExpect(jsonPath("authorities[2].headingRef", is(SPIEWNIK_HEADING)))
      .andExpect(jsonPath("authorities[2].authRefType", is(AUTHORIZED.getTypeValue())))
      .andExpect(jsonPath("authorities[3].headingRef", is(SUSPENSE_HEADING)))
      .andExpect(jsonPath("authorities[3].authRefType", is(AUTHORIZED.getTypeValue())))
      .andExpect(jsonPath("authorities[4].authRefType", is(AUTHORIZED.getTypeValue())))
      .andExpect(jsonPath("authorities[4].headingRef", is(ZEROMSKI_HEADING)));

  }

  @Test
  void canSortAuthoritiesByAuthRefType_desc() throws Exception {
    doSearchByAuthorities(scopedQuerySortedBy("authRefType", DESCENDING))
      .andExpect(jsonPath("totalRecords", is(RECORDS_COUNT)))
      .andExpect(jsonPath("authorities[0].authRefType", is(AUTHORIZED.getTypeValue())))
      .andExpect(jsonPath("authorities[0].headingRef", is(ZEROMSKI_HEADING)))
      .andExpect(jsonPath("authorities[1].authRefType", is(AUTHORIZED.getTypeValue())))
      .andExpect(jsonPath("authorities[1].headingRef", is(SUSPENSE_HEADING)))
      .andExpect(jsonPath("authorities[2].authRefType", is(AUTHORIZED.getTypeValue())))
      .andExpect(jsonPath("authorities[2].headingRef", is(SPIEWNIK_HEADING)))
      .andExpect(jsonPath("authorities[3].authRefType", is(AUTHORIZED.getTypeValue())))
      .andExpect(jsonPath("authorities[3].headingRef", is(CAMBRIDGE_HEADING)))
      .andExpect(jsonPath("authorities[4].authRefType", is(AUTHORIZED.getTypeValue())))
      .andExpect(jsonPath("authorities[4].headingRef", is(BACH_HEADING)));
  }

  @Test
  void search_negative_invalidSortOption() throws Exception {
    attemptSearchByAuthorities(scopedQuerySortedBy("unknownSort", ASCENDING))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Sort field not found or cannot be used.")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("sortField")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("unknownSort")));
  }
}
