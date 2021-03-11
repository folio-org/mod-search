package org.folio.search.controller;

import static org.folio.search.support.base.ApiEndpoints.createIndicesEndpoint;
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

import java.io.IOException;
import java.util.List;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.index.Index;
import org.folio.search.domain.dto.IndexRequestBody;
import org.folio.search.domain.dto.ReindexJob;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.service.IndexService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
    when(indexService.createIndex(RESOURCE_NAME, TENANT_ID))
      .thenReturn(getSuccessFolioCreateIndexResponse(List.of(INDEX_NAME)));

    mockMvc.perform(preparePostRequest(createIndicesEndpoint(), asJsonString(requestBody())))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("success")))
      .andExpect(jsonPath("$.indices", is(List.of(INDEX_NAME))));
  }

  @Test
  void createIndex_negative_indexAlreadyExists() throws Exception {
    var elasticsearchException = new ElasticsearchException("Elasticsearch exception "
      + "[type=resource_already_exists_exception, "
      + "reason=index [instance_test-tenant/um_SBtCaRLKUOBbdmFZeKQ] already exists]");
    elasticsearchException.setIndex(new Index(INDEX_NAME, randomId()));

    when(indexService.createIndex(RESOURCE_NAME, TENANT_ID)).thenThrow(
      new SearchOperationException("error", elasticsearchException));

    mockMvc.perform(preparePostRequest(createIndicesEndpoint(), asJsonString(requestBody())))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Index already exists: " + INDEX_NAME)))
      .andExpect(jsonPath("$.errors[0].type", is("ElasticsearchException")))
      .andExpect(jsonPath("$.errors[0].code", is("elasticsearch_error")));
  }

  @Test
  void createIndex_negative_unknownElasticsearchError() throws Exception {
    var errorMessage = "Elasticsearch exception [type=unknown_error, reason=mappings not found]";
    var elasticsearchException = new ElasticsearchException(errorMessage);

    when(indexService.createIndex(RESOURCE_NAME, TENANT_ID)).thenThrow(
      new SearchOperationException("i/o error", elasticsearchException));

    mockMvc.perform(preparePostRequest(createIndicesEndpoint(), asJsonString(requestBody())))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(errorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("ElasticsearchException")))
      .andExpect(jsonPath("$.errors[0].code", is("elasticsearch_error")));
  }

  @Test
  void createIndex_negative_unknownErrorInSearchOperationException() throws Exception {
    var errorMessage = "i/o error";
    when(indexService.createIndex(RESOURCE_NAME, TENANT_ID)).thenThrow(
      new SearchOperationException(errorMessage, new IOException(errorMessage)));

    mockMvc.perform(preparePostRequest(createIndicesEndpoint(), asJsonString(requestBody())))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(errorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("SearchOperationException")))
      .andExpect(jsonPath("$.errors[0].code", is("elasticsearch_error")));
  }

  @Test
  void createIndex_negative_resourceNameIsNotPassed() throws Exception {
    mockMvc.perform(preparePostRequest(createIndicesEndpoint(), asJsonString(new IndexRequestBody())))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("must not be null")))
      .andExpect(jsonPath("$.errors[0].type", is("MethodArgumentNotValidException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("resourceName")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("null")));
  }

  @Test
  void createIndex_negative_nullPointerException() throws Exception {
    when(indexService.createIndex(RESOURCE_NAME, TENANT_ID)).thenThrow(new NullPointerException());
    mockMvc.perform(preparePostRequest(createIndicesEndpoint(), asJsonString(requestBody())))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].type", is("NullPointerException")))
      .andExpect(jsonPath("$.errors[0].code", is("unknown_error")));
  }

  @Test
  void updateMappings_positive() throws Exception {
    when(indexService.updateMappings(RESOURCE_NAME, TENANT_ID)).thenReturn(getSuccessIndexOperationResponse());
    mockMvc.perform(preparePostRequest("/search/index/mappings", asJsonString(requestBody())))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("success")));
  }

  @Test
  void indexResources_positive() throws Exception {
    var instanceData = OBJECT_MAPPER.createObjectNode();
    instanceData.put("id", randomId());
    var resourceBody = eventBody(RESOURCE_NAME, mapOf("id", randomId()));

    when(indexService.indexResources(List.of(resourceBody))).thenReturn(getSuccessIndexOperationResponse());

    mockMvc.perform(preparePostRequest("/search/index/records", asJsonString(resourceBody)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("success")));
  }

  @Test
  void canSubmitReindex() throws Exception {
    var jobId = randomId();
    when(indexService.reindexInventory()).thenReturn(new ReindexJob().id(jobId));

    mockMvc.perform(post("/search/index/inventory/reindex"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("id", is(jobId)));
  }

  private static MockHttpServletRequestBuilder preparePostRequest(String endpoint, String requestBody) {
    return post(endpoint)
      .content(requestBody)
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);
  }

  private static IndexRequestBody requestBody() {
    var indexRequestBody = new IndexRequestBody();
    indexRequestBody.setResourceName(RESOURCE_NAME);
    return indexRequestBody;
  }
}
