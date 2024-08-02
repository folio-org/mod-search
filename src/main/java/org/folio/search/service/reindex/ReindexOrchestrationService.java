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

  private final ReindexRangeIndexService rangeIndexService;
  private final PrimaryResourceRepository elasticRepository;
  private final MultiTenantSearchDocumentConverter documentConverter;

  public boolean process(ReindexRangeIndexEvent event) {
    var resourceEvents = rangeIndexService.fetchRecordRange(event);
    var documents = documentConverter.convert(resourceEvents).values().stream().flatMap(Collection::stream).toList();
    var folioIndexOperationResponse = elasticRepository.indexResources(documents);
    rangeIndexService.updateFinishDate(event);
    if (folioIndexOperationResponse.getStatus() == FolioIndexOperationResponse.StatusEnum.ERROR) {
      // TODO MSEARCH-797 - update status as failed indicating upload has failed
      throw new ReindexException(folioIndexOperationResponse.getErrorMessage());
    }
    return true;
  }
}
