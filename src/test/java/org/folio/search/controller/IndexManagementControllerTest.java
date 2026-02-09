package org.folio.search.controller;

import static org.folio.search.utils.SearchResponseHelper.getSuccessFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.support.base.ApiEndpoints.createIndicesPath;
import static org.folio.support.base.ApiEndpoints.reindexFailedPath;
import static org.folio.support.base.ApiEndpoints.reindexFullPath;
import static org.folio.support.base.ApiEndpoints.reindexInstanceRecordsStatus;
import static org.folio.support.base.ApiEndpoints.reindexUploadPath;
import static org.folio.support.utils.JsonTestUtils.asJsonString;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.randomId;
import static org.folio.support.utils.TestUtils.resourceEvent;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.folio.search.domain.dto.CreateIndexRequest;
import org.folio.search.domain.dto.IndexDynamicSettings;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.domain.dto.ReindexJob;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.domain.dto.ReindexStatusItem;
import org.folio.search.domain.dto.ReindexUploadDto;
import org.folio.search.domain.dto.UpdateIndexDynamicSettingsRequest;
import org.folio.search.domain.dto.UpdateMappingsRequest;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.IndexService;
import org.folio.search.service.ResourceService;
import org.folio.search.service.reindex.ReindexService;
import org.folio.search.service.reindex.ReindexStatusService;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.type.UnitTest;
import org.folio.support.config.TestNoOpCacheConfig;
import org.hibernate.validator.internal.engine.ConstraintViolationImpl;
import org.hibernate.validator.internal.engine.path.PathImpl;
import org.junit.jupiter.api.Test;
import org.opensearch.OpenSearchException;
import org.opensearch.core.index.Index;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@UnitTest
@WebMvcTest(IndexManagementController.class)
@Import({ApiExceptionHandler.class, TestNoOpCacheConfig.class})
class IndexManagementControllerTest {

  private static final ResourceType RESOURCE = ResourceType.INSTANCE;
  private static final String TENANT_ID = "test-tenant";
  public static final String INDEX_NAME = RESOURCE.getName() + "_" + TENANT_ID;

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private IndexService indexService;
  @MockitoBean
  private ResourceService resourceService;
  @MockitoBean
  private ReindexService reindexService;
  @MockitoBean
  private ReindexStatusService reindexStatusService;

  @Test
  void submitReindexFull_positive() throws Exception {
    when(reindexService.submitFullReindex(TENANT_ID, null)).thenReturn(new CompletableFuture<>());

    mockMvc.perform(post(reindexFullPath())
        .contentType(APPLICATION_JSON).header(XOkapiHeaders.TENANT, TENANT_ID))
      .andExpect(status().isOk());
  }

  @Test
  void submitReindexFull_positive_withSettings() throws Exception {
    var requestBody = new IndexSettings().numberOfShards(1).refreshInterval(2).numberOfReplicas(3);
    when(reindexService.submitFullReindex(TENANT_ID, requestBody)).thenReturn(new CompletableFuture<>());

    mockMvc.perform(preparePostRequest(reindexFullPath(), asJsonString(requestBody)))
      .andExpect(status().isOk());
  }

  @Test
  void submitReindexUpload_positive() throws Exception {
    when(reindexService.submitUploadReindex(eq(TENANT_ID), anyList())).thenReturn(new CompletableFuture<>());
    var requestBody = new ReindexUploadDto().addEntityTypesItem(ReindexUploadDto.EntityTypesEnum.INSTANCE);

    mockMvc.perform(preparePostRequest(reindexUploadPath(), asJsonString(requestBody))
        .header(XOkapiHeaders.TENANT, TENANT_ID))
      .andExpect(status().isOk());
  }

