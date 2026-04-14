package org.folio.search.service.reindex;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.s3.client.FolioS3Client;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.exception.ReindexException;
import org.folio.search.model.event.ReindexFileReadyEvent;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.event.ReindexRecordType;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexRangeStatus;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ReindexOrchestrationService {

  private static final int BATCH_SIZE = 500;

  private final ReindexUploadRangeIndexService uploadRangeService;
  private final ReindexMergeRangeIndexService mergeRangeService;
  private final ReindexStatusService reindexStatusService;
  private final PrimaryResourceRepository elasticRepository;
  private final ReindexService reindexService;
  private final MultiTenantSearchDocumentConverter documentConverter;
  private final FolioExecutionContext context;
  private final JsonConverter jsonConverter;
  private FolioS3Client folioS3Client;

  @Autowired(required = false)
  public void setFolioS3Client(FolioS3Client folioS3Client) {
    this.folioS3Client = folioS3Client;
  }

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

      var folioIndexOperationResponse = fetchRecordsAndIndexForUploadRange(event);
      if (folioIndexOperationResponse.getStatus() == FolioIndexOperationResponse.StatusEnum.ERROR) {
        throw handleReindexUploadFailure(event, folioIndexOperationResponse.getErrorMessage());
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

    try {
      log.info("process:: ReindexRecordsEvent [rangeId: {}, tenantId: {}, memberTenantId: {}, "
        + "recordType: {}, recordsCount: {}]",
        event.getRangeId(), event.getTenant(), memberTenantId, event.getRecordType(), event.getRecords().size());

      persistEntities(event);
      log.info("process:: ReindexRecordsEvent processed [rangeId: {}, recordType: {}]",
        event.getRangeId(), event.getRecordType());
      mergeRangeService.saveEntities(event);
      handleMergeSuccess(entityType, event.getRangeId(), event.getRecordType());
    } catch (PessimisticLockingFailureException ex) {
      log.warn(new FormattedMessage("process:: ReindexRecordsEvent indexing recoverable error"
                                    + " [rangeId: {}, error: {}]", event.getRangeId(), ex.getMessage()), ex);
      throw new ReindexException(ex.getMessage());
    } catch (Exception ex) {
      handleReindexMergeFailure(event, ex.getMessage());
      return true;
    } finally {
      // Clean up member tenant context
      if (memberTenantId != null) {
        ReindexContext.clearMemberTenantId();
      }
    }

    startUploadOnMergeCompletion();
    return true;
  }
  public boolean process(ReindexFileReadyEvent event) {
    log.info("process:: ReindexRecordsEvent [traceId: {}, rangeId: {}, tenantId: {}, recordType: {}]",
      event.getTraceId(), event.getRangeId(), event.getTenantId(), event.getRecordType());
    var entityType = event.getRecordType().getEntityType();

    try {
      readAndSave(event);
      handleMergeSuccess(entityType, event.getRangeId(), event.getRecordType());
    } catch (PessimisticLockingFailureException ex) {
      log.warn(new FormattedMessage("process:: ReindexRecordsEvent indexing recoverable error"
                                    + " [rangeId: {}, error: {}]", event.getRangeId(), ex.getMessage()), ex);
      throw new ReindexException(ex.getMessage());
    } catch (Exception ex) {
      handleMergeFailure(entityType, event.getRangeId(), ex);
    }

    return true;
  }
  private void persistEntities(ReindexRecordsEvent event) {
    var entityType = event.getRecordType().getEntityType();
    mergeRangeService.saveEntities(event);
    mergeRangeService.updateStatus(entityType, event.getRangeId(), ReindexRangeStatus.SUCCESS, null);
    reindexStatusService.addProcessedMergeRanges(entityType, 1);
  }

  private void startUploadOnMergeCompletion() {
    if (reindexStatusService.isMergeCompleted()) {
      // Get targetTenantId before migration
      var targetTenantId = reindexStatusService.getTargetTenantId();

      // Check if this is a tenant-specific reindex that requires staging migration and OpenSearch cleanup
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

  private void performStagingMigration(String targetTenantId) {
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

  private void readAndSave(ReindexFileReadyEvent event) throws IOException {
    try (var is = folioS3Client.read(event.getObjectKey());
         var isr = new InputStreamReader(is, StandardCharsets.UTF_8);
         var reader = new BufferedReader(isr)) {
      List<Object> batch = new ArrayList<>(BATCH_SIZE);
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.isBlank()) {
          batch.add(jsonConverter.fromJsonToMap(line));
        }
        if (batch.size() >= BATCH_SIZE) {
          saveBatch(event, batch, true);
        }
      }
      saveBatch(event, batch, false);
    }
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

  private void saveBatch(ReindexFileReadyEvent event, List<Object> batch, boolean clearAfterSave) {
    if (batch.isEmpty()) {
      return;
    }
    mergeRangeService.saveEntities(toReindexRecordsEvent(event, batch));
    if (clearAfterSave) {
      batch.clear();
    }
  }

  private ReindexRecordsEvent toReindexRecordsEvent(ReindexFileReadyEvent event, List<Object> records) {
    var reindexRecordsEvent = new ReindexRecordsEvent();
    reindexRecordsEvent.setRangeId(event.getRangeId());
    reindexRecordsEvent.setTenant(event.getTenantId());
    reindexRecordsEvent.setRecordType(event.getRecordType());
    reindexRecordsEvent.setRecords(records);
    return reindexRecordsEvent;
  }

  private void handleMergeSuccess(ReindexEntityType entityType, String rangeId, ReindexRecordType recordType) {
    reindexStatusService.addProcessedMergeRanges(entityType, 1);
    mergeRangeService.updateStatus(entityType, rangeId, ReindexRangeStatus.SUCCESS, null);
    log.info("process:: ReindexRecordsEvent processed [rangeId: {}, recordType: {}]", rangeId, recordType);
    if (reindexStatusService.isMergeCompleted()) {
      reindexService.submitUploadReindex(context.getTenantId(), ReindexEntityType.supportUploadTypes());
    }
  }

  private void handleMergeFailure(ReindexEntityType entityType, String rangeId, Exception ex) {
    log.error(new FormattedMessage("process:: ReindexRecordsEvent indexing error [rangeId: {}, error: {}]",
      rangeId, ex.getMessage()), ex);
    reindexStatusService.updateReindexMergeFailed(entityType);
    mergeRangeService.updateStatus(entityType, rangeId, ReindexRangeStatus.FAIL, ex.getMessage());
  }

  private void handleReindexMergeFailure(ReindexRecordsEvent event, String errorMessage) {
    log.warn("handleReindexMergeFailure:: ReindexRecordsEvent indexing error [rangeId: {}, error: {}]",
      event.getRangeId(), errorMessage);
    var entityType = event.getRecordType().getEntityType();
    reindexStatusService.updateReindexMergeFailed(entityType);
    mergeRangeService.updateStatus(entityType, event.getRangeId(), ReindexRangeStatus.FAIL, errorMessage);
  }
}
