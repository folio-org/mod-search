package org.folio.search.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.IndexRebuildRequest;
import org.folio.search.domain.dto.IndexRebuildStatusItem;
import org.folio.search.rest.resource.IndexRebuildApi;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.indexrebuild.derived.DerivedReindexService;
import org.folio.search.service.indexrebuild.records.RecordsReindexService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller with endpoints for rebuilding indexes independently of each other.
 */
@Log4j2
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class IndexRebuildController implements IndexRebuildApi {

  private final RecordsReindexService recordsReindexService;
  private final DerivedReindexService derivedReindexService;

  @Override
  public ResponseEntity<Void> reindexItemRecords(String tenantId, IndexRebuildRequest request) {
    var targetTenantId = request != null ? request.getTenantId() : null;
    var indexSettings = request != null ? request.getIndexSettings() : null;
    log.info("Attempting to run item records reindex [tenant: {}, targetTenant: {}]", tenantId, targetTenantId);
    recordsReindexService.start(IndexRebuildTarget.ITEM, tenantId, targetTenantId, indexSettings);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<Void> retryFailedItemRecordsReindex(String tenantId) {
    log.info("Attempting to retry failed item records reindex [tenant: {}]", tenantId);
    recordsReindexService.retryFailed(IndexRebuildTarget.ITEM, tenantId);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<List<IndexRebuildStatusItem>> getItemRecordsReindexStatus(String tenantId) {
    return ResponseEntity.ok(recordsReindexService.getStatus(IndexRebuildTarget.ITEM, tenantId));
  }

  @Override
  public ResponseEntity<Void> reindexCallNumberRecords(String tenantId, IndexRebuildRequest request) {
    var targetTenantId = request != null ? request.getTenantId() : null;
    var indexSettings = request != null ? request.getIndexSettings() : null;
    log.info("Attempting to run call number reindex [tenant: {}, targetTenant: {}]", tenantId, targetTenantId);
    derivedReindexService.start(IndexRebuildTarget.CALL_NUMBER, tenantId, targetTenantId, indexSettings);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<Void> retryFailedCallNumberRecordsReindex(String tenantId) {
    log.info("Attempting to retry failed call number reindex [tenant: {}]", tenantId);
    derivedReindexService.retryFailed(IndexRebuildTarget.CALL_NUMBER, tenantId);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<List<IndexRebuildStatusItem>> getCallNumberRecordsReindexStatus(String tenantId) {
    return ResponseEntity.ok(derivedReindexService.getStatus(IndexRebuildTarget.CALL_NUMBER, tenantId));
  }
}
