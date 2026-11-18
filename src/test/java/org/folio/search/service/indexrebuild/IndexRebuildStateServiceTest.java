package org.folio.search.service.indexrebuild;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.service.indexrebuild.IndexRebuildTarget.CALL_NUMBER;
import static org.folio.search.service.indexrebuild.IndexRebuildTarget.ITEM;
import static org.folio.search.service.indexrebuild.model.IndexRebuildRunMode.PER_TENANT;
import static org.folio.search.service.indexrebuild.model.IndexRebuildRunMode.WHOLE_INDEX;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.service.indexrebuild.jdbc.DeletedRecordRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildRangeRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IndexRebuildStateServiceTest {

  private static final List<String> TENANTS = List.of(TENANT_ID, MEMBER_TENANT_ID);

  @Mock
  private IndexRebuildStateRepository stateRepository;
  @Mock
  private IndexRebuildRangeRepository rangeRepository;
  @Mock
  private DeletedRecordRepository deletedRecordRepository;
  @InjectMocks
  private IndexRebuildStateService service;

  @Test
  void start_positive_perTenantRemovesRangesAndTrackedDeletesOfTenants() {
    when(stateRepository.claim(ITEM, TENANT_ID)).thenReturn(true);

    service.start(ITEM, List.of(TENANT_ID), PER_TENANT);

    verify(rangeRepository).delete(ITEM, TENANT_ID);
    verify(rangeRepository, never()).deleteAll(any());
    verify(deletedRecordRepository).deleteAll(ITEM, TENANT_ID);
    verify(stateRepository, never()).isAnyActive(any());
  }

  @Test
  void start_positive_wholeIndexRemovesAllRangesOfTarget() {
    when(stateRepository.claim(CALL_NUMBER, TENANT_ID)).thenReturn(true);
    when(stateRepository.claim(CALL_NUMBER, MEMBER_TENANT_ID)).thenReturn(true);

    service.start(CALL_NUMBER, TENANTS, WHOLE_INDEX);

    verify(rangeRepository).deleteAll(CALL_NUMBER);
    verify(rangeRepository, never()).delete(any(), any());
  }

  @Test
  void start_positive_otherTargetKeepsTrackedDeletes() {
    when(stateRepository.claim(CALL_NUMBER, TENANT_ID)).thenReturn(true);

    service.start(CALL_NUMBER, List.of(TENANT_ID), PER_TENANT);

    verifyNoInteractions(deletedRecordRepository);
  }

  @Test
  void start_negative_perTenantRunAlreadyActiveForTenant() {
    when(stateRepository.claim(ITEM, TENANT_ID)).thenReturn(false);

    assertThatThrownBy(() -> service.start(ITEM, TENANTS, PER_TENANT))
      .isInstanceOf(RequestValidationException.class)
      .hasMessageContaining("already in progress");
    verify(rangeRepository, never()).delete(any(), any());
  }

  @Test
  void start_negative_wholeIndexAnyRunActive() {
    when(stateRepository.isAnyActive(CALL_NUMBER)).thenReturn(true);

    assertThatThrownBy(() -> service.start(CALL_NUMBER, TENANTS, WHOLE_INDEX))
      .isInstanceOf(RequestValidationException.class);
    verify(stateRepository, never()).claim(any(), any());
  }

  @Test
  void start_negative_wholeIndexConcurrentStartWonClaim() {
    when(stateRepository.claim(CALL_NUMBER, TENANT_ID)).thenReturn(false);

    assertThatThrownBy(() -> service.start(CALL_NUMBER, TENANTS, WHOLE_INDEX))
      .isInstanceOf(RequestValidationException.class)
      .hasMessageContaining("already in progress");
    verify(rangeRepository, never()).deleteAll(any());
  }
}
