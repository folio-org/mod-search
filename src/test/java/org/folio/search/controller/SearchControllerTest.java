package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.search.domain.dto.SearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.service.SearchService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@WebMvcTest(SearchController.class)
class SearchControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private SearchService searchService;

  @Test
  void search_positive() throws Exception {
    var expectedSearchResult = new SearchResult();
    expectedSearchResult.setTotalRecords(0);
    expectedSearchResult.setInstances(emptyList());

    var cqlQuery = "title all \"test-query\"";
    var expectedSearchRequest = CqlSearchRequest.of("instance", cqlQuery, TENANT_ID, 100, 0);

    when(searchService.search(expectedSearchRequest)).thenReturn(expectedSearchResult);

    var requestBuilder = get("/search/instances")
      .queryParam("resource", RESOURCE_NAME)
      .queryParam("query", cqlQuery)
      .queryParam("limit", "100")
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(0)))
      .andExpect(jsonPath("$.instances", is(emptyList())));
  }
}
