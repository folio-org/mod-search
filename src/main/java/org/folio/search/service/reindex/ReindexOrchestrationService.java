package org.folio.search.service.reindex;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.exception.ReindexException;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReindexOrchestrationService {

  private final ReindexUploadRangeIndexService rangeIndexService;
  private final ReindexStatusService reindexStatusService;
  private final PrimaryResourceRepository elasticRepository;
  private final MultiTenantSearchDocumentConverter documentConverter;

  public boolean process(ReindexRangeIndexEvent event) {
    var resourceEvents = rangeIndexService.fetchRecordRange(event);
    var documents = documentConverter.convert(resourceEvents).values().stream().flatMap(Collection::stream).toList();
    var folioIndexOperationResponse = elasticRepository.indexResources(documents);
    rangeIndexService.updateFinishDate(event);
    if (folioIndexOperationResponse.getStatus() == FolioIndexOperationResponse.StatusEnum.ERROR) {
      reindexStatusService.updateReindexUploadFailed(event.getEntityType());
      throw new ReindexException(folioIndexOperationResponse.getErrorMessage());
    }

    reindexStatusService.addProcessedUploadRanges(event.getEntityType(), documents.size());
    return true;
  }
}
