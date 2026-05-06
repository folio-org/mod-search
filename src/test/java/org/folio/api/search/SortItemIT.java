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
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@IntegrationTest
@SuppressWarnings("checkstyle:DeclarationOrder")
public abstract class SortItemIT extends BaseIntegrationTest {

  private static final String[] IDS = array(
    "497a920b-225f-44c2-9a48-964c52fb5cc3",
    "b3122ab1-8745-43a9-8b31-8a248bef387b",
    "9e20dd36-e46e-4e12-b35e-f3339a947966",
    "36805693-dd16-400e-be24-7e9a362f79fe",
    "414f663b-b97b-42ed-8a96-3195067126ff");
  private static final String[] HOLDINGS_IDS = array(
    "7ac8e203-defb-4bf9-9f87-4ffd67823ad8",
    "8a3f2f3a-ecf4-49fd-b58f-5129ae5a128d",
    "1650bee3-1e90-4815-ac12-31d1a12bd7c2",
    "0646ce7c-efcd-4449-ba29-f5aba3ec7690",
    "bafb734e-88f4-4fbe-bd78-119630d225bb");
  private static final String[] ITEM_IDS = array(
    "e0000001-0000-4000-a000-000000000000",
    "e0000002-0000-4000-a000-000000000000",
    "e0000003-0000-4000-a000-000000000000",
    "e0000004-0000-4000-a000-000000000000",
    "e0000005-0000-4000-a000-000000000000",
    "e0000006-0000-4000-a000-000000000000",
    "e0000007-0000-4000-a000-000000000000",
    "e0000008-0000-4000-a000-000000000000",
    "e0000009-0000-4000-a000-000000000000",
    "e0000010-0000-4000-a000-000000000000",
    "e0000011-0000-4000-a000-000000000000",
    "e0000012-0000-4000-a000-000000000000",
    "e0000013-0000-4000-a000-000000000000",
    "e0000014-0000-4000-a000-000000000000",
    "e0000015-0000-4000-a000-000000000000",
    "e0000016-0000-4000-a000-000000000000",
    "e0000017-0000-4000-a000-000000000000",
    "e0000018-0000-4000-a000-000000000000",
    "e0000019-0000-4000-a000-000000000000",
    "e0000020-0000-4000-a000-000000000000",
    "e0000021-0000-4000-a000-000000000000");

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
      arguments(tagged("(id=*) sortby title"), asIdsList(0, 2, 3, 4, 1)),
      arguments(tagged("(id=*) sortby title/sort.descending"), asIdsList(1, 4, 3, 2, 0)),
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
