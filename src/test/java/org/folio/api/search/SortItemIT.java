package org.folio.api.search;

import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemStatus;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
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
  private static final String[] HOLDINGS_IDS = array(
    "7ac8e203-defb-4bf9-9f87-4ffd67823ad8",
    "8a3f2f3a-ecf4-49fd-b58f-5129ae5a128d",
    "1650bee3-1e90-4815-ac12-31d1a12bd7c2",
    "0646ce7c-efcd-4449-ba29-f5aba3ec7690",
    "bafb734e-88f4-4fbe-bd78-119630d225bb");

  @BeforeAll
  static void prepare() {
    var holdingsMatcher = jsonPath("sum($.instances..holdings.length())", is(5.0));
    var itemsMatcher = jsonPath("sum($.instances..items.length())", is(21.0));
    setUpTenant(List.of(holdingsMatcher, itemsMatcher), instances());
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

    instances[0].title("Death of the Price Jackal").holdings(List.of(new Holding().id(HOLDINGS_IDS[0]))).items(List.of(
      item("Available", HOLDINGS_IDS[0]), item("Available", HOLDINGS_IDS[0]),
      item("Aged to lost", HOLDINGS_IDS[0]), item("Unknown", HOLDINGS_IDS[0])));

    instances[1].title("Wild and Wicked").holdings(List.of(new Holding().id(HOLDINGS_IDS[1]))).items(List.of(
      item("Missing", HOLDINGS_IDS[1]), item("Available", HOLDINGS_IDS[1]),
      item("Awaiting pickup", HOLDINGS_IDS[1]), item("In process", HOLDINGS_IDS[1])));

    instances[2].title("Sword of Gruko").holdings(List.of(new Holding().id(HOLDINGS_IDS[2]))).items(List.of(
      item("In progress", HOLDINGS_IDS[2]), item("Checked out", HOLDINGS_IDS[2]),
      item("In progress", HOLDINGS_IDS[2]), item("In process", HOLDINGS_IDS[2]),
      item("In transit", HOLDINGS_IDS[2])));

    instances[3].title("The Blade in the Ice").holdings(List.of(new Holding().id(HOLDINGS_IDS[3]))).items(List.of(
      item("Awaiting delivery", HOLDINGS_IDS[3]), item("Checked out", HOLDINGS_IDS[3]),
      item("Awaiting pickup", HOLDINGS_IDS[3]), item("Unavailable", HOLDINGS_IDS[3])));

    instances[4].title("The Raven Crystal").holdings(List.of(new Holding().id(HOLDINGS_IDS[4]))).items(List.of(
      item("Unavailable", HOLDINGS_IDS[4]), item("Unknown", HOLDINGS_IDS[4]),
      item("Restricted", HOLDINGS_IDS[4]), item("On order", HOLDINGS_IDS[4])));

    return instances;
  }

  private static Item item(String status, String holdingId) {
    return new Item().id(randomId()).status(new ItemStatus().name(status)).holdingsRecordId(holdingId);
  }
}