  @Test
  void submitReindexUpload_positive_withSettings() throws Exception {
    var indexSettings = new IndexSettings().numberOfShards(1).refreshInterval(2).numberOfReplicas(3);
    var requestBody = new ReindexUploadDto()
      .addEntityTypesItem(ReindexUploadDto.EntityTypesEnum.INSTANCE)
      .indexSettings(indexSettings);
    when(reindexService.submitUploadReindex(TENANT_ID, requestBody)).thenReturn(new CompletableFuture<>());

    mockMvc.perform(preparePostRequest(reindexUploadPath(), asJsonString(requestBody))
        .header(XOkapiHeaders.TENANT, TENANT_ID))
      .andExpect(status().isOk());
  }

  @Test
  void submitReindexMergeFailed_positive() throws Exception {
    when(reindexService.submitFailedMergeRangesReindex(TENANT_ID)).thenReturn(new CompletableFuture<>());

    mockMvc.perform(post(reindexFailedPath())
        .header(XOkapiHeaders.TENANT, TENANT_ID))
      .andExpect(status().isOk());
  }

  @Test
  void createIndex_positive() throws Exception {
    when(indexService.createIndex(RESOURCE, TENANT_ID))
      .thenReturn(getSuccessFolioCreateIndexResponse(List.of(INDEX_NAME)));

    mockMvc.perform(preparePostRequest(createIndicesPath(), asJsonString(createIndexRequest())))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("success")))
      .andExpect(jsonPath("$.indices", is(List.of(INDEX_NAME))));
  }

  @Test
  void createIndex_negative_indexAlreadyExists() throws Exception {
    var openSearchException =
      new OpenSearchException("Elasticsearch exception "
                              + "[type=resource_already_exists_exception, "
                              + "reason=index [instance_test-tenant/um_SBtCaRLKUOBbdmFZeKQ] already exists]");
    openSearchException.setIndex(new Index(INDEX_NAME, randomId()));

    when(indexService.createIndex(RESOURCE, TENANT_ID)).thenThrow(
      new SearchOperationException("error", openSearchException));

    mockMvc.perform(preparePostRequest(createIndicesPath(), asJsonString(createIndexRequest())))
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

    when(indexService.createIndex(RESOURCE, TENANT_ID)).thenThrow(
      new SearchOperationException("i/o error", openSearchException));

    mockMvc.perform(preparePostRequest(createIndicesPath(), asJsonString(createIndexRequest())))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(errorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("OpenSearchException")))
      .andExpect(jsonPath("$.errors[0].code", is("elasticsearch_error")));
  }

  @Test
  void createIndex_negative_unknownErrorInSearchOperationException() throws Exception {
    var errorMessage = "i/o error";
    when(indexService.createIndex(RESOURCE, TENANT_ID)).thenThrow(
      new SearchOperationException(errorMessage, new IOException(errorMessage)));

    mockMvc.perform(preparePostRequest(createIndicesPath(), asJsonString(createIndexRequest())))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(errorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("SearchOperationException")))
      .andExpect(jsonPath("$.errors[0].code", is("elasticsearch_error")));
  }

  @Test
  void createIndex_negative_resourceNameIsNotPassed() throws Exception {
    mockMvc.perform(preparePostRequest(createIndicesPath(), asJsonString(new CreateIndexRequest())))
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
    when(indexService.createIndex(RESOURCE, TENANT_ID)).thenThrow(new NullPointerException());
    mockMvc.perform(preparePostRequest(createIndicesPath(), asJsonString(createIndexRequest())))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].type", is("NullPointerException")))
      .andExpect(jsonPath("$.errors[0].code", is("unknown_error")));
  }

  @Test
  void updateMappings_positive() throws Exception {
    when(indexService.updateMappings(RESOURCE, TENANT_ID)).thenReturn(getSuccessIndexOperationResponse());
    mockMvc.perform(preparePostRequest("/search/index/mappings", asJsonString(updateMappingsRequest())))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("success")));
  }

  @Test
  void indexResources_positive() throws Exception {
    var resourceBody = resourceEvent(ResourceType.INSTANCE, mapOf("id", randomId()));

    when(resourceService.indexResources(List.of(resourceBody))).thenReturn(getSuccessIndexOperationResponse());

    mockMvc.perform(preparePostRequest("/search/index/records", asJsonString(resourceBody)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("success")));
  }

  @Test
  void updateIndexSettings_positive() throws Exception {
    when(indexService.updateIndexSettings(RESOURCE, TENANT_ID, createIndexDynamicSettings()))
      .thenReturn(getSuccessIndexOperationResponse());
    mockMvc.perform(preparePutRequest("/search/index/settings", asJsonString(updateIndexSettingsRequest())))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("success")));
  }

  @Test
  void canSubmitReindex() throws Exception {
    var jobId = randomId();
    var request = new ReindexRequest().resourceName(ReindexRequest.ResourceNameEnum.AUTHORITY);
    when(indexService.reindexInventory(TENANT_ID, request)).thenReturn(new ReindexJob().id(jobId));

    mockMvc.perform(post("/search/index/inventory/reindex")
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .content(asJsonString(request))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("id", is(jobId)));
  }

  @Test
  void reindexInventoryRecords_positive_withRecreateIndexFlag() throws Exception {
    var jobId = randomId();
    var request = new ReindexRequest().resourceName(ReindexRequest.ResourceNameEnum.AUTHORITY).recreateIndex(true);
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
  void canSubmitReindex_negative_noBody() throws Exception {
    var jobId = randomId();
    when(indexService.reindexInventory(TENANT_ID, null)).thenReturn(new ReindexJob().id(jobId));

    mockMvc.perform(post("/search/index/inventory/reindex")
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest());
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
    var request = new ReindexRequest().resourceName(ReindexRequest.ResourceNameEnum.AUTHORITY);
    var constraintViolation = mock(ConstraintViolationImpl.class);
    when(constraintViolation.getPropertyPath()).thenReturn(PathImpl.createPathFromString("recreateIndices"));
    when(constraintViolation.getMessage()).thenReturn("must be boolean");
    when(indexService.reindexInventory(TENANT_ID, request)).thenThrow(
      new ConstraintViolationException("error", Set.<ConstraintViolation<?>>of(constraintViolation)));

    mockMvc.perform(post("/search/index/inventory/reindex")
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .content(asJsonString(request))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("recreateIndices must be boolean")))
      .andExpect(jsonPath("$.errors[0].type", is("ConstraintViolationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void reindexInventoryRecords_negative_illegalArgumentException() throws Exception {
    var request = new ReindexRequest().resourceName(ReindexRequest.ResourceNameEnum.AUTHORITY);
    when(indexService.reindexInventory(TENANT_ID, request)).thenThrow(new IllegalArgumentException("invalid value"));

    mockMvc.perform(post("/search/index/inventory/reindex")
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .content(asJsonString(request))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", containsString("invalid value")))
      .andExpect(jsonPath("$.errors[0].type", is("IllegalArgumentException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void getReindexStatus_positive() throws Exception {
    var reindexStatus = new ReindexStatusItem().entityType(ReindexEntityType.INSTANCE.name());
    when(reindexStatusService.getReindexStatuses(TENANT_ID)).thenReturn(List.of(reindexStatus));

    mockMvc.perform(get(reindexInstanceRecordsStatus())
        .header(XOkapiHeaders.TENANT, TENANT_ID))
      .andExpect(status().isOk())
      .andExpect(jsonPath("[0].entityType", is(reindexStatus.getEntityType())));
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
    return new CreateIndexRequest().resourceName(RESOURCE.getName());
  }

  private static UpdateMappingsRequest updateMappingsRequest() {
    return new UpdateMappingsRequest().resourceName(RESOURCE.getName());
  }

  private static UpdateIndexDynamicSettingsRequest updateIndexSettingsRequest() {
    return new UpdateIndexDynamicSettingsRequest().resourceName(RESOURCE.getName())
      .indexSettings(createIndexDynamicSettings());
  }

  private static IndexDynamicSettings createIndexDynamicSettings() {
    return new IndexDynamicSettings().numberOfReplicas(2).refreshInterval(1);
  }
}
