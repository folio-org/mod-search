package org.folio.search.controller;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.CreateIndexRequest;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ReindexFullRequest;
import org.folio.search.domain.dto.ReindexJob;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.domain.dto.ReindexStatusItem;
import org.folio.search.domain.dto.ReindexUploadDto;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.UpdateIndexDynamicSettingsRequest;
import org.folio.search.domain.dto.UpdateMappingsRequest;
import org.folio.search.model.types.ResourceType;
import org.folio.search.rest.resource.IndexManagementApi;
import org.folio.search.service.IndexService;
import org.folio.search.service.ItemIndexService;
import org.folio.search.service.ResourceService;
import org.folio.search.service.reindex.ReindexService;
import org.folio.search.service.reindex.ReindexStatusService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller with set of endpoints for manipulating with Elasticsearch index API.
 */
@Log4j2
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class IndexManagementController implements IndexManagementApi {

  private final IndexService indexService;
  private final ResourceService resourceService;
  private final ReindexService reindexService;
  private final ReindexStatusService reindexStatusService;
  private final ObjectProvider<ItemIndexService> itemIndexServiceProvider;

  @Override
  public ResponseEntity<FolioCreateIndexResponse> createIndices(String tenantId, CreateIndexRequest request) {
    return ResponseEntity.ok(indexService.createIndex(ResourceType.byName(request.getResourceName()), tenantId));
  }

  @Override
  public ResponseEntity<FolioIndexOperationResponse> indexRecords(String tenantId, List<ResourceEvent> events) {
    var response = resourceService.indexResources(events);
    var itemIndexService = itemIndexServiceProvider.getIfAvailable();
    if (itemIndexService == null) {
      return ResponseEntity.ok(response);
    }
    return ResponseEntity.ok(mergeResponses(response, itemIndexService.indexItemEvents(events)));
  }

  @Override
  public ResponseEntity<Void> reindexInstanceRecords(String tenantId, ReindexFullRequest reindexFullRequest) {
    var targetTenantId = reindexFullRequest != null && isNotBlank(reindexFullRequest.getTenantId())
      ? reindexFullRequest.getTenantId()
      : null;
    var indexSettings = reindexFullRequest != null ? reindexFullRequest.getIndexSettings() : null;

    log.info("Attempting to run full-reindex for instance records [requestingTenant: {}, targetTenant: {}]",
      tenantId, targetTenantId != null ? targetTenantId : "all consortium members");

    reindexService.submitFullReindex(tenantId, indexSettings, targetTenantId);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<Void> reindexUploadInstanceRecords(String tenantId, ReindexUploadDto reindexUploadDto) {
    reindexService.submitUploadReindex(tenantId, reindexUploadDto);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<Void> reindexFailedMergeRanges(String tenantId) {
    reindexService.submitFailedMergeRangesReindex(tenantId);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<ReindexJob> reindexInventoryRecords(String tenantId, ReindexRequest request) {
    log.info("Attempting to start reindex for inventory [tenant: {}]", tenantId);
    return ResponseEntity.ok(indexService.reindexInventory(tenantId, request));
  }

  @Override
  public ResponseEntity<FolioIndexOperationResponse> updateIndexDynamicSettings(
    String tenantId, UpdateIndexDynamicSettingsRequest request) {
    return ResponseEntity.ok(indexService.updateIndexSettings(ResourceType.byName(request.getResourceName()), tenantId,
      request.getIndexSettings()));
  }

  @Override
  public ResponseEntity<FolioIndexOperationResponse> updateMappings(String tenantId, UpdateMappingsRequest request) {
    return ResponseEntity.ok(indexService.updateMappings(ResourceType.byName(request.getResourceName()), tenantId));
  }

  @Override
  public ResponseEntity<List<ReindexStatusItem>> getReindexStatus(String tenantId) {
    return ResponseEntity.ok(reindexStatusService.getReindexStatuses(tenantId));
  }

  private static FolioIndexOperationResponse mergeResponses(FolioIndexOperationResponse first,
                                                            FolioIndexOperationResponse second) {
    var errors = Stream.of(first, second)
      .map(FolioIndexOperationResponse::getErrorMessage)
      .filter(Objects::nonNull)
      .collect(Collectors.joining(", "));
    return errors.isEmpty() ? getSuccessIndexOperationResponse() : getErrorIndexOperationResponse(errors);
  }
}
