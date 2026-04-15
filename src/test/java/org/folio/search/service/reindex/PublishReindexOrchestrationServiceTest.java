package org.folio.search.service.reindex;

import static java.util.Collections.emptyList;
import static org.folio.search.model.event.ReindexRecordType.INSTANCE;
import static org.folio.search.model.types.ReindexRangeStatus.FAIL;
import static org.folio.search.model.types.ReindexRangeStatus.SUCCESS;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.folio.search.exception.ReindexException;
import org.folio.search.model.event.ReindexRecordType;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PublishReindexOrchestrationServiceTest {

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
  private ReindexService reindexService;
  @Mock
  private FolioExecutionContext context;

  @InjectMocks
  private PublishReindexOrchestrationService service;

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
    verify(mergeRangeService).updateStatus(ReindexEntityType.INSTANCE, event.getRangeId(), SUCCESS, null);
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
    verify(mergeRangeService).updateStatus(ReindexEntityType.INSTANCE, event.getRangeId(), FAIL, failCause);
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
  void process_reindexRecordsEvent_shouldSkipStagingAndSubmitUploadWhenMergeCompletedForFullReindex() {
    var event = new ReindexRecordsEvent();
    event.setRangeId(UUID.randomUUID().toString());
    event.setRecordType(ReindexRecordType.INSTANCE);
    event.setRecords(emptyList());

    when(reindexStatusService.isMergeCompleted()).thenReturn(true);
    when(reindexStatusService.getTargetTenantId()).thenReturn(null);
    when(context.getTenantId()).thenReturn("test-tenant");

    service.process(event);

    verify(mergeRangeService).saveEntities(event);
    verify(reindexStatusService).isMergeCompleted();
    verify(mergeRangeService, never()).performStagingMigration(any());
    verify(reindexService).submitUploadReindex("test-tenant", ReindexEntityType.supportUploadTypes());
  }

  @Test
  void process_reindexRecordsEvent_shouldNotTriggerUploadWhenMergeNotCompleted() {
    var event = new ReindexRecordsEvent();
    event.setRangeId(UUID.randomUUID().toString());
    event.setRecordType(ReindexRecordType.INSTANCE);
    event.setRecords(emptyList());

    when(reindexStatusService.isMergeCompleted()).thenReturn(false);

    service.process(event);

    verify(mergeRangeService).saveEntities(event);
    verify(reindexStatusService).isMergeCompleted();
    verify(mergeRangeService, never()).performStagingMigration(any());
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
}
