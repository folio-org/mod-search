package org.folio.search.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.CreateIndexRequest;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.IndexSettings;
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
import org.folio.search.service.ResourceService;
import org.folio.search.service.reindex.ReindexService;
import org.folio.search.service.reindex.ReindexStatusService;
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

  @Override
  public ResponseEntity<FolioCreateIndexResponse> createIndices(String tenantId, CreateIndexRequest request) {
    return ResponseEntity.ok(indexService.createIndex(ResourceType.byName(request.getResourceName()), tenantId));
  }

  @Override
  public ResponseEntity<FolioIndexOperationResponse> indexRecords(List<ResourceEvent> events) {
    return ResponseEntity.ok(resourceService.indexResources(events));
  }

  @Override
  public ResponseEntity<Void> reindexInstanceRecords(String tenantId, IndexSettings indexSettings) {
    log.info("Attempting to run full-reindex for instance records [tenant: {}]", tenantId);
    reindexService.submitFullReindex(tenantId, indexSettings);
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
}
