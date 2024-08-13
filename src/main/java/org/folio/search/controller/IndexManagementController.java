package org.folio.search.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.CreateIndexRequest;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ReindexJob;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.domain.dto.ReindexStatusItem;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.UpdateIndexDynamicSettingsRequest;
import org.folio.search.domain.dto.UpdateMappingsRequest;
import org.folio.search.rest.resource.IndexManagementApi;
import org.folio.search.service.IndexService;
import org.folio.search.service.ResourceService;
import org.folio.search.service.reindex.ReindexRangeIndexService;
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
  private final ReindexRangeIndexService reindexRangeService;
  private final ResourceService resourceService;

  @Override
  public ResponseEntity<FolioCreateIndexResponse> createIndices(String tenantId, CreateIndexRequest request) {
    return ResponseEntity.ok(indexService.createIndex(request.getResourceName(), tenantId));
  }

  @Override
  public ResponseEntity<FolioIndexOperationResponse> indexRecords(List<ResourceEvent> events) {
    return ResponseEntity.ok(resourceService.indexResources(events));
  }

  @Override
  public ResponseEntity<ReindexJob> reindexInventoryRecords(String tenantId, ReindexRequest request) {
    log.info("Attempting to start reindex for inventory [tenant: {}]", tenantId);
    return ResponseEntity.ok(indexService.reindexInventory(tenantId, request));
  }

  @Override
  public ResponseEntity<FolioIndexOperationResponse> updateIndexDynamicSettings(
    String tenantId, UpdateIndexDynamicSettingsRequest request) {
    return ResponseEntity.ok(indexService.updateIndexSettings(request.getResourceName(), tenantId,
      request.getIndexSettings()));
  }

  @Override
  public ResponseEntity<FolioIndexOperationResponse> updateMappings(String tenantId, UpdateMappingsRequest request) {
    return ResponseEntity.ok(indexService.updateMappings(request.getResourceName(), tenantId));
  }

  @Override
  public ResponseEntity<List<ReindexStatusItem>> getReindexStatus(String tenantId) {
    return ResponseEntity.ok(reindexRangeService.getReindexStatuses(tenantId));
  }
}
