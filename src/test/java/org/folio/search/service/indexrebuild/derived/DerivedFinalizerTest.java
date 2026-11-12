package org.folio.search.service.indexrebuild.derived;

import static org.folio.search.service.indexrebuild.IndexRebuildTarget.CALL_NUMBER;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.folio.search.service.indexrebuild.IndexFingerprintService;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DerivedFinalizerTest {

  @Mock
  private IndexRebuildStateRepository stateRepository;
  @Mock
  private IndexFingerprintService fingerprintService;
  @Mock
  private DerivedIndexReader reader;
  @InjectMocks
  private DerivedFinalizer finalizer;

  @Test
  void tryFinalize_positive_refreshesIndexBeforeCompleting() {
    when(stateRepository.tryStartAllFinalizing(CALL_NUMBER)).thenReturn(true);
    when(fingerprintService.current(CALL_NUMBER)).thenReturn("fingerprint");

    finalizer.tryFinalize(CALL_NUMBER, TENANT_ID);

    var order = inOrder(reader, stateRepository);
    order.verify(reader).refresh(CALL_NUMBER, TENANT_ID);
    order.verify(stateRepository).completeAll(CALL_NUMBER, "fingerprint");
  }

  @Test
  void tryFinalize_positive_doesNothingWhenNotTheWinner() {
    when(stateRepository.tryStartAllFinalizing(CALL_NUMBER)).thenReturn(false);

    finalizer.tryFinalize(CALL_NUMBER, TENANT_ID);

    verifyNoInteractions(reader, fingerprintService);
  }
}
