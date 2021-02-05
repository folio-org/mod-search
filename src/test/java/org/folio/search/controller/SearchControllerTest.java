package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.folio.search.controller.IndexControllerTest.INDEX_NAME;
import static org.folio.search.utils.SearchUtils.X_OKAPI_TENANT_HEADER;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.index.Index;
import org.folio.search.domain.dto.SearchResult;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.service.SearchService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@WebMvcTest(SearchController.class)
@Import(ApiExceptionHandler.class)
class SearchControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private SearchService searchService;

  @Test
  void search_positive() throws Exception {
    var expectedSearchResult = new SearchResult().totalRecords(0).instances(emptyList());

    var cqlQuery = "title all \"test-query\"";
    var expectedSearchRequest = CqlSearchRequest.of("instance", cqlQuery, TENANT_ID, 100, 0, false);

    when(searchService.search(expectedSearchRequest)).thenReturn(expectedSearchResult);

    var requestBuilder = get("/search/instances")
      .queryParam("query", cqlQuery)
      .queryParam("limit", "100")
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(0)))
      .andExpect(jsonPath("$.instances", is(emptyList())));
  }

  @Test
  void search_negative_indexNotFound() throws Exception {
    var cqlQuery = "title all \"test-query\"";
    var expectedSearchRequest = CqlSearchRequest.of("instance", cqlQuery, TENANT_ID, 100, 0);
    var elasticsearchException = new ElasticsearchException("Elasticsearch exception ["
      + "type=index_not_found_exception, "
      + "reason=no such index [instance_test-tenant]]");
    elasticsearchException.setIndex(new Index(INDEX_NAME, randomId()));

    when(searchService.search(expectedSearchRequest)).thenThrow(
      new SearchOperationException("error", elasticsearchException));

    var requestBuilder = get("/search/instances")
      .queryParam("query", cqlQuery)
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Index not found: " + INDEX_NAME)))
      .andExpect(jsonPath("$.errors[0].type", is("ElasticsearchException")))
      .andExpect(jsonPath("$.errors[0].code", is("elasticsearch_error")));
  }

  @Test
  void search_negative_invalidLimitParameter() throws Exception {
    var requestBuilder = get("/search/instances")
      .queryParam("query", "title all \"test-query\"")
      .queryParam("limit", "100000")
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("must be less than or equal to 500")))
      .andExpect(jsonPath("$.errors[0].type", is("ConstraintViolationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void search_negative_invalidCqlQuery() throws Exception {
    var cqlQuery = "title all \"test-query\" and";
    var expectedSearchRequest = CqlSearchRequest.of("instance", cqlQuery, TENANT_ID, 100, 0);
    var exceptionMessage = String.format("Failed to parse CQL query [query: '%s']", cqlQuery);
    when(searchService.search(expectedSearchRequest)).thenThrow(new SearchServiceException(exceptionMessage));

    var requestBuilder = get("/search/instances")
      .queryParam("query", cqlQuery)
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(exceptionMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("SearchServiceException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")));
  }
}
