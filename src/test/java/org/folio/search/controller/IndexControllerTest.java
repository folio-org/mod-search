package org.folio.search.controller;

import static org.folio.search.support.base.ApiEndpoints.createIndicesEndpoint;
import static org.folio.search.utils.SearchResponseHelper.getSuccessFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.folio.search.domain.dto.CreateIndexRequest;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.domain.dto.ReindexJob;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.domain.dto.UpdateIndexSettingsRequest;
import org.folio.search.domain.dto.UpdateMappingsRequest;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.service.IndexService;
import org.folio.search.service.ResourceService;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.test.type.UnitTest;
import org.hibernate.validator.internal.engine.ConstraintViolationImpl;
import org.hibernate.validator.internal.engine.path.PathImpl;
import org.junit.jupiter.api.Test;
import org.opensearch.OpenSearchException;
import org.opensearch.index.Index;
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

  @Autowired
  private MockMvc mockMvc;
  @MockBean
  private IndexService indexService;
  @MockBean
  private ResourceService resourceService;

  @Test
  void createIndex_positive() throws Exception {
    when(indexService.createIndex(RESOURCE_NAME, TENANT_ID))
      .thenReturn(getSuccessFolioCreateIndexResponse(List.of(INDEX_NAME)));

    mockMvc.perform(preparePostRequest(createIndicesEndpoint(), asJsonString(createIndexRequest())))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("success")))
      .andExpect(jsonPath("$.indices", is(List.of(INDEX_NAME))));
  }

  @Test
  void createIndex_negative_indexAlreadyExists() throws Exception {
    var openSearchException = new OpenSearchException("Elasticsearch exception "
      + "[type=resource_already_exists_exception, "
      + "reason=index [instance_test-tenant/um_SBtCaRLKUOBbdmFZeKQ] already exists]");
    openSearchException.setIndex(new Index(INDEX_NAME, randomId()));

    when(indexService.createIndex(RESOURCE_NAME, TENANT_ID)).thenThrow(
      new SearchOperationException("error", openSearchException));

    mockMvc.perform(preparePostRequest(createIndicesEndpoint(), asJsonString(createIndexRequest())))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Index already exists: " + INDEX_NAME)))
      .andExpect(jsonPath("$.errors[0].type", is("OpenSearchException")))
      .andExpect(jsonPath("$.errors[0].code", is("elasticsearch_error")));
  }

  @Test
  void createIndex_negative_unknownElasticsearchError() throws Exception {
    var errorMessage = "Elasticsearch exception [type=unknown_error, reason=mappings not found]";
    var openSearchException = new OpenSearchException(errorMessage);

    when(indexService.createIndex(RESOURCE_NAME, TENANT_ID)).thenThrow(
      new SearchOperationException("i/o error", openSearchException));

    mockMvc.perform(preparePostRequest(createIndicesEndpoint(), asJsonString(createIndexRequest())))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(errorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("OpenSearchException")))
      .andExpect(jsonPath("$.errors[0].code", is("elasticsearch_error")));
  }

  @Test
  void createIndex_negative_unknownErrorInSearchOperationException() throws Exception {
    var errorMessage = "i/o error";
    when(indexService.createIndex(RESOURCE_NAME, TENANT_ID)).thenThrow(
      new SearchOperationException(errorMessage, new IOException(errorMessage)));

    mockMvc.perform(preparePostRequest(createIndicesEndpoint(), asJsonString(createIndexRequest())))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(errorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("SearchOperationException")))
      .andExpect(jsonPath("$.errors[0].code", is("elasticsearch_error")));
  }

  @Test
  void createIndex_negative_resourceNameIsNotPassed() throws Exception {
    mockMvc.perform(preparePostRequest(createIndicesEndpoint(), asJsonString(new CreateIndexRequest())))
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
    mockMvc.perform(preparePostRequest(createIndicesEndpoint(), asJsonString(createIndexRequest())))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].type", is("NullPointerException")))
      .andExpect(jsonPath("$.errors[0].code", is("unknown_error")));
  }

  @Test
  void updateMappings_positive() throws Exception {
    when(indexService.updateMappings(RESOURCE_NAME, TENANT_ID)).thenReturn(getSuccessIndexOperationResponse());
    mockMvc.perform(preparePostRequest("/search/index/mappings", asJsonString(updateMappingsRequest())))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("success")));
  }

  @Test
  void indexResources_positive() throws Exception {
    var instanceData = OBJECT_MAPPER.createObjectNode();
    instanceData.put("id", randomId());
    var resourceBody = resourceEvent(RESOURCE_NAME, mapOf("id", randomId()));

    when(resourceService.indexResources(List.of(resourceBody))).thenReturn(getSuccessIndexOperationResponse());

    mockMvc.perform(preparePostRequest("/search/index/records", asJsonString(resourceBody)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("success")));
  }

  @Test
  void updateIndexSettings_positive() throws Exception {
    when(indexService.updateIndexSettings(RESOURCE_NAME, TENANT_ID, createIndexSettings()))
      .thenReturn(getSuccessIndexOperationResponse());
    mockMvc.perform(preparePutRequest("/search/index/settings", asJsonString(updateIndexSettingsRequest())))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("success")));
  }

  @Test
  void canSubmitReindex() throws Exception {
    var jobId = randomId();
    when(indexService.reindexInventory(TENANT_ID, null)).thenReturn(new ReindexJob().id(jobId));

    mockMvc.perform(post("/search/index/inventory/reindex")
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("id", is(jobId)));
  }

  @Test
  void reindexInventoryRecords_positive_withRecreateIndexFlag() throws Exception {
    var jobId = randomId();
    var request = new ReindexRequest().recreateIndex(true);
    when(indexService.reindexInventory(TENANT_ID, request)).thenReturn(new ReindexJob().id(jobId));

    mockMvc.perform(post("/search/index/inventory/reindex")
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .contentType(APPLICATION_JSON)
        .content(asJsonString(request))
        .param("recreateIndex", "true"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("id", is(jobId)));
  }

  @Test
  void reindexInventoryRecords_negative_emptyContentTypeAndBody() throws Exception {
    var jobId = randomId();
    var request = new ReindexRequest().recreateIndex(true);
    when(indexService.reindexInventory(TENANT_ID, request)).thenReturn(new ReindexJob().id(jobId));

    mockMvc.perform(post("/search/index/inventory/reindex")
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .param("recreateIndex", "true"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Content-Type is not supported")))
      .andExpect(jsonPath("$.errors[0].type", is("HttpMediaTypeNotSupportedException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void reindexInventoryRecords_negative_constraintViolationException() throws Exception {
    var constraintViolation = mock(ConstraintViolationImpl.class);
    when(constraintViolation.getPropertyPath()).thenReturn(PathImpl.createPathFromString("recreateIndices"));
    when(constraintViolation.getMessage()).thenReturn("must be boolean");
    when(indexService.reindexInventory(TENANT_ID, null)).thenThrow(
      new ConstraintViolationException("error", Set.<ConstraintViolation<?>>of(constraintViolation)));

    mockMvc.perform(post("/search/index/inventory/reindex")
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("recreateIndices must be boolean")))
      .andExpect(jsonPath("$.errors[0].type", is("ConstraintViolationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void reindexInventoryRecords_negative_illegalArgumentException() throws Exception {
    when(indexService.reindexInventory(TENANT_ID, null)).thenThrow(new IllegalArgumentException("invalid value"));

    mockMvc.perform(post("/search/index/inventory/reindex")
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("invalid value")))
      .andExpect(jsonPath("$.errors[0].type", is("IllegalArgumentException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  private static MockHttpServletRequestBuilder preparePostRequest(String endpoint, String requestBody) {
    return post(endpoint)
      .content(requestBody)
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);
  }

  private static MockHttpServletRequestBuilder preparePutRequest(String endpoint, String requestBody) {
    return put(endpoint)
      .content(requestBody)
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);
  }

  private static CreateIndexRequest createIndexRequest() {
    return new CreateIndexRequest().resourceName(RESOURCE_NAME);
  }

  private static UpdateMappingsRequest updateMappingsRequest() {
    return new UpdateMappingsRequest().resourceName(RESOURCE_NAME);
  }

  private static UpdateIndexSettingsRequest updateIndexSettingsRequest() {
    return new UpdateIndexSettingsRequest().resourceName(RESOURCE_NAME).indexSettings(createIndexSettings());
  }

  private static IndexSettings createIndexSettings() {
    return new IndexSettings().numberOfReplicas(2).numberOfShards(1).refreshInterval(1);
  }
}
