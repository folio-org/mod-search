package org.folio.api.search;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@IntegrationTest
public abstract class SearchByEmptyValuesIT extends BaseSharedTest {

  @CsvSource({
    "cql.allRecords=1, 96",
    "instanceTypeId=\"\", 96",
    "isbn=\"\", 91",
    "cql.allRecords=1 NOT isbn=\"\", 5",
    "cql.allRecords=1 NOT instanceTypeId=\"\", 0",
    "contributors.name==[], 0",
    "indexTitle=\"\" NOT indexTitle==\"\", 96",
    "cql.allRecords=1 NOT indexTitle=\"\", 0",
    "subjects.value==[], 5",
  })
  @ParameterizedTest
  void search_parameterized(String query, String count) throws Exception {
    doSearchByInstances(query + " sortBy title")
      .andExpect(jsonPath("totalRecords", is(Integer.parseInt(count))));
  }
}
