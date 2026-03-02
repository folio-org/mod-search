package org.folio.search.service.reindex;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.s3.client.FolioS3Client;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.exception.ReindexException;
import org.folio.search.model.event.ReindexFileReadyEvent;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.event.ReindexRecordType;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.model.types.IndexingDataFormat;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexRangeStatus;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
  @Mock
  private JsonConverter jsonConverter;
  @Mock
  private FolioS3Client folioS3Client;

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
  void process_shouldThrowReindexException_whenExceptionOccursDuringFetchRecords() {
    // Arrange
    var event = reindexEvent();
    var exceptionMessage = "Failed to fetch records from database";

    when(uploadRangeIndexService.fetchRecordRange(event)).thenThrow(new RuntimeException(exceptionMessage));

    // Act & Assert
    assertThrows(ReindexException.class, () -> service.process(event));

    verify(uploadRangeIndexService).fetchRecordRange(event);
    verify(reindexStatusService).updateReindexUploadFailed(event.getEntityType());
  }

  @Test
  void process_shouldThrowReindexException_whenExceptionOccursDuringDocumentConversion() {
    // Arrange
    var event = reindexEvent();
    var resourceEvent = new ResourceEvent();
    var exceptionMessage = "Failed to convert documents";

    when(uploadRangeIndexService.fetchRecordRange(event)).thenReturn(List.of(resourceEvent));
    when(documentConverter.convert(List.of(resourceEvent))).thenThrow(new RuntimeException(exceptionMessage));

    // Act & Assert
    assertThrows(ReindexException.class, () -> service.process(event));

    verify(uploadRangeIndexService).fetchRecordRange(event);
    verify(documentConverter).convert(List.of(resourceEvent));
    verify(reindexStatusService).updateReindexUploadFailed(event.getEntityType());
  }

  @Test
  void process_shouldThrowReindexException_whenExceptionOccursDuringIndexing() {
    // Arrange
    var event = reindexEvent();
    var resourceEvent = new ResourceEvent();
    var exceptionMessage = "Failed to index documents in Elasticsearch";

    when(uploadRangeIndexService.fetchRecordRange(event)).thenReturn(List.of(resourceEvent));
    when(documentConverter.convert(List.of(resourceEvent))).thenReturn(Map.of("key", List.of(SearchDocumentBody.of(null,
      IndexingDataFormat.JSON, resourceEvent, IndexActionType.INDEX))));
    when(elasticRepository.indexResources(any())).thenThrow(new RuntimeException(exceptionMessage));

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
    event.setRecordType(ReindexRecordType.INSTANCE);
    event.setRecords(emptyList());

    service.process(event);

    verify(mergeRangeIndexService).saveEntities(event);
    verify(reindexStatusService).addProcessedMergeRanges(ReindexEntityType.INSTANCE, 1);
    verify(mergeRangeIndexService)
      .updateStatus(ReindexEntityType.INSTANCE, event.getRangeId(), ReindexRangeStatus.SUCCESS, null);
  }

  @Test
  void process_negative_reindexRecordsEvent_shouldFailMergeOnException() {
    var event = new ReindexRecordsEvent();
    event.setRangeId(UUID.randomUUID().toString());
    event.setRecordType(ReindexRecordType.INSTANCE);
    event.setRecords(emptyList());
    var failCause = "exception occurred";
    doThrow(new RuntimeException(failCause)).when(mergeRangeIndexService).saveEntities(event);

    service.process(event);

    verify(reindexStatusService).updateReindexMergeFailed(ReindexEntityType.INSTANCE);
    verify(mergeRangeIndexService)
      .updateStatus(ReindexEntityType.INSTANCE, event.getRangeId(), ReindexRangeStatus.FAIL, failCause);
    verifyNoMoreInteractions(reindexStatusService);
  }

  @Test
  void process_negative_reindexRecordsEvent_shouldNotFailMergeOnPessimisticLockingFailureException() {
    var event = new ReindexRecordsEvent();
    event.setRangeId(UUID.randomUUID().toString());
    event.setRecordType(ReindexRecordType.INSTANCE);
    event.setRecords(emptyList());
    doThrow(new PessimisticLockingFailureException("Deadlock")).when(mergeRangeIndexService).saveEntities(event);

    assertThrows(ReindexException.class, () -> service.process(event));

    verifyNoMoreInteractions(mergeRangeIndexService);
    verifyNoMoreInteractions(reindexStatusService);
  }

  @Test
  void process_positive_reindexFileReadyEvent() {
    var rangeId = UUID.randomUUID().toString();
    var event = new ReindexFileReadyEvent("diku", ReindexRecordType.INSTANCE, rangeId, UUID.randomUUID().toString(),
      "bucket", "object-key", "2026-03-02T00:00:00.000Z");
    var line = "{\"id\":\"ddc29cbf-f2f5-4f6a-9411-359d6274478e\"}";
    var record = Map.<String, Object>of("id", "ddc29cbf-f2f5-4f6a-9411-359d6274478e");
    service.setFolioS3Client(folioS3Client);
    when(folioS3Client.read(event.getObjectKey())).thenReturn(new ByteArrayInputStream((line + "\n").getBytes(UTF_8)));
    when(jsonConverter.fromJsonToMap(line)).thenReturn(record);

    service.process(event);

    var eventCaptor = ArgumentCaptor.forClass(ReindexRecordsEvent.class);
    verify(mergeRangeIndexService).saveEntities(eventCaptor.capture());
    assertEquals(List.of(record), eventCaptor.getValue().getRecords());
    verify(reindexStatusService).addProcessedMergeRanges(ReindexEntityType.INSTANCE, 1);
    verify(mergeRangeIndexService).updateStatus(ReindexEntityType.INSTANCE, rangeId, ReindexRangeStatus.SUCCESS, null);
  }

  private ReindexRangeIndexEvent reindexEvent() {
    var event = new ReindexRangeIndexEvent();
    event.setId(UUID.randomUUID());
    event.setEntityType(ReindexEntityType.INSTANCE);
    return event;
  }
}
