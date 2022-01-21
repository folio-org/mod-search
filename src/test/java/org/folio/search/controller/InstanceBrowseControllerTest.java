package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.folio.search.support.base.ApiEndpoints.instanceCallNumberBrowsePath;
import static org.folio.search.support.base.ApiEndpoints.instanceSubjectBrowsePath;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;
import static org.folio.search.utils.SearchUtils.X_OKAPI_TENANT_HEADER;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.browseRequest;
import static org.folio.search.utils.TestUtils.searchResult;
import static org.folio.search.utils.TestUtils.subjectBrowseItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.folio.search.model.service.BrowseRequest;
import org.folio.search.service.CallNumberBrowseService;
import org.folio.search.service.SubjectBrowseService;
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
  @MockBean private SubjectBrowseService subjectBrowseService;

  @Test
  void browseInstancesByCallNumber_positive() throws Exception {
    var query = "callNumber > PR4034 .P7 2019";
    var request = browseRequest(query, 5);
    when(callNumberBrowseService.browse(request)).thenReturn(searchResult());
    var requestBuilder = get(instanceCallNumberBrowsePath())
      .queryParam("query", query)
      .queryParam("limit", "5")
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(0)))
      .andExpect(jsonPath("$.items", is(emptyList())));
  }

  @Test
  void browseInstancesByCallNumber_positive_allFields() throws Exception {
    var query = "callNumber > B";
    var request = BrowseRequest.of(INSTANCE_RESOURCE, TENANT_ID, query, 20, "callNumber", true, true, 5);
    when(callNumberBrowseService.browse(request)).thenReturn(searchResult());

    var requestBuilder = get(instanceCallNumberBrowsePath())
      .queryParam("query", query)
      .queryParam("limit", "20")
      .queryParam("expandAll", "true")
      .queryParam("highlightMatch", "true")
      .queryParam("precedingRecordsCount", "5")
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(0)))
      .andExpect(jsonPath("$.items", is(emptyList())));
  }

  @Test
  void browseInstancesBySubject_positive() throws Exception {
    var query = "subject > water";
    var request = BrowseRequest.of(INSTANCE_SUBJECT_RESOURCE, TENANT_ID, query, 25, "subject", null, true, 12);
    when(subjectBrowseService.browse(request)).thenReturn(searchResult(subjectBrowseItem(10, "water treatment")));
    var requestBuilder = get(instanceSubjectBrowsePath())
      .queryParam("query", query)
      .queryParam("limit", "25")
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.items[0].subject", is("water treatment")))
      .andExpect(jsonPath("$.items[0].totalRecords", is(10)));
  }

  @Test
  void browseInstancesByCallNumber_negative_missingQueryParameter() throws Exception {
    var requestBuilder = get(instanceCallNumberBrowsePath())
      .queryParam("limit", "5")
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Required String parameter 'query' is not present")))
      .andExpect(jsonPath("$.errors[0].type", is("MissingServletRequestParameterException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void browseInstancesByCallNumber_negative_precedingRecordsMoreThatLimit() throws Exception {
    var requestBuilder = get(instanceCallNumberBrowsePath())
      .queryParam("query", "callNumber > A")
      .queryParam("limit", "5")
      .queryParam("precedingRecordsCount", "10")
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Preceding records count must be less than request limit")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("precedingRecordsCount")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("10")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }
}
