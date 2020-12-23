package org.folio.search.controller;

import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.folio.search.model.rest.request.SearchRequestBody;
import org.folio.search.model.rest.response.SearchResult;
import org.folio.search.service.SearchService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@WebMvcTest(SearchController.class)
@ExtendWith(SpringExtension.class)
class SearchControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private SearchService searchService;

  @Test
  void search_positive() throws Exception {
    when(searchService.search(any(), any())).thenReturn(new SearchResult());

    var requestBuilder = post("/search/query")
        .content(asJsonString(SearchRequestBody.of("search", "text")))
        .contentType(APPLICATION_JSON)
        .header("tenant-id", TENANT_ID);

    mockMvc.perform(requestBuilder)
        .andExpect(status().isOk());
  }
}