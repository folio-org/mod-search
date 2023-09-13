package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CN_INTERMEDIATE_VALUES;
import static org.folio.search.support.base.ApiEndpoints.instanceCallNumberBrowsePath;
import static org.folio.search.utils.TestUtils.cnBrowseItem;
import static org.folio.search.utils.TestUtils.getShelfKeyFromCallNumber;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.folio.search.utils.TestUtils.randomId;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.CallNumberBrowseResult;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.CallNumberUtils;
import org.folio.spring.test.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class BrowseTypedCallNumberWithoutExpandAllIT extends BaseIntegrationTest {

  private static final Instance[] INSTANCES = instances();
  private static final Map<String, Instance> INSTANCE_MAP =
    Arrays.stream(INSTANCES).collect(toMap(Instance::getTitle, identity()));

  @BeforeAll
  static void prepare() {
    setUpTenant(INSTANCES);
    enableFeature(BROWSE_CN_INTERMEDIATE_VALUES);
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void browseByCallNumber_browsingAroundWithoutExpandAll() {
    var request = get(instanceCallNumberBrowsePath())
      .param("highlightMatch", "true")
      .param("limit", "100")
      .param("precedingRecordsCount", "5")
      .param("callNumberType", "dewey")
      .param("query", prepareQuery("typedCallNumber>={value} or typedCallNumber<{value}", "308 H970"));
    var result = parseResponse(doGet(request), CallNumberBrowseResult.class);
    final CallNumberBrowseResult expected = new CallNumberBrowseResult()
      .totalRecords(2)
      .items(
        List.of(
          cnBrowseItem(INSTANCE_MAP.get("instance #10"), "308 H970", true)
        )
      );
    expected.setItems(CallNumberUtils.excludeIrrelevantResultItems("dewey", expected.getItems()));

    assertThat(result).isEqualTo(expected);
  }

  private static Instance[] instances() {
    List<List<String>> basicPropsInstanceData = callNumberBrowseInstanceData();
    Map<String, List<List<String>>> titleMap = basicPropsInstanceData
      .stream()
      .collect(groupingBy(d -> d.get(2)));
    List<Instance> basicPropsInstanceList = titleMap.keySet()
      .stream()
      .map(k -> instanceWithOnlyBasicProperties(titleMap.get(k), k))
      .toList();

    List<Instance> instances = new ArrayList<>(basicPropsInstanceList);

    var instanceArray = new Instance[instances.size()];
    instances.toArray(instanceArray);

    return instanceArray;
  }

  private static Instance instanceWithOnlyBasicProperties(List<List<String>> data, String title) {
    var items = data.stream().map(d -> new Item()
        .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents()
          .callNumber(d.get(1))
          .typeId(d.get(0)))
        .effectiveShelvingOrder(getShelfKeyFromCallNumber(d.get(1))))
      .toList();

    return new Instance()
      .id(randomId())
      .title(title)
      .staffSuppress(false)
      .discoverySuppress(false)
      .isBoundWith(false)
      .items(items)
      .holdings(emptyList());
  }

  private static List<List<String>> callNumberBrowseInstanceData() {
    return List.of(
      List.of("95467209-6d7b-468b-94df-0f5d7ad2747d", "Z669.R360 1977", "instance #10"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H970", "instance #10")
    );
  }
}
