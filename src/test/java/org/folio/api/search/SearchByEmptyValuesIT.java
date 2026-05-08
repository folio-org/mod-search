package org.folio.api.search;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.apache.commons.lang3.StringUtils;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@IntegrationTest
public abstract class SearchByEmptyValuesIT extends BaseSharedTest {

  public static final String INSTANCE_ID_1 = "00000003-0000-4000-8000-000000000000";
  public static final String INSTANCE_ID_2 = "00000002-0000-4000-8000-000000000000";

  private static final String TAG_FILTER = "tags.tagList==\"search-by-empty-values\"";

  @CsvSource({
    "cql.allRecords=1, title1;title2",
    "instanceTypeId=\"\", title1;title2",
    "isbn=\"\",",
    "cql.allRecords=1 NOT isbn=\"\", title1;title2",
    "cql.allRecords=1 NOT instanceTypeId=\"\",",
    "contributors.name==[], title2",
    "indexTitle=\"\" NOT indexTitle==\"\", title1",
    "cql.allRecords=1 NOT indexTitle=\"\", title2",
    "subjects.value==[], title1;title2",
  })
  @ParameterizedTest
  void search_parameterized(String query, String titles) throws Exception {
    var expectedTitles = StringUtils.isNotEmpty(titles) ? asList(titles.split(";")) : null;
    doSearchByInstances(TAG_FILTER + " AND (" + query + ")" + " sortBy title")
      .andExpect(jsonPath("totalRecords", is(expectedTitles == null ? 0 : expectedTitles.size())))
      .andExpect(expectedTitles == null
                 ? jsonPath("instances[*].title").doesNotExist()
                 : jsonPath("instances[*].title", is(expectedTitles)));
  }
}
