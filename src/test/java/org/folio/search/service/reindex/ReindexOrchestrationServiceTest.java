package org.folio.search.service.reindex;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static org.folio.search.model.event.ReindexRecordType.INSTANCE;
import static org.folio.search.model.types.ReindexRangeStatus.FAIL;
import static org.folio.search.model.types.ReindexRangeStatus.SUCCESS;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
import org.folio.spring.FolioExecutionContext;
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
  private ReindexUploadRangeIndexService uploadRangeService;
  @Mock
  private ReindexMergeRangeIndexService mergeRangeService;
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
  @Mock
  private ReindexService reindexService;
  @Mock
  private FolioExecutionContext context;

  @InjectMocks
  private ReindexOrchestrationService service;

  @Test
  void process_shouldProcessSuccessfully() {
    // Arrange
    var event = reindexEvent();
    var resourceEvent = new ResourceEvent();
    var folioIndexOperationResponse = new FolioIndexOperationResponse()
      .status(FolioIndexOperationResponse.StatusEnum.SUCCESS);

    when(uploadRangeService.fetchRecordRange(event)).thenReturn(List.of(resourceEvent));
    when(documentConverter.convertForReindex(List.of(resourceEvent))).thenReturn(List.of(SearchDocumentBody.of(null,
      IndexingDataFormat.JSON, resourceEvent, IndexActionType.INDEX)));
    when(elasticRepository.indexResources(any())).thenReturn(folioIndexOperationResponse);

    // Act
    boolean result = service.process(event);

    // Assert
    assertTrue(result);
    verify(uploadRangeService).fetchRecordRange(event);
    verify(documentConverter).convertForReindex(List.of(resourceEvent));
    verify(elasticRepository).indexResources(any());
    verify(uploadRangeService).updateStatus(event, ReindexRangeStatus.SUCCESS, null);
    verify(reindexStatusService).addProcessedUploadRanges(event.getEntityType(), 1);
  }

  @Test
  void process_shouldThrowReindexException_whenElasticSearchReportsError() {
    // Arrange
    var event = reindexEvent();
    var resourceEvent = new ResourceEvent();
    var errorMessage = "Error occurred during indexing.";
    var folioIndexOperationResponse = new FolioIndexOperationResponse()
      .status(FolioIndexOperationResponse.StatusEnum.ERROR)
      .errorMessage(errorMessage);

    when(uploadRangeService.fetchRecordRange(event)).thenReturn(List.of(resourceEvent));
    when(documentConverter.convertForReindex(List.of(resourceEvent))).thenReturn(List.of(SearchDocumentBody.of(null,
      IndexingDataFormat.JSON, resourceEvent, IndexActionType.INDEX)));
    when(elasticRepository.indexResources(any())).thenReturn(folioIndexOperationResponse);

    // Act & Assert
    assertThrows(ReindexException.class, () -> service.process(event));

    verify(uploadRangeService).fetchRecordRange(event);
    verify(documentConverter).convertForReindex(List.of(resourceEvent));
    verify(elasticRepository).indexResources(any());
    verify(uploadRangeService).updateStatus(event, ReindexRangeStatus.FAIL, errorMessage);
    verify(reindexStatusService).updateReindexUploadFailed(event.getEntityType());
  }

  @Test
  void process_shouldThrowReindexException_whenExceptionOccursDuringFetchRecords() {
    // Arrange
    var event = reindexEvent();
    var exceptionMessage = "Failed to fetch records from database";

    when(uploadRangeService.fetchRecordRange(event)).thenThrow(new RuntimeException(exceptionMessage));

    // Act & Assert
    assertThrows(ReindexException.class, () -> service.process(event));

    verify(uploadRangeService).fetchRecordRange(event);
    verify(uploadRangeService).updateStatus(event, ReindexRangeStatus.FAIL, exceptionMessage);
    verify(reindexStatusService).updateReindexUploadFailed(event.getEntityType());
  }

  @Test
  void process_shouldThrowReindexException_whenExceptionOccursDuringDocumentConversion() {
    // Arrange
    var event = reindexEvent();
    var resourceEvent = new ResourceEvent();
    var exceptionMessage = "Failed to convert documents";

    when(uploadRangeService.fetchRecordRange(event)).thenReturn(List.of(resourceEvent));
    when(documentConverter.convertForReindex(List.of(resourceEvent))).thenThrow(new RuntimeException(exceptionMessage));

    // Act & Assert
    assertThrows(ReindexException.class, () -> service.process(event));

    verify(uploadRangeService).fetchRecordRange(event);
    verify(documentConverter).convertForReindex(List.of(resourceEvent));
    verify(uploadRangeService).updateStatus(event, ReindexRangeStatus.FAIL, exceptionMessage);
    verify(reindexStatusService).updateReindexUploadFailed(event.getEntityType());
  }

  @Test
  void process_shouldThrowReindexException_whenExceptionOccursDuringIndexing() {
    // Arrange
    var event = reindexEvent();
    var resourceEvent = new ResourceEvent();
    var exceptionMessage = "Failed to index documents in Elasticsearch";

    when(uploadRangeService.fetchRecordRange(event)).thenReturn(List.of(resourceEvent));
    when(documentConverter.convertForReindex(List.of(resourceEvent))).thenReturn(List.of(SearchDocumentBody.of(null,
      IndexingDataFormat.JSON, resourceEvent, IndexActionType.INDEX)));
    when(elasticRepository.indexResources(any())).thenThrow(new RuntimeException(exceptionMessage));

    // Act & Assert
    assertThrows(ReindexException.class, () -> service.process(event));

    verify(uploadRangeService).fetchRecordRange(event);
    verify(documentConverter).convertForReindex(List.of(resourceEvent));
    verify(elasticRepository).indexResources(any());
    verify(uploadRangeService).updateStatus(event, ReindexRangeStatus.FAIL, exceptionMessage);
    verify(reindexStatusService).updateReindexUploadFailed(event.getEntityType());
  }

  @Test
  void process_positive_reindexRecordsEvent() {
    var event = new ReindexRecordsEvent();
    event.setRangeId(UUID.randomUUID().toString());
    event.setRecordType(INSTANCE);
    event.setRecords(emptyList());

    when(reindexStatusService.getTargetTenantId()).thenReturn(MEMBER_TENANT_ID);
    when(reindexStatusService.isMergeCompleted()).thenReturn(false);

    service.process(event);

    verify(reindexStatusService).getTargetTenantId();
    verify(mergeRangeService).saveEntities(event);
    verify(reindexStatusService).addProcessedMergeRanges(ReindexEntityType.INSTANCE, 1);
    verify(mergeRangeService)
      .updateStatus(ReindexEntityType.INSTANCE, event.getRangeId(), SUCCESS, null);
    verify(reindexStatusService).isMergeCompleted();
  }

  @Test
  void process_negative_reindexRecordsEvent_shouldFailMergeOnException() {
    var event = new ReindexRecordsEvent();
    event.setRangeId(UUID.randomUUID().toString());
    event.setRecordType(INSTANCE);
    event.setRecords(emptyList());
    var failCause = "exception occurred";
    doThrow(new RuntimeException(failCause)).when(mergeRangeService).saveEntities(event);

    service.process(event);

    verify(reindexStatusService).getTargetTenantId();
    verify(reindexStatusService).updateReindexMergeFailed(ReindexEntityType.INSTANCE);
    verify(mergeRangeService)
      .updateStatus(ReindexEntityType.INSTANCE, event.getRangeId(), FAIL, failCause);
    verifyNoMoreInteractions(reindexStatusService);
  }

  @Test
  void process_negative_reindexRecordsEvent_shouldNotFailMergeOnPessimisticLockingFailureException() {
    var event = new ReindexRecordsEvent();
    event.setRangeId(UUID.randomUUID().toString());
    event.setRecordType(INSTANCE);
    event.setRecords(emptyList());
    doThrow(new PessimisticLockingFailureException("Deadlock")).when(mergeRangeService).saveEntities(event);

    assertThrows(ReindexException.class, () -> service.process(event));

    verifyNoMoreInteractions(mergeRangeService);
    verify(reindexStatusService).getTargetTenantId();
    verifyNoMoreInteractions(reindexStatusService);
  }

  @Test
  void process_positive_reindexFileReadyEvent() {
    service.setFolioS3Client(folioS3Client);
    var rangeId = UUID.randomUUID().toString();
    var event = getReindexFileReadyEvent(rangeId);
    var line = "{\"id\":\"ddc29cbf-f2f5-4f6a-9411-359d6274478e\"}";
    var inventoryRecord = Map.<String, Object>of("id", "ddc29cbf-f2f5-4f6a-9411-359d6274478e");
    when(folioS3Client.read(event.getObjectKey())).thenReturn(new ByteArrayInputStream((line + "\n").getBytes(UTF_8)));
    when(jsonConverter.fromJsonToMap(line)).thenReturn(inventoryRecord);

    service.process(event);

    var eventCaptor = ArgumentCaptor.forClass(ReindexRecordsEvent.class);
    verify(mergeRangeService).saveEntities(eventCaptor.capture());
    assertEquals(List.of(inventoryRecord), eventCaptor.getValue().getRecords());
    verify(reindexStatusService).addProcessedMergeRanges(ReindexEntityType.INSTANCE, 1);
    verify(mergeRangeService).updateStatus(ReindexEntityType.INSTANCE, rangeId, SUCCESS, null);
  }

  @Test
  void process_positive_reindexFileReadyEvent_shouldSkipBlankLines() {
    service.setFolioS3Client(folioS3Client);
    var rangeId = UUID.randomUUID().toString();
    var event = getReindexFileReadyEvent(rangeId);
    var line1 = "{\"id\":\"ddc29cbf-f2f5-4f6a-9411-359d6274478e\"}";
    var line2 = "{\"id\":\"23be9716-2935-4e8c-9931-f904eb10d6ce\"}";
    var record1 = Map.<String, Object>of("id", "ddc29cbf-f2f5-4f6a-9411-359d6274478e");
    var record2 = Map.<String, Object>of("id", "23be9716-2935-4e8c-9931-f904eb10d6ce");
    when(folioS3Client.read(event.getObjectKey()))
      .thenReturn(new ByteArrayInputStream((line1 + "\n\n" + line2 + "\n").getBytes(UTF_8)));
    when(jsonConverter.fromJsonToMap(line1)).thenReturn(record1);
    when(jsonConverter.fromJsonToMap(line2)).thenReturn(record2);

    service.process(event);

    var eventCaptor = ArgumentCaptor.forClass(ReindexRecordsEvent.class);
    verify(mergeRangeService).saveEntities(eventCaptor.capture());
    assertEquals(List.of(record1, record2), eventCaptor.getValue().getRecords());
    verify(reindexStatusService).addProcessedMergeRanges(ReindexEntityType.INSTANCE, 1);
    verify(mergeRangeService).updateStatus(ReindexEntityType.INSTANCE, rangeId, SUCCESS, null);
  }

  @Test
  void process_negative_reindexFileReadyEvent_shouldFailMergeOnException() {
    service.setFolioS3Client(folioS3Client);
    var rangeId = UUID.randomUUID().toString();
    var event = getReindexFileReadyEvent(rangeId);
    var line = "{\"id\":\"ddc29cbf-f2f5-4f6a-9411-359d6274478e\"}";
    var inventoryRecord = Map.<String, Object>of("id", "ddc29cbf-f2f5-4f6a-9411-359d6274478e");
    var failCause = "exception occurred";
    when(folioS3Client.read(event.getObjectKey())).thenReturn(new ByteArrayInputStream((line + "\n").getBytes(UTF_8)));
    when(jsonConverter.fromJsonToMap(line)).thenReturn(inventoryRecord);
    doThrow(new RuntimeException(failCause)).when(mergeRangeService).saveEntities(any(ReindexRecordsEvent.class));

    var result = service.process(event);

    assertTrue(result);
    verify(reindexStatusService).updateReindexMergeFailed(ReindexEntityType.INSTANCE);
    verify(mergeRangeService).updateStatus(ReindexEntityType.INSTANCE, rangeId, FAIL, failCause);
    verifyNoMoreInteractions(reindexStatusService);
  }

  @Test
  void process_negative_reindexFileReadyEvent_shouldThrowReindexExceptionOnPessimisticLockingFailureException() {
    service.setFolioS3Client(folioS3Client);
    var rangeId = UUID.randomUUID().toString();
    var event = getReindexFileReadyEvent(rangeId);
    var line = "{\"id\":\"ddc29cbf-f2f5-4f6a-9411-359d6274478e\"}";
    var inventoryRecord = Map.<String, Object>of("id", "ddc29cbf-f2f5-4f6a-9411-359d6274478e");
    when(folioS3Client.read(event.getObjectKey())).thenReturn(new ByteArrayInputStream((line + "\n").getBytes(UTF_8)));
    when(jsonConverter.fromJsonToMap(line)).thenReturn(inventoryRecord);
    doThrow(new PessimisticLockingFailureException("Deadlock"))
      .when(mergeRangeService).saveEntities(any(ReindexRecordsEvent.class));

    assertThrows(ReindexException.class, () -> service.process(event));

    verifyNoMoreInteractions(reindexStatusService);
  }

  @Test
  void process_reindexRecordsEvent_shouldSkipStagingAndSubmitUploadWhenMergeCompletedForFullReindex() {
    // given
    var event = new ReindexRecordsEvent();
    event.setRangeId(UUID.randomUUID().toString());
    event.setRecordType(ReindexRecordType.INSTANCE);
    event.setRecords(emptyList());

    when(reindexStatusService.isMergeCompleted()).thenReturn(true);
    when(reindexStatusService.getTargetTenantId()).thenReturn(null);
    when(context.getTenantId()).thenReturn("test-tenant");

    // act
    service.process(event);

    // assert
    verify(mergeRangeService).saveEntities(event);
    verify(reindexStatusService).isMergeCompleted();
    // For full reindex (null targetTenantId): no staging migration
    verify(mergeRangeService, never()).performStagingMigration(any());
    verify(reindexService).submitUploadReindex("test-tenant", ReindexEntityType.supportUploadTypes());
  }

  @Test
  void process_reindexRecordsEvent_shouldNotTriggerUploadWhenMergeNotCompleted() {
    // given
    var event = new ReindexRecordsEvent();
    event.setRangeId(UUID.randomUUID().toString());
    event.setRecordType(ReindexRecordType.INSTANCE);
    event.setRecords(emptyList());

    when(reindexStatusService.isMergeCompleted()).thenReturn(false);

    // act
    service.process(event);

    // assert
    verify(mergeRangeService).saveEntities(event);
    verify(reindexStatusService).isMergeCompleted();
    verify(mergeRangeService, never()).performStagingMigration(any());
  }

  @Test
  void process_reindexRangeIndexEvent_shouldHandleMemberTenantContext() {
    // given
    var event = reindexEvent();
    event.setMemberTenantId(MEMBER_TENANT_ID);
    var resourceEvent = new ResourceEvent();
    var folioIndexOperationResponse = new FolioIndexOperationResponse()
      .status(FolioIndexOperationResponse.StatusEnum.SUCCESS);

    when(uploadRangeService.fetchRecordRange(event)).thenReturn(List.of(resourceEvent));
    when(documentConverter.convertForReindex(List.of(resourceEvent))).thenReturn(List.of(SearchDocumentBody.of(null,
      IndexingDataFormat.JSON, resourceEvent, IndexActionType.INDEX)));
    when(elasticRepository.indexResources(any())).thenReturn(folioIndexOperationResponse);

    // act
    service.process(event);

    // assert
    verify(uploadRangeService).fetchRecordRange(event);
    verify(documentConverter).convertForReindex(List.of(resourceEvent));
    verify(elasticRepository).indexResources(any());
    verify(uploadRangeService).updateStatus(event, ReindexRangeStatus.SUCCESS, null);
    verify(reindexStatusService).addProcessedUploadRanges(event.getEntityType(), 1);
  }

  @Test
  void process_reindexRecordsEvent_shouldRunStagingMigrationAndSubmitUploadWhenMergeCompletedForMemberTenant() {
    var event = new ReindexRecordsEvent();
    event.setRangeId(UUID.randomUUID().toString());
    event.setRecordType(ReindexRecordType.INSTANCE);
    event.setRecords(emptyList());

    when(reindexStatusService.isMergeCompleted()).thenReturn(true);
    when(reindexStatusService.getTargetTenantId()).thenReturn(MEMBER_TENANT_ID);
    when(context.getTenantId()).thenReturn("central-tenant");

    service.process(event);

    verify(mergeRangeService).saveEntities(event);
    verify(reindexStatusService).isMergeCompleted();
    verify(reindexStatusService).updateStagingStarted();
    verify(mergeRangeService).performStagingMigration(MEMBER_TENANT_ID);
    verify(reindexStatusService).updateStagingCompleted();
    verify(reindexService).submitUploadReindexWithTenantCleanup(
      "central-tenant", ReindexEntityType.supportUploadTypes(), MEMBER_TENANT_ID);
  }

  @Test
  void process_reindexRecordsEvent_shouldFailAndThrowWhenStagingMigrationFails() {
    var event = new ReindexRecordsEvent();
    event.setRangeId(UUID.randomUUID().toString());
    event.setRecordType(ReindexRecordType.INSTANCE);
    event.setRecords(emptyList());
    var migrationError = "DB connection lost";

    when(reindexStatusService.isMergeCompleted()).thenReturn(true);
    when(reindexStatusService.getTargetTenantId()).thenReturn(MEMBER_TENANT_ID);
    doThrow(new RuntimeException(migrationError)).when(mergeRangeService).performStagingMigration(MEMBER_TENANT_ID);

    assertThrows(ReindexException.class, () -> service.process(event));

    verify(reindexStatusService).updateStagingStarted();
    verify(mergeRangeService).performStagingMigration(MEMBER_TENANT_ID);
    verify(reindexStatusService).updateStagingFailed();
    verify(reindexStatusService, never()).updateStagingCompleted();
    verify(reindexService, never()).submitUploadReindexWithTenantCleanup(any(), any(), any());
  }

  private ReindexFileReadyEvent getReindexFileReadyEvent(String rangeId) {
    return new ReindexFileReadyEvent("diku", INSTANCE, rangeId, UUID.randomUUID().toString(),
      "bucket", "object-key", "2026-03-02T00:00:00.000Z");
  }

  private ReindexRangeIndexEvent reindexEvent() {
    var event = new ReindexRangeIndexEvent();
    event.setId(UUID.randomUUID());
    event.setEntityType(ReindexEntityType.INSTANCE);
    return event;
  }
}
