package org.folio.search.service.reindex;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.exception.ReindexException;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.model.types.ReindexEntityType;
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

  public boolean process(ReindexRangeIndexEvent event) {
    log.info("process:: ReindexRangeIndexEvent [id: {}, tenantId: {}, entityType: {}, lower: {}, upper: {}, ts: {}]",
      event.getId(), event.getTenant(), event.getEntityType(), event.getLower(), event.getUpper(), event.getTs());
    var resourceEvents = uploadRangeService.fetchRecordRange(event);
    var documents = documentConverter.convert(resourceEvents).values().stream().flatMap(Collection::stream).toList();
    var folioIndexOperationResponse = elasticRepository.indexResources(documents);
    uploadRangeService.updateFinishDate(event);
    if (folioIndexOperationResponse.getStatus() == FolioIndexOperationResponse.StatusEnum.ERROR) {
      log.warn("process:: ReindexRangeIndexEvent indexing error [id: {}, error: {}]",
        event.getId(), folioIndexOperationResponse.getErrorMessage());
      reindexStatusService.updateReindexUploadFailed(event.getEntityType());
      throw new ReindexException(folioIndexOperationResponse.getErrorMessage());
    }

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
      mergeRangeService.updateFinishDate(entityType, event.getRangeId());
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
      mergeRangeService.updateFinishDate(entityType, event.getRangeId());
    }

    return true;
  }
}
