package org.folio.api.search;

import static org.folio.support.utils.TestUtils.array;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@IntegrationTest
@SuppressWarnings("checkstyle:DeclarationOrder")
public abstract class SortItemIT extends BaseSharedTest {

  private static final String[] IDS = array(
    "00000007-0000-4000-8000-000000000000",
    "00000045-0000-4000-8000-000000000000",
    "00000011-0000-4000-8000-000000000000",
    "00000004-0000-4000-8000-000000000000",
    "00000006-0000-4000-8000-000000000000");

  private static final String TAG = "tags.tagList==\"sort-item\"";

  private static String tagged(String query) {
    int idx = query.toLowerCase().indexOf(" sortby ");
    if (idx >= 0) {
      return "(" + TAG + " AND " + query.substring(0, idx) + ")" + query.substring(idx);
    }
    return "(" + TAG + " AND " + query + ")";
  }

  @MethodSource("sortItemQueryProvider")
  @DisplayName("searchByInstances_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}")
  void searchByInstances_parameterized(String query, List<String> expectedIds) throws Exception {
    doSearchByInstances(query)
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(expectedIds.size())))
      .andExpect(jsonPath("instances[*].id", is(expectedIds)));
  }

  private static Stream<Arguments> sortItemQueryProvider() {
    return Stream.of(
      arguments(tagged("(id=*) sortby item.status.name"), asIdsList(0, 1, 3, 2, 4)),
      arguments(tagged("(id=*) sortby item.status.name/sort.descending"), asIdsList(4, 0, 3, 1, 2)),

      arguments(tagged("(id=*) sortby items.status.name"), asIdsList(0, 1, 3, 2, 4)),
      arguments(tagged("(id=*) sortby items.status.name/sort.descending"), asIdsList(4, 0, 3, 1, 2))
    );
  }

  private static List<String> asIdsList(Integer... indices) {
    return Arrays.stream(indices).map(index -> IDS[index]).toList();
  }
}
