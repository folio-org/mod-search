package org.folio.search.service.indexrebuild.derived;

import static org.folio.search.service.indexrebuild.IndexRebuildTarget.CALL_NUMBER;
import static org.folio.search.service.indexrebuild.IndexRebuildTarget.ITEM;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.service.indexrebuild.IndexFingerprintService;
import org.folio.search.service.indexrebuild.IndexRebuildReadinessService;
import org.folio.search.service.indexrebuild.jdbc.DirtyKeyRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.folio.search.service.indexrebuild.model.IndexRebuildState;
import org.folio.search.service.indexrebuild.model.IndexRebuildStatus;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DerivedRefreshServiceTest {

  private static final String FINGERPRINT = "current";
  private static final Timestamp STARTED_AT = Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"));
  private static final IndexRebuildState SOURCE_READY =
    new IndexRebuildState(TENANT_ID, IndexRebuildStatus.COMPLETED, FINGERPRINT, null, null, null);

  @Mock
  private DerivedRebuildStrategy strategy;
  @Mock
  private DirtyKeyRepository dirtyKeyRepository;
  @Mock
  private DerivedIndexReader reader;
  @Mock
  private DerivedDocumentWriter documentWriter;
  @Mock
  private IndexRebuildStateRepository stateRepository;
  @Mock
  private IndexRebuildReadinessService readinessService;
  @Mock
  private IndexFingerprintService fingerprintService;
  @Mock
  private TransactionTemplate transactionTemplate;
  private DerivedRefreshService service;

  @BeforeEach
  void setUp() {
    var configuration = new SearchConfigurationProperties();
    configuration.setIndexing(new SearchConfigurationProperties.IndexingSettings());
    configuration.getIndexing().setSubResourceBatchSize(10);
    lenient().when(strategy.target()).thenReturn(CALL_NUMBER);
    lenient().when(strategy.source()).thenReturn(ITEM);
    service = new DerivedRefreshService(new DerivedRebuildStrategies(List.of(strategy)), dirtyKeyRepository, reader,
      documentWriter, stateRepository, readinessService, fingerprintService, configuration, transactionTemplate);
    lenient().when(transactionTemplate.execute(any()))
      .thenAnswer(invocation -> invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
  }

  @Test
  void refresh_positive_rebuildsQueuedKeys() {
    mockEnabled();
    when(stateRepository.findAll(CALL_NUMBER)).thenReturn(List.of());
    when(readinessService.isReady(SOURCE_READY, FINGERPRINT)).thenReturn(true);
    when(dirtyKeyRepository.tryLock(CALL_NUMBER)).thenReturn(true);
    when(dirtyKeyRepository.currentTime()).thenReturn(STARTED_AT);
    when(dirtyKeyRepository.findBatch(CALL_NUMBER, List.of(TENANT_ID), 10)).thenReturn(List.of("key"));

    service.refresh(CALL_NUMBER, TENANT_ID);

    verify(reader).refresh(ITEM, TENANT_ID);
    verify(documentWriter).rebuild(strategy, TENANT_ID, List.of("key"));
    verify(dirtyKeyRepository).delete(CALL_NUMBER, List.of("key"), List.of(TENANT_ID), STARTED_AT);
  }

  @Test
  void refresh_positive_skipsWhenQueueIsLockedByAnotherInstance() {
    mockEnabled();
    when(stateRepository.findAll(CALL_NUMBER)).thenReturn(List.of());
    when(readinessService.isReady(SOURCE_READY, FINGERPRINT)).thenReturn(true);
    when(dirtyKeyRepository.tryLock(CALL_NUMBER)).thenReturn(false);

    service.refresh(CALL_NUMBER, TENANT_ID);

    verify(dirtyKeyRepository, never()).findBatch(any(), any(), anyInt());
    verifyNoInteractions(documentWriter);
  }

  @Test
  void refresh_positive_waitsWhileSourceIndexOfAnotherTenantIsNotReady() {
    var memberInProgress =
      new IndexRebuildState(MEMBER_TENANT_ID, IndexRebuildStatus.IN_PROGRESS, null, null, null, null);
    when(strategy.isEnabled()).thenReturn(true);
    when(fingerprintService.current(ITEM)).thenReturn(FINGERPRINT);
    when(stateRepository.findAll(ITEM)).thenReturn(List.of(SOURCE_READY, memberInProgress));
    when(readinessService.isReady(SOURCE_READY, FINGERPRINT)).thenReturn(true);
    when(readinessService.isReady(memberInProgress, FINGERPRINT)).thenReturn(false);

    service.refresh(CALL_NUMBER, TENANT_ID);

    verifyNoInteractions(dirtyKeyRepository, documentWriter);
  }

  @Test
  void refresh_positive_waitsWhileRunIsActive() {
    mockEnabled();
    when(stateRepository.findAll(CALL_NUMBER)).thenReturn(List.of(
      new IndexRebuildState(TENANT_ID, IndexRebuildStatus.IN_PROGRESS, null, null, null, null)));
    when(readinessService.isReady(SOURCE_READY, FINGERPRINT)).thenReturn(true);

    service.refresh(CALL_NUMBER, TENANT_ID);

    verifyNoInteractions(dirtyKeyRepository, documentWriter);
  }

  @Test
  void refresh_positive_waitsWhileSourceIndexIsNotReady() {
    mockEnabled();
    when(stateRepository.findAll(CALL_NUMBER)).thenReturn(List.of());
    when(readinessService.isReady(SOURCE_READY, FINGERPRINT)).thenReturn(false);

    service.refresh(CALL_NUMBER, TENANT_ID);

    verifyNoInteractions(dirtyKeyRepository, documentWriter);
  }

  @Test
  void refresh_positive_disabled() {
    when(strategy.isEnabled()).thenReturn(false);

    service.refresh(CALL_NUMBER, TENANT_ID);

    verify(stateRepository, never()).findAll(any());
  }

  private void mockEnabled() {
    when(strategy.isEnabled()).thenReturn(true);
    when(fingerprintService.current(ITEM)).thenReturn(FINGERPRINT);
    when(stateRepository.findAll(ITEM)).thenReturn(List.of(SOURCE_READY));
  }
}
