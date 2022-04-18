package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.folio.search.support.base.ApiEndpoints.authoritySearchPath;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.searchResult;
import static org.folio.search.utils.TestUtils.searchServiceRequest;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.folio.search.converter.StreamIdsJobMapper;
import org.folio.search.domain.dto.Authority;
import org.folio.search.service.ResourceIdsStreamHelper;
import org.folio.search.service.SearchService;
import org.folio.search.service.StreamIdsJobService;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.integration.XOkapiHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@Import({ApiExceptionHandler.class})
@WebMvcTest(AuthorityController.class)
class AuthorityControllerTest {

  @MockBean private SearchService searchService;
  @MockBean private ResourceIdsStreamHelper resourceIdsStreamHelper;
  @MockBean private StreamIdsJobService streamIdsJobService;
  @MockBean private StreamIdsJobMapper streamIdsJobMapper;
  @Autowired private MockMvc mockMvc;

  @Test
  void search_positive() throws Exception {
    var cqlQuery = "cql.allRecords=1";
    var expectedSearchRequest = searchServiceRequest(Authority.class, cqlQuery);

    when(searchService.search(expectedSearchRequest)).thenReturn(searchResult());

    var requestBuilder = get(authoritySearchPath())
      .queryParam("query", cqlQuery)
      .queryParam("limit", "100")
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(0)))
      .andExpect(jsonPath("$.authorities", is(emptyList())));
  }
}
