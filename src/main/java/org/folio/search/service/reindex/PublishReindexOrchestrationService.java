package org.folio.search.service.reindex;

import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.search.exception.ReindexException;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@ConditionalOnProperty(name = "folio.reindex.reindex-type", havingValue = "PUBLISH")
public class PublishReindexOrchestrationService extends ReindexOrchestrationService {

  public PublishReindexOrchestrationService(ReindexUploadRangeIndexService uploadRangeService,
                                            ReindexMergeRangeIndexService mergeRangeService,
                                            ReindexStatusService reindexStatusService,
                                            PrimaryResourceRepository elasticRepository,
                                            ReindexService reindexService,
                                            MultiTenantSearchDocumentConverter documentConverter,
                                            FolioExecutionContext context) {
    super(uploadRangeService, mergeRangeService, reindexStatusService, elasticRepository,
      reindexService, documentConverter, context);
  }

  @Override
  public boolean process(ReindexRecordsEvent event) {
    var memberTenantId = getMemberTenantIdForProcessing();

    try {
      log.info("process:: ReindexRecordsEvent [rangeId: {}, tenantId: {}, memberTenantId: {}, "
               + "recordType: {}, recordsCount: {}]",
        event.getRangeId(), event.getTenant(), memberTenantId, event.getRecordType(), event.getRecords().size());

      persistEntities(event);
      log.info("process:: ReindexRecordsEvent processed [rangeId: {}, recordType: {}]",
        event.getRangeId(), event.getRecordType());
    } catch (PessimisticLockingFailureException ex) {
      log.warn(new FormattedMessage("process:: ReindexRecordsEvent indexing recoverable error"
                                    + " [rangeId: {}, error: {}]", event.getRangeId(), ex.getMessage()), ex);
      throw new ReindexException(ex.getMessage());
    } catch (Exception ex) {
      handleReindexMergeFailure(event, ex.getMessage());
      return true;
    } finally {
      if (memberTenantId != null) {
        ReindexContext.clearMemberTenantId();
      }
    }

    startUploadOnMergeCompletion();
    return true;
  }
}
