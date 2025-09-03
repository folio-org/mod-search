package org.folio.search.service.reindex;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.exception.ReindexException;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexRangeStatus;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ReindexOrchestrationService {

  private final ReindexUploadRangeIndexService uploadRangeService;
  private final ReindexMergeRangeIndexService mergeRangeService;
  private final ReindexStatusService reindexStatusService;
  private final PrimaryResourceRepository elasticRepository;
  private final ReindexService reindexService;
  private final MultiTenantSearchDocumentConverter documentConverter;
  private final FolioExecutionContext context;
  private final ReindexConfigurationProperties reindexConfig;

  public boolean process(ReindexRangeIndexEvent event) {
    // Restore member tenant context for upload processing
    if (event.getMemberTenantId() != null) {
      ReindexContext.setMemberTenantId(event.getMemberTenantId());
    }
    
    try {
      log.info("process:: ReindexRangeIndexEvent [id: {}, tenantId: {}, memberTenantId: {}, "
        + "entityType: {}, lower: {}, upper: {}]",
        event.getId(), event.getTenant(), event.getMemberTenantId(), 
        event.getEntityType(), event.getLower(), event.getUpper());
      
      var resourceEvents = uploadRangeService.fetchRecordRange(event);
      var documents = documentConverter.convert(resourceEvents).values().stream().flatMap(Collection::stream).toList();
      var folioIndexOperationResponse = elasticRepository.indexResources(documents);
      if (folioIndexOperationResponse.getStatus() == FolioIndexOperationResponse.StatusEnum.ERROR) {
        log.warn("process:: ReindexRangeIndexEvent indexing error [id: {}, error: {}]",
          event.getId(), folioIndexOperationResponse.getErrorMessage());
        uploadRangeService.updateStatus(event, ReindexRangeStatus.FAIL, folioIndexOperationResponse.getErrorMessage());
        reindexStatusService.updateReindexUploadFailed(event.getEntityType());
        throw new ReindexException(folioIndexOperationResponse.getErrorMessage());
      }
      uploadRangeService.updateStatus(event, ReindexRangeStatus.SUCCESS, null);

      log.info("process:: ReindexRangeIndexEvent processed [id: {}]", event.getId());
      reindexStatusService.addProcessedUploadRanges(event.getEntityType(), 1);
      return true;
    } finally {
      if (event.getMemberTenantId() != null) {
        ReindexContext.clearMemberTenantId();
      }
    }
  }

  public boolean process(ReindexRecordsEvent event) {
    log.info("process:: ReindexRecordsEvent [rangeId: {}, tenantId: {}, recordType: {}, recordsCount: {}]",
      event.getRangeId(), event.getTenant(), event.getRecordType(), event.getRecords().size());
    var entityType = event.getRecordType().getEntityType();

    try {
      mergeRangeService.saveEntities(event);
      reindexStatusService.addProcessedMergeRanges(entityType, 1);
      mergeRangeService.updateStatus(entityType, event.getRangeId(), ReindexRangeStatus.SUCCESS, null);
      log.info("process:: ReindexRecordsEvent processed [rangeId: {}, recordType: {}]",
        event.getRangeId(), event.getRecordType());
      if (reindexStatusService.isMergeCompleted()) {
        // Get targetTenantId before deduplication
        var targetTenantId = reindexStatusService.getTargetTenantId();
        
        // Perform deduplication of staging tables
        log.info("Merge completed. Starting deduplication of staging tables");
        try {
          mergeRangeService.performDeduplication(targetTenantId);
          log.info("Deduplication completed successfully. Starting upload phase");
        } catch (Exception e) {
          log.error("Deduplication failed", e);
          reindexStatusService.updateReindexMergeFailed(entityType);
          throw new ReindexException("Deduplication failed: " + e.getMessage());
        }
        // Check if this is a tenant-specific reindex that requires OpenSearch document cleanup
        if (targetTenantId != null) {
          log.info("process:: Starting tenant-specific upload phase with document cleanup [targetTenant: {}]",
            targetTenantId);
          reindexService.submitUploadReindexWithTenantCleanup(context.getTenantId(),
                                                             ReindexEntityType.supportUploadTypes(),
                                                             targetTenantId);
        } else {
          log.info("process:: Starting standard upload phase without tenant-specific cleanup");
          reindexService.submitUploadReindex(context.getTenantId(), ReindexEntityType.supportUploadTypes());
        }
      }
    } catch (PessimisticLockingFailureException ex) {
      log.warn(new FormattedMessage("process:: ReindexRecordsEvent indexing recoverable error"
                                    + " [rangeId: {}, error: {}]", event.getRangeId(), ex.getMessage()), ex);
      throw new ReindexException(ex.getMessage());
    } catch (Exception ex) {
      log.error(new FormattedMessage("process:: ReindexRecordsEvent indexing error [rangeId: {}, error: {}]",
        event.getRangeId(), ex.getMessage()), ex);
      reindexStatusService.updateReindexMergeFailed(entityType);
      mergeRangeService.updateStatus(entityType, event.getRangeId(), ReindexRangeStatus.FAIL, ex.getMessage());
    }

    return true;
  }
}
