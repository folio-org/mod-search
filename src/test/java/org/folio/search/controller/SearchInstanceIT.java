package org.folio.search.controller;

import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.support.base.ApiEndpoints.searchInstancesByQuery;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.folio.search.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

public class SearchInstanceIT extends BaseIntegrationTest {
  @Test
  void canSearchByInstanceId_exactMatch() throws Exception {
    mockMvc.perform(get(searchInstancesByQuery("id=={value}"), getSemanticWeb().getId())
      .headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWeb().getId())));
  }

  @Test
  void canSearchByInstanceId_wildcard() throws Exception {
    mockMvc.perform(get(searchInstancesByQuery("id=={value}"), "5bf370e0*a0a39")
      .headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWeb().getId())));
  }
}
