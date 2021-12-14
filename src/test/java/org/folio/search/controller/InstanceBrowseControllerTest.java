package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.folio.search.support.base.ApiEndpoints.instanceCallNumberBrowsePath;
import static org.folio.search.utils.SearchUtils.X_OKAPI_TENANT_HEADER;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.callNumberBrowseRequest;
import static org.folio.search.utils.TestUtils.searchResult;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.folio.search.service.CallNumberBrowseService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@WebMvcTest(InstanceBrowseController.class)
@Import({ApiExceptionHandler.class})
class InstanceBrowseControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private CallNumberBrowseService callNumberBrowseService;

  @Test
  void findRelatedInstances_positive() throws Exception {
    var shelvingOrder = "callNumber = PR4034 .P7 2019";
    var request = callNumberBrowseRequest(shelvingOrder, 5);
    when(callNumberBrowseService.browseByCallNumber(request)).thenReturn(searchResult());
    var requestBuilder = get(instanceCallNumberBrowsePath())
      .queryParam("query", shelvingOrder)
      .queryParam("limit", "5")
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(0)))
      .andExpect(jsonPath("$.items", is(emptyList())));
  }

  @Test
  void findRelatedInstances_missingQueryParameter() throws Exception {
    var requestBuilder = get(instanceCallNumberBrowsePath())
      .queryParam("limit", "5")
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message",
        is("Required String parameter 'query' is not present")))
      .andExpect(jsonPath("$.errors[0].type", is("MissingServletRequestParameterException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }
}
