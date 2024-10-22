package org.folio.search.service.reindex;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.exception.ReindexException;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.event.ReindexRecordsEvent;
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
import org.springframework.dao.PessimisticLockingFailureException;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ReindexOrchestrationServiceTest {

  @Mock
  private ReindexUploadRangeIndexService uploadRangeIndexService;
  @Mock
  private ReindexMergeRangeIndexService mergeRangeIndexService;
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

    when(uploadRangeIndexService.fetchRecordRange(event)).thenReturn(List.of(resourceEvent));
    when(documentConverter.convert(List.of(resourceEvent))).thenReturn(Map.of("key", List.of(SearchDocumentBody.of(null,
      IndexingDataFormat.JSON, resourceEvent, IndexActionType.INDEX))));
    when(elasticRepository.indexResources(any())).thenReturn(folioIndexOperationResponse);

    // Act
    boolean result = service.process(event);

    // Assert
    assertTrue(result);
    verify(uploadRangeIndexService).fetchRecordRange(event);
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

    when(uploadRangeIndexService.fetchRecordRange(event)).thenReturn(List.of(resourceEvent));
    when(documentConverter.convert(List.of(resourceEvent))).thenReturn(Map.of("key", List.of(SearchDocumentBody.of(null,
      IndexingDataFormat.JSON, resourceEvent, IndexActionType.INDEX))));
    when(elasticRepository.indexResources(any())).thenReturn(folioIndexOperationResponse);

    // Act & Assert
    assertThrows(ReindexException.class, () -> service.process(event));

    verify(uploadRangeIndexService).fetchRecordRange(event);
    verify(documentConverter).convert(List.of(resourceEvent));
    verify(elasticRepository).indexResources(any());
    verify(reindexStatusService).updateReindexUploadFailed(event.getEntityType());
  }

  @Test
  void process_positive_reindexRecordsEvent() {
    var event = new ReindexRecordsEvent();
    event.setRangeId(UUID.randomUUID().toString());
    event.setRecordType(ReindexRecordsEvent.ReindexRecordType.INSTANCE);
    event.setRecords(emptyList());

    service.process(event);

    verify(mergeRangeIndexService).saveEntities(event);
    verify(reindexStatusService).addProcessedMergeRanges(ReindexEntityType.INSTANCE, 1);
    verify(mergeRangeIndexService).updateFinishDate(ReindexEntityType.INSTANCE, event.getRangeId());
  }

  @Test
  void process_negative_reindexRecordsEvent_shouldFailMergeOnException() {
    var event = new ReindexRecordsEvent();
    event.setRangeId(UUID.randomUUID().toString());
    event.setRecordType(ReindexRecordsEvent.ReindexRecordType.INSTANCE);
    event.setRecords(emptyList());
    doThrow(new RuntimeException()).when(mergeRangeIndexService).saveEntities(event);

    service.process(event);

    verify(reindexStatusService).updateReindexMergeFailed();
    verify(mergeRangeIndexService).updateFinishDate(ReindexEntityType.INSTANCE, event.getRangeId());
    verifyNoMoreInteractions(reindexStatusService);
  }

  @Test
  void process_negative_reindexRecordsEvent_shouldNotFailMergeOnPessimisticLockingFailureException() {
    var event = new ReindexRecordsEvent();
    event.setRangeId(UUID.randomUUID().toString());
    event.setRecordType(ReindexRecordsEvent.ReindexRecordType.INSTANCE);
    event.setRecords(emptyList());
    doThrow(new PessimisticLockingFailureException("Deadlock")).when(mergeRangeIndexService).saveEntities(event);

    assertThrows(ReindexException.class, () -> service.process(event));

    verifyNoMoreInteractions(mergeRangeIndexService);
    verifyNoMoreInteractions(reindexStatusService);
  }

  private ReindexRangeIndexEvent reindexEvent() {
    var event = new ReindexRangeIndexEvent();
    event.setId(UUID.randomUUID());
    event.setEntityType(ReindexEntityType.INSTANCE);
    return event;
  }
}
