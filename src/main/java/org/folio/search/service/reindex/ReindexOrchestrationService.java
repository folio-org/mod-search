package org.folio.search.service.reindex;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.s3.client.FolioS3Client;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.exception.ReindexException;
import org.folio.search.model.event.ReindexFileReadyEvent;
import org.folio.search.model.event.ReindexRangeIndexEvent;
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

  public boolean process(ReindexRangeIndexEvent event) {
    log.info("process:: ReindexRangeIndexEvent [id: {}, tenantId: {}, entityType: {}, lower: {}, upper: {}, ts: {}]",
      event.getId(), event.getTenant(), event.getEntityType(), event.getLower(), event.getUpper(), event.getTs());

    var folioIndexOperationResponse = fetchRecordsAndIndexForUploadRange(event);
    if (folioIndexOperationResponse.getStatus() == FolioIndexOperationResponse.StatusEnum.ERROR) {
      throw handleReindexUploadFailure(event, folioIndexOperationResponse.getErrorMessage());
    }
    uploadRangeService.updateStatus(event, ReindexRangeStatus.SUCCESS, null);

    log.info("process:: ReindexRangeIndexEvent processed [id: {}]", event.getId());
    reindexStatusService.addProcessedUploadRanges(event.getEntityType(), 1);
    return true;
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
        reindexService.submitUploadReindex(context.getTenantId(), ReindexEntityType.supportUploadTypes());
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

  @SuppressWarnings("checkstyle:MethodLength")
  public boolean process(ReindexFileReadyEvent event) {
    log.info("process:: ReindexRecordsEvent [traceId: {}, rangeId: {}, tenantId: {}, recordType: {}]",
      event.getTraceId(), event.getRangeId(), event.getTenantId(), event.getRecordType());
    var entityType = event.getRecordType().getEntityType();

    try {
      var inputStreamReader = new InputStreamReader(folioS3Client.read(event.getObjectKey()), StandardCharsets.UTF_8);
      var reader = new BufferedReader(inputStreamReader);
      List<Object> batch = new ArrayList<>(BATCH_SIZE);
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.isBlank()) {
          batch.add(jsonConverter.convertToMap(line));
        }
        if (batch.size() >= BATCH_SIZE) {
          var reindexRecordsEvent = new ReindexRecordsEvent();
          reindexRecordsEvent.setRangeId(event.getRangeId());
          reindexRecordsEvent.setTenant(event.getTenantId());
          reindexRecordsEvent.setRecordType(event.getRecordType());
          reindexRecordsEvent.setRecords(batch);
          mergeRangeService.saveEntities(reindexRecordsEvent);
          batch.clear();
        }
      }
      if (!batch.isEmpty()) {
        var reindexRecordsEvent = new ReindexRecordsEvent();
        reindexRecordsEvent.setRangeId(event.getRangeId());
        reindexRecordsEvent.setTenant(event.getTenantId());
        reindexRecordsEvent.setRecordType(event.getRecordType());
        reindexRecordsEvent.setRecords(batch);
        mergeRangeService.saveEntities(reindexRecordsEvent);
      }

      reindexStatusService.addProcessedMergeRanges(entityType, 1);
      mergeRangeService.updateStatus(entityType, event.getRangeId(), ReindexRangeStatus.SUCCESS, null);
      log.info("process:: ReindexRecordsEvent processed [rangeId: {}, recordType: {}]",
        event.getRangeId(), event.getRecordType());
      if (reindexStatusService.isMergeCompleted()) {
        reindexService.submitUploadReindex(context.getTenantId(), ReindexEntityType.supportUploadTypes());
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

  private FolioIndexOperationResponse fetchRecordsAndIndexForUploadRange(ReindexRangeIndexEvent event) {
    try {
      var resourceEvents = uploadRangeService.fetchRecordRange(event);
      var documents = documentConverter.convert(resourceEvents).values().stream().flatMap(Collection::stream).toList();
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
