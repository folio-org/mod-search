package org.folio.search.service.reindex;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.exception.ReindexException;
import org.folio.search.model.event.ReindexFileReadyEvent;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexRangeStatus;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.spring.FolioExecutionContext;

@Log4j2
public abstract class ReindexOrchestrationService {

  protected final ReindexMergeRangeIndexService mergeRangeService;
  private final ReindexUploadRangeIndexService uploadRangeService;
  private final ReindexStatusService reindexStatusService;
  private final PrimaryResourceRepository elasticRepository;
  private final ReindexService reindexService;
  private final MultiTenantSearchDocumentConverter documentConverter;
  private final FolioExecutionContext context;

  protected ReindexOrchestrationService(
      ReindexUploadRangeIndexService uploadRangeService,
      ReindexMergeRangeIndexService mergeRangeService,
      ReindexStatusService reindexStatusService,
      PrimaryResourceRepository elasticRepository,
      ReindexService reindexService,
      MultiTenantSearchDocumentConverter documentConverter,
      FolioExecutionContext context) {
    this.mergeRangeService = mergeRangeService;
    this.uploadRangeService = uploadRangeService;
    this.reindexStatusService = reindexStatusService;
    this.elasticRepository = elasticRepository;
    this.reindexService = reindexService;
    this.documentConverter = documentConverter;
    this.context = context;
  }

  public boolean process(ReindexRangeIndexEvent event) {
    var memberTenantId = getMemberTenantIdForRangeProcessing(event);

    try {
      log.info("process:: ReindexRangeIndexEvent [id: {}, tenantId: {}, memberTenantId: {}, "
               + "entityType: {}, lower: {}, upper: {}, ts: {}]",
        event.getId(), event.getTenant(), memberTenantId,
        event.getEntityType(), event.getLower(), event.getUpper(), event.getTs());

      var folioIndexOperationResponse = fetchRecordsAndIndexForUploadRange(event);
      if (folioIndexOperationResponse.getStatus() == FolioIndexOperationResponse.StatusEnum.ERROR) {
        throw handleReindexUploadFailure(event, folioIndexOperationResponse.getErrorMessage());
      }
      uploadRangeService.updateStatus(event, ReindexRangeStatus.SUCCESS, null);

      log.info("process:: ReindexRangeIndexEvent processed [id: {}]", event.getId());
      reindexStatusService.addProcessedUploadRanges(event.getEntityType(), 1);
      return true;
    } finally {
      if (memberTenantId != null) {
        ReindexContext.clearMemberTenantId();
      }
    }
  }

  public boolean process(ReindexRecordsEvent event) {
    throw new UnsupportedOperationException("process(ReindexRecordsEvent) is not supported in this reindex mode");
  }

  public boolean process(ReindexFileReadyEvent event) {
    throw new UnsupportedOperationException("process(ReindexFileReadyEvent) is not supported in this reindex mode");
  }

  /**
   * Determines and sets the member tenant ID context for processing.
   * Uses the reindex status service to get the target tenant ID from the database.
   *
   * @return the member tenant ID that was set, or null if none was set
   */
  protected String getMemberTenantIdForProcessing() {
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
  protected String getMemberTenantIdForRangeProcessing(ReindexRangeIndexEvent event) {
    var memberTenantId = event.getMemberTenantId();

    if (StringUtils.isNotBlank(memberTenantId)) {
      log.debug("getMemberTenantIdForRangeProcessing:: Setting member tenant context: {}", memberTenantId);
      ReindexContext.setMemberTenantId(memberTenantId);
      return memberTenantId;
    }

    return null;
  }

  protected void persistEntities(ReindexRecordsEvent event) {
    var entityType = event.getRecordType().getEntityType();
    mergeRangeService.saveEntities(event);
    handleMergeSuccess(entityType, event.getRangeId());
  }

  protected void startUploadOnMergeCompletion() {
    if (reindexStatusService.isMergeCompleted()) {
      var targetTenantId = reindexStatusService.getTargetTenantId();

      if (targetTenantId != null) {
        log.info("process:: Merge completed for member tenant reindex. Starting staging migration [targetTenant: {}]",
          targetTenantId);
        performStagingMigration(targetTenantId);

        log.info("process:: Starting tenant-specific upload phase with document cleanup [targetTenant: {}]",
          targetTenantId);
        reindexService.submitUploadReindexWithTenantCleanup(context.getTenantId(),
          ReindexEntityType.supportUploadTypes(),
          targetTenantId);
      } else {
        log.info("process:: Merge completed. Starting standard upload phase without tenant-specific cleanup");
        reindexService.submitUploadReindex(context.getTenantId(), ReindexEntityType.supportUploadTypes());
      }

      log.info("process:: Upload phase submitted for {}",
        targetTenantId != null ? "tenant: " + targetTenantId : "consortium");
    }
  }

  protected void performStagingMigration(String targetTenantId) {
    try {
      log.info("performStagingMigration:: Starting staging migration for [targetTenant: {}]", targetTenantId);
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

  protected void handleMergeSuccess(ReindexEntityType entityType, String rangeId) {
    mergeRangeService.updateStatus(entityType, rangeId, ReindexRangeStatus.SUCCESS, null);
    reindexStatusService.addProcessedMergeRanges(entityType, 1);
  }

  protected void handleMergeFailure(ReindexEntityType entityType, String rangeId, Exception ex) {
    log.error(new FormattedMessage("process:: ReindexRecordsEvent indexing error [rangeId: {}, error: {}]",
      rangeId, ex.getMessage()), ex);
    reindexStatusService.updateReindexMergeFailed(entityType);
    mergeRangeService.updateStatus(entityType, rangeId, ReindexRangeStatus.FAIL, ex.getMessage());
  }

  protected void handleReindexMergeFailure(ReindexRecordsEvent event, String errorMessage) {
    log.warn("handleReindexMergeFailure:: ReindexRecordsEvent indexing error [rangeId: {}, error: {}]",
      event.getRangeId(), errorMessage);
    var entityType = event.getRecordType().getEntityType();
    reindexStatusService.updateReindexMergeFailed(entityType);
    mergeRangeService.updateStatus(entityType, event.getRangeId(), ReindexRangeStatus.FAIL, errorMessage);
  }

  private FolioIndexOperationResponse fetchRecordsAndIndexForUploadRange(ReindexRangeIndexEvent event) {
    try {
      var resourceEvents = uploadRangeService.fetchRecordRange(event);
      var documents = documentConverter.convertForReindex(resourceEvents);
      return elasticRepository.indexResources(documents);
    } catch (Exception ex) {
      throw handleReindexUploadFailure(event, ex.getMessage());
    }
  }

  private ReindexException handleReindexUploadFailure(ReindexRangeIndexEvent event, String errorMessage) {
    log.warn("handleReindexUploadFailure:: ReindexRangeIndexEvent indexing error [eventId: {}, error: {}]",
      event.getId(), errorMessage);
    uploadRangeService.updateStatus(event, ReindexRangeStatus.FAIL, errorMessage);
    reindexStatusService.updateReindexUploadFailed(event.getEntityType());
    return new ReindexException(errorMessage);
  }
}
