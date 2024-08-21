package org.folio.search.service.reindex;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.exception.ReindexException;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.model.types.IndexingDataFormat;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ReindexOrchestrationServiceTest {

  @Mock
  private ReindexUploadRangeIndexService rangeIndexService;
  @Mock
  private ReindexStatusService reindexStatusService;
  @Mock
  private PrimaryResourceRepository elasticRepository;
  @Mock
  private MultiTenantSearchDocumentConverter documentConverter;

  @InjectMocks
  private ReindexOrchestrationService service;

  @Test
  void process_shouldProcessSuccessfully() {
    // Arrange
    var event = reindexEvent();
    var resourceEvent = new ResourceEvent();
    var folioIndexOperationResponse = new FolioIndexOperationResponse()
      .status(FolioIndexOperationResponse.StatusEnum.SUCCESS);

    when(rangeIndexService.fetchRecordRange(event)).thenReturn(List.of(resourceEvent));
    when(documentConverter.convert(List.of(resourceEvent))).thenReturn(Map.of("key", List.of(SearchDocumentBody.of(null,
      IndexingDataFormat.JSON, resourceEvent, IndexActionType.INDEX))));
    when(elasticRepository.indexResources(any())).thenReturn(folioIndexOperationResponse);

    // Act
    boolean result = service.process(event);

    // Assert
    assertTrue(result);
    verify(rangeIndexService).fetchRecordRange(event);
    verify(documentConverter).convert(List.of(resourceEvent));
    verify(elasticRepository).indexResources(any());
    verify(reindexStatusService).addProcessedUploadRanges(event.getEntityType(), 1);
  }

  @Test
  void process_shouldThrowReindexException_whenElasticSearchReportsError() {
    // Arrange
    var event = reindexEvent();
    var resourceEvent = new ResourceEvent();
    var folioIndexOperationResponse = new FolioIndexOperationResponse()
      .status(FolioIndexOperationResponse.StatusEnum.ERROR)
      .errorMessage("Error occurred during indexing.");

    when(rangeIndexService.fetchRecordRange(event)).thenReturn(List.of(resourceEvent));
    when(documentConverter.convert(List.of(resourceEvent))).thenReturn(Map.of("key", List.of(SearchDocumentBody.of(null,
      IndexingDataFormat.JSON, resourceEvent, IndexActionType.INDEX))));
    when(elasticRepository.indexResources(any())).thenReturn(folioIndexOperationResponse);

    // Act & Assert
    assertThrows(ReindexException.class, () -> service.process(event));

    verify(rangeIndexService).fetchRecordRange(event);
    verify(documentConverter).convert(List.of(resourceEvent));
    verify(elasticRepository).indexResources(any());
    verify(reindexStatusService).updateReindexUploadFailed(event.getEntityType());
  }

  private ReindexRangeIndexEvent reindexEvent() {
    var event = new ReindexRangeIndexEvent();
    event.setId(UUID.randomUUID());
    event.setEntityType(ReindexEntityType.INSTANCE);
    return event;
  }
}
