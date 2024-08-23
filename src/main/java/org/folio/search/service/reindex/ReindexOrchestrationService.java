package org.folio.search.service.reindex;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.exception.ReindexException;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ReindexOrchestrationService {

  private final ReindexUploadRangeIndexService uploadRangeIndexService;
  private final ReindexMergeRangeIndexService mergeRangeIndexService;
  private final ReindexStatusService reindexStatusService;
  private final PrimaryResourceRepository elasticRepository;
  private final MultiTenantSearchDocumentConverter documentConverter;

  public boolean process(ReindexRangeIndexEvent event) {
    log.info("process:: ReindexRangeIndexEvent [id: {}, tenantId: {}, entityType: {}, offset: {}, limit: {}, ts: {}]",
      event.getId(), event.getTenant(), event.getEntityType(), event.getOffset(), event.getLimit(), event.getTs());
    var resourceEvents = uploadRangeIndexService.fetchRecordRange(event);
    var documents = documentConverter.convert(resourceEvents).values().stream().flatMap(Collection::stream).toList();
    var folioIndexOperationResponse = elasticRepository.indexResources(documents);
    uploadRangeIndexService.updateFinishDate(event);
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
      mergeRangeIndexService.saveEntities(event);
      reindexStatusService.addProcessedMergeRanges(entityType, 1);
    } catch (Exception ex) {
      log.warn("process:: ReindexRecordsEvent indexing error [rangeId: {}, error: {}]",
        event.getRangeId(), ex);
      reindexStatusService.updateReindexMergeFailed(List.of(entityType));
    } finally {
      log.info("process:: ReindexRecordsEvent processed [rangeId: {}]", event.getRangeId());
      mergeRangeIndexService.updateFinishDate(entityType, event.getRangeId());
    }

    return true;
  }
}
