package org.folio.search.controller;

import static org.folio.search.utils.SearchResponseHelper.getSuccessFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.X_OKAPI_TENANT_HEADER;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.eventBody;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.index.Index;
import org.folio.search.domain.dto.IndexRequestBody;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.service.IndexService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@WebMvcTest(IndexController.class)
@Import(ApiExceptionHandler.class)
class IndexControllerTest {

  private static final String RESOURCE_NAME = "test-resource";
  private static final String TENANT_ID = "test-tenant";
  public static final String INDEX_NAME = RESOURCE_NAME + "_" + TENANT_ID;

  @Autowired private MockMvc mockMvc;
  @MockBean private IndexService indexService;

  @Test
  void createIndex_positive() throws Exception {
    var requestBuilder = post("/search/index/indices")
      .content(asJsonString(requestBody()))
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    when(indexService.createIndex(RESOURCE_NAME, TENANT_ID))
      .thenReturn(getSuccessFolioCreateIndexResponse(List.of(INDEX_NAME)));

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("success")))
      .andExpect(jsonPath("$.indices", is(List.of(INDEX_NAME))));
  }

  @Test
  void createIndex_negative_indexAlreadyExists() throws Exception {
    var requestBuilder = post("/search/index/indices")
      .content(asJsonString(requestBody()))
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    var elasticsearchException = new ElasticsearchException("Elasticsearch exception "
      + "[type=resource_already_exists_exception, "
      + "reason=index [instance_test-tenant/um_SBtCaRLKUOBbdmFZeKQ] already exists]");
    elasticsearchException.setIndex(new Index(INDEX_NAME, randomId()));

    when(indexService.createIndex(RESOURCE_NAME, TENANT_ID)).thenThrow(
      new SearchOperationException("error", elasticsearchException));

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message", is("Index already exists: " + INDEX_NAME)))
      .andExpect(jsonPath("$.errors[0].type", is("ElasticsearchException")))
      .andExpect(jsonPath("$.errors[0].code", is("Elasticsearch error")))
      .andExpect(jsonPath("$.total_records", is(1)));
  }

  @Test
  void createIndex_negative_unknownElasticsearchError() throws Exception {
    var requestBuilder = post("/search/index/indices")
      .content(asJsonString(requestBody()))
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    var errorMessage = "Elasticsearch exception [type=unknown_error, reason=mappings not found]";
    var elasticsearchException = new ElasticsearchException(errorMessage);

    when(indexService.createIndex(RESOURCE_NAME, TENANT_ID)).thenThrow(
      new SearchOperationException("error", elasticsearchException));

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message", is(errorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("ElasticsearchException")))
      .andExpect(jsonPath("$.errors[0].code", is("Elasticsearch error")))
      .andExpect(jsonPath("$.total_records", is(1)));
  }

  @Test
  void createIndex_negative_resourceNameIsNotPassed() throws Exception {
    var requestBuilder = post("/search/index/indices")
      .content(asJsonString(new IndexRequestBody()))
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message", is("must not be null")))
      .andExpect(jsonPath("$.errors[0].type", is("MethodArgumentNotValidException")))
      .andExpect(jsonPath("$.errors[0].code", is("Validation error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("resourceName")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("null")))
      .andExpect(jsonPath("$.total_records", is(1)));
  }

  @Test
  void createIndex_negative_nullPointerException() throws Exception {
    var requestBuilder = post("/search/index/indices")
      .content(asJsonString(requestBody()))
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    when(indexService.createIndex(RESOURCE_NAME, TENANT_ID)).thenThrow(new NullPointerException());

    mockMvc.perform(requestBuilder)
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.errors[0].type", is("NullPointerException")))
      .andExpect(jsonPath("$.errors[0].code", is("Unknown error")))
      .andExpect(jsonPath("$.total_records", is(1)));
  }

  @Test
  void updateMappings_positive() throws Exception {
    var requestBuilder = post("/search/index/mappings")
      .content(asJsonString(requestBody()))
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    when(indexService.updateMappings(RESOURCE_NAME, TENANT_ID))
      .thenReturn(getSuccessIndexOperationResponse());

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("success")));
  }

  @Test
  void indexResources_positive() throws Exception {
    var instanceData = OBJECT_MAPPER.createObjectNode();
    instanceData.put("id", randomId());
    var resourceBody = eventBody(RESOURCE_NAME, mapOf("id", randomId()));

    var requestBuilder = post("/search/index/records")
      .content(asJsonString(resourceBody))
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    when(indexService.indexResources(List.of(resourceBody)))
      .thenReturn(getSuccessIndexOperationResponse());

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("success")));
  }

  private static IndexRequestBody requestBody() {
    var indexRequestBody = new IndexRequestBody();
    indexRequestBody.setResourceName(RESOURCE_NAME);
    return indexRequestBody;
  }
}
