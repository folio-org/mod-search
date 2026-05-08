package org.folio.api.search;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.Test;

@IntegrationTest
public abstract class SortInstanceByTitleIT extends BaseSharedTest {

  public static final List<String> TITLES = List.of(
    "Ground water in Africa",
    "Ground water in North Africa",
    "Ground-water hydrology of the Chad Basin",
    "Ground-water resources of Bengasi area",
    "Ground-water exploration in Al Marj (1964)",
    "Regional ground-water hydrology of Tunisia",
    "Occurrence of ground water in Tabulbah (study 1)",
    "Evaluation of ground-water conditions in Cape Verde",
    "Significance of ground-water chemistry in North Sahara",
    "Occurrence of ground water in Tabulbah (study 2)",
    "Ground water in Sirte, Libya",
    "Ground water in Eastern and Southern Africa",
    "Ground-water exploration in Al Marj (1966)"
  );

  private static final String TAG_FILTER = "tags.tagList==\"sort-instance-by-title\"";

  @Test
  void canSortInstancesByTitles_asc() throws Exception {
    var expectedTitleOrder = TITLES.stream()
      .sorted(String::compareToIgnoreCase)
      .toList();

    doSearchByInstances(TAG_FILTER + " sortBy title")
      .andExpect(jsonPath("totalRecords", is(13)))
      .andExpect(jsonPath("instances[*].title", is(expectedTitleOrder)));
  }
}
