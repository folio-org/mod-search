package org.folio.search.controller;

import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.support.base.ApiEndpoints.searchInstancesByQuery;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.stream.Collectors;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.SearchResult;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

@IntegrationTest
class EsInstanceToInventoryInstanceIT extends BaseIntegrationTest {
  @Test
  void responseContainsOnlyBasicInstanceProperties() throws Exception {
    final var expected = getSemanticWeb();
    final var actualJson = mockMvc.perform(get(searchInstancesByQuery("id=={value}"), expected.getId())
      .headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(1)))
      // make sure that no unexpected properties are present
      .andExpect(jsonPath("instances[0].length()", is(4)))
      .andReturn().getResponse().getContentAsString();

    final var actual = JsonPath.parse(actualJson).read("instances[0]", Instance.class);

    assertThat(actual.getId(), is(expected.getId()));
    assertThat(actual.getTitle(), is(expected.getTitle()));
    assertThat(actual.getContributors(), is(expected.getContributors()));
    assertThat(actual.getPublication(), is(expected.getPublication()));
  }

  @Test
  void responseContainsAllInstanceProperties() throws Exception {
    final var expected = getSemanticWeb();
    final var actualJson = mockMvc.perform(get(searchInstancesByQuery("id=={value}&expandAll=true"), expected.getId())
      .headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(1)))
      .andReturn().getResponse().getContentAsString();

    final var actual = OBJECT_MAPPER.readValue(actualJson, SearchResult.class).getInstances().get(0);
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
