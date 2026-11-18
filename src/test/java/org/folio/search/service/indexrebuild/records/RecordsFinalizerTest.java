package org.folio.search.service.indexrebuild.records;

import static org.folio.search.service.indexrebuild.IndexRebuildTarget.ITEM;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.search.service.indexrebuild.IndexFingerprintService;
import org.folio.search.service.indexrebuild.item.ItemRecordsRebuildStrategy;
import org.folio.search.service.indexrebuild.jdbc.DeletedRecordRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RecordsFinalizerTest {

  private static final RecordsRebuildStrategy STRATEGY = new ItemRecordsRebuildStrategy(null, null);

  @Mock
  private IndexRebuildStateRepository stateRepository;
  @Mock
  private DeletedRecordRepository deletedRecordRepository;
  @Mock
  private RecordsIndexWriter indexWriter;
  @Mock
  private IndexFingerprintService fingerprintService;
  @InjectMocks
  private RecordsFinalizer finalizer;

  @Test
  void tryFinalize_positive_removesDeletedRecordsBeforeCompleting() {
    when(stateRepository.tryStartFinalizing(ITEM, TENANT_ID)).thenReturn(true);
    when(deletedRecordRepository.findIds(ITEM, TENANT_ID)).thenReturn(List.of("item"));
    when(fingerprintService.current(ITEM)).thenReturn("fingerprint");

    finalizer.tryFinalize(STRATEGY, TENANT_ID);

    var order = inOrder(indexWriter, stateRepository, deletedRecordRepository);
    order.verify(indexWriter).delete(STRATEGY, TENANT_ID, List.of("item"));
    order.verify(indexWriter).refresh(STRATEGY, TENANT_ID);
    order.verify(stateRepository).complete(ITEM, TENANT_ID, "fingerprint");
    order.verify(deletedRecordRepository).deleteAll(ITEM, TENANT_ID);
  }

  @Test
  void tryFinalize_positive_doesNothingWhenNotTheWinner() {
    when(stateRepository.tryStartFinalizing(ITEM, TENANT_ID)).thenReturn(false);

    finalizer.tryFinalize(STRATEGY, TENANT_ID);

    verifyNoInteractions(indexWriter, deletedRecordRepository, fingerprintService);
  }

  @Test
  void tryFinalize_negative_failureKeepsDeletedRecordsForRetry() {
    when(stateRepository.tryStartFinalizing(ITEM, TENANT_ID)).thenReturn(true);
    when(deletedRecordRepository.findIds(ITEM, TENANT_ID)).thenReturn(List.of("item"));
    doThrow(new IllegalStateException("error")).when(indexWriter).delete(any(), any(), any());

    finalizer.tryFinalize(STRATEGY, TENANT_ID);

    verify(stateRepository).markFailed(eq(ITEM), eq(TENANT_ID), anyString());
    verify(stateRepository, never()).complete(any(), any(), any());
    verify(deletedRecordRepository, never()).deleteAll(any(), any());
  }
}
