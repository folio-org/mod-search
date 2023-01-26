package org.folio.search.controller;

import static org.folio.search.utils.TestUtils.array;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemStatus;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.spring.test.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@IntegrationTest
class SortItemIT extends BaseIntegrationTest {

  private static final String[] IDS = array(
    "497a920b-225f-44c2-9a48-964c52fb5cc3",
    "b3122ab1-8745-43a9-8b31-8a248bef387b",
    "9e20dd36-e46e-4e12-b35e-f3339a947966",
    "36805693-dd16-400e-be24-7e9a362f79fe",
    "414f663b-b97b-42ed-8a96-3195067126ff");

  @BeforeAll
  static void prepare() {
    setUpTenant(instances());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
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
      arguments("(id=*) sortby title", asIdsList(0, 2, 3, 4, 1)),
      arguments("(id=*) sortby title/sort.descending", asIdsList(1, 4, 3, 2, 0)),
      arguments("(id=*) sortby item.status.name", asIdsList(0, 1, 3, 2, 4)),
      arguments("(id=*) sortby item.status.name/sort.descending", asIdsList(4, 0, 3, 1, 2)),

      arguments("(id=*) sortby items.status.name", asIdsList(0, 1, 3, 2, 4)),
      arguments("(id=*) sortby items.status.name/sort.descending", asIdsList(4, 0, 3, 1, 2))
    );
  }

  private static List<String> asIdsList(Integer... indices) {
    return Arrays.stream(indices).map(index -> IDS[index]).toList();
  }

  private static Instance[] instances() {
    var instances = IntStream.range(0, 5)
      .mapToObj(i -> new Instance().id(IDS[i]))
      .toArray(Instance[]::new);

    instances[0].title("Death of the Price Jackal").items(List.of(
      item("Available"), item("Available"), item("Aged to lost"), item("Unknown")));

    instances[1].title("Wild and Wicked").items(List.of(
      item("Missing"), item("Available"), item("Awaiting pickup"), item("In process")));

    instances[2].title("Sword of Gruko").items(List.of(
      item("In progress"), item("Checked out"), item("In progress"), item("In process"), item("In transit")));

    instances[3].title("The Blade in the Ice").items(List.of(
      item("Awaiting delivery"), item("Checked out"), item("Awaiting pickup"), item("Unavailable")));

    instances[4].title("The Raven Crystal").items(List.of(
      item("Unavailable"), item("Unknown"), item("Restricted"), item("On order")));

    return instances;
  }

  private static Item item(String status) {
    return new Item().id(randomId()).status(new ItemStatus().name(status));
  }
}
