package org.folio.search.service.reindex;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.message.FormattedMessage;
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

  /**
   * Determines and sets the member tenant ID context for processing.
   * Uses the reindex status service to get the target tenant ID from the database.
   *
   * @return the member tenant ID that was set, or null if none was set
   */
  private String getMemberTenantIdForProcessing() {
    var memberTenantId = reindexStatusService.getTargetTenantId();

    if (StringUtils.isNotBlank(memberTenantId)) {
      log.debug("getMemberTenantIdForProcessing:: Setting member tenant context: {}", memberTenantId);
      ReindexContext.setMemberTenantId(memberTenantId);
      return memberTenantId;
    }

    return null;
  }

  /**
   * Determines and sets the member tenant ID context for range processing.
   *
   * @param event the reindex range event containing member tenant information
   * @return the member tenant ID that was set, or null if none was set
   */
  private String getMemberTenantIdForRangeProcessing(ReindexRangeIndexEvent event) {
    var memberTenantId = event.getMemberTenantId();

    if (StringUtils.isNotBlank(memberTenantId)) {
      log.debug("getMemberTenantIdForRangeProcessing:: Setting member tenant context: {}", memberTenantId);
      ReindexContext.setMemberTenantId(memberTenantId);
      return memberTenantId;
    }

    return null;
  }

  public boolean process(ReindexRangeIndexEvent event) {
    var memberTenantId = getMemberTenantIdForRangeProcessing(event);

    try {
      log.info("process:: ReindexRangeIndexEvent [id: {}, tenantId: {}, memberTenantId: {}, "
          + "entityType: {}, lower: {}, upper: {}, ts: {}]",
        event.getId(), event.getTenant(), memberTenantId,
        event.getEntityType(), event.getLower(), event.getUpper(), event.getTs());

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
      // Clean up member tenant context
      if (memberTenantId != null) {
        ReindexContext.clearMemberTenantId();
      }
    }
  }

  public boolean process(ReindexRecordsEvent event) {
    var memberTenantId = getMemberTenantIdForProcessing();
    var entityType = event.getRecordType().getEntityType();

    try {
      log.info("process:: ReindexRecordsEvent [rangeId: {}, tenantId: {}, memberTenantId: {}, "
        + "recordType: {}, recordsCount: {}]",
        event.getRangeId(), event.getTenant(), memberTenantId, event.getRecordType(), event.getRecords().size());

      mergeRangeService.saveEntities(event);
      mergeRangeService.updateStatus(entityType, event.getRangeId(), ReindexRangeStatus.SUCCESS, null);
      reindexStatusService.addProcessedMergeRanges(entityType, 1);
      log.info("process:: ReindexRecordsEvent processed [rangeId: {}, recordType: {}]",
        event.getRangeId(), event.getRecordType());
    } catch (PessimisticLockingFailureException ex) {
      log.warn(new FormattedMessage("process:: ReindexRecordsEvent indexing recoverable error"
                                    + " [rangeId: {}, error: {}]", event.getRangeId(), ex.getMessage()), ex);
      throw new ReindexException(ex.getMessage());
    } catch (Exception ex) {
      log.error(new FormattedMessage("process:: ReindexRecordsEvent indexing error [rangeId: {}, error: {}]",
        event.getRangeId(), ex.getMessage()), ex);
      reindexStatusService.updateReindexMergeFailed(entityType);
      mergeRangeService.updateStatus(entityType, event.getRangeId(), ReindexRangeStatus.FAIL, ex.getMessage());
      return true;
    } finally {
      // Clean up member tenant context
      if (memberTenantId != null) {
        ReindexContext.clearMemberTenantId();
      }
    }

    if (reindexStatusService.isMergeCompleted()) {
      // Get targetTenantId before migration
      var targetTenantId = reindexStatusService.getTargetTenantId();

      // Perform migration of staging tables
      log.info("process:: Merge completed. Starting migration of staging tables");
      performStagingMigration(targetTenantId);

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

      log.info("process:: Migration and upload phase completed for {}",
        targetTenantId != null ? "tenant: " + targetTenantId : "consortium");
    }

    return true;
  }

  private void performStagingMigration(String targetTenantId) {
    try {
      log.info("performStagingMigration:: Starting staging migration");
      reindexStatusService.updateStagingStarted();

      mergeRangeService.performStagingMigration(targetTenantId);

      reindexStatusService.updateStagingCompleted();
      log.info("performStagingMigration:: Migration completed successfully. Starting upload phase");
    } catch (Exception e) {
      log.error("performStagingMigration:: Migration failed", e);
      reindexStatusService.updateStagingFailed();
      throw new ReindexException("Migration failed: " + e.getMessage());
    }
  }
}
