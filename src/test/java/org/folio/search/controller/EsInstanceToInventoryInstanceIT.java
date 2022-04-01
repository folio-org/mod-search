package org.folio.search.controller;

import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.sample.SampleInstances.getSemanticWebAsMap;
import static org.folio.search.sample.SampleInstances.getSemanticWebId;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.stream.Collectors;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.model.service.ResultList;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@IntegrationTest
class EsInstanceToInventoryInstanceIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(Instance.class, getSemanticWebAsMap());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  @Disabled
  void responseContainsOnlyBasicInstanceProperties() throws Exception {
    var expected = getSemanticWeb();
    var response = doSearchByInstances(prepareQuery("id=={value}", getSemanticWebId()))
      .andExpect(jsonPath("totalRecords", is(1)))
      // make sure that no unexpected properties are present
      .andExpect(jsonPath("instances[0].length()", is(8)));

    var actual = parseResponse(response, new TypeReference<ResultList<Instance>>() {}).getResult().get(0);
    assertThat(actual.getId(), is(expected.getId()));
    assertThat(actual.getTitle(), is(expected.getTitle()));
    assertThat(actual.getContributors(), is(expected.getContributors()));
    assertThat(actual.getStaffSuppress(), is(false));
    assertThat(actual.getIsBoundWith(), is(true));
    assertThat(actual.getDiscoverySuppress(), is(expected.getDiscoverySuppress()));
    assertThat(actual.getPublication(), is(expected.getPublication()));
    assertThat(actual.getItems(), is(List.of(
      new Item()
        .effectiveCallNumberComponents(callNumber("prefix-10101", "suffix-10101"))
        .effectiveShelvingOrder("TK 45105.88815 A58 42004 FT MEADE"),
      new Item()
        .effectiveCallNumberComponents(callNumber("prefix-90000", "suffix-90000"))
        .effectiveShelvingOrder("TK 45105.88815 A58 42004 FT MEADE")
    )));
  }

  @Test
  @Disabled
  void responseContainsAllInstanceProperties() throws Exception {
    var expected = getSemanticWeb();
    var response = doSearchByInstances(prepareQuery("id=={value}", getSemanticWebId()), true)
      .andExpect(jsonPath("totalRecords", is(1)));

    var actual = parseResponse(response, new TypeReference<ResultList<Instance>>() {}).getResult().get(0);

    assertThat(actual.getHoldings(), containsInAnyOrder(expected.getHoldings().stream()
      .map(hr -> hr.discoverySuppress(false))
      .map(EsInstanceToInventoryInstanceIT::removeUnexpectedProperties)
      .map(Matchers::is).collect(Collectors.toList())));

    assertThat(actual.getItems(), containsInAnyOrder(expected.getItems().stream()
      .map(EsInstanceToInventoryInstanceIT::removeUnexpectedProperties)
      .map(Matchers::is).collect(Collectors.toList())));

    assertThat(actual.holdings(null).items(null),
      is(removeUnexpectedProperties(expected)));
  }

  private static Holding removeUnexpectedProperties(Holding holding) {
    holding.getElectronicAccess().forEach(e -> e.setMaterialsSpecification(null));
    return holding.callNumberSuffix(null).callNumber(null).callNumberPrefix(null);
  }

  private static Item removeUnexpectedProperties(Item item) {
    item.getElectronicAccess().forEach(e -> e.setMaterialsSpecification(null));
    return item.discoverySuppress(false);
  }

  private static Instance removeUnexpectedProperties(Instance instance) {
    instance.getElectronicAccess().forEach(e -> e.setMaterialsSpecification(null));
    return instance.staffSuppress(false).discoverySuppress(false).items(null).holdings(null);
  }

  private static ItemEffectiveCallNumberComponents callNumber(String prefix, String suffix) {
    return new ItemEffectiveCallNumberComponents().prefix(prefix).suffix(suffix)
      .callNumber("TK5105.88815 . A58 2004 FT MEADE");
  }
}
