package org.folio.search.controller;

import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.support.base.ApiEndpoints.searchInstancesByQuery;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.stream.Collectors;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.model.service.ResultList;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

@IntegrationTest
class EsInstanceToInventoryInstanceIT extends BaseIntegrationTest {
  @Test
  void responseContainsOnlyBasicInstanceProperties() throws Exception {
    var expected = getSemanticWeb();
    var response = doGet(searchInstancesByQuery("id=={value}"), expected.getId())
      .andExpect(jsonPath("totalRecords", is(1)))
      // make sure that no unexpected properties are present
      .andExpect(jsonPath("instances[0].length()", is(4)));

    var actual = parseResponse(response, new TypeReference<ResultList<Instance>>() {}).getResult().get(0);
    assertThat(actual.getId(), is(expected.getId()));
    assertThat(actual.getTitle(), is(expected.getTitle()));
    assertThat(actual.getContributors(), is(expected.getContributors()));
    assertThat(actual.getPublication(), is(expected.getPublication()));
  }

  @Test
  void responseContainsAllInstanceProperties() throws Exception {
    var expected = getSemanticWeb();
    var response = doGet(searchInstancesByQuery("id=={value}&expandAll=true"), expected.getId())
      .andExpect(jsonPath("totalRecords", is(1)));

    var actual = parseResponse(response, new TypeReference<ResultList<Instance>>() {}).getResult().get(0);
    assertThat(actual.getHoldings(), containsInAnyOrder(expected.getHoldings().stream()
      .map(hr -> hr.discoverySuppress(false))
      .map(this::removeUnexpectedProperties)
      .map(Matchers::is).collect(Collectors.toList())));
    assertThat(actual.getItems(), containsInAnyOrder(expected.getItems().stream()
      .map(item -> item.discoverySuppress(false))
      .map(Matchers::is).collect(Collectors.toList())));

    assertThat(actual.holdings(null).items(null),
      is(expected.staffSuppress(false).discoverySuppress(false).items(null).holdings(null)));
  }

  private Holding removeUnexpectedProperties(Holding holding) {
    return holding.callNumberSuffix(null).callNumber(null).callNumberPrefix(null);
  }
}
