package org.folio.search.service.indexrebuild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.service.indexrebuild.IndexRebuildTarget.CALL_NUMBER;
import static org.folio.search.service.indexrebuild.IndexRebuildTarget.ITEM;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildRangeRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.folio.search.service.indexrebuild.model.IndexRebuildState;
import org.folio.search.service.indexrebuild.model.IndexRebuildStatus;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IndexRebuildStatusReporterTest {

  @Mock
  private IndexRebuildStateRepository stateRepository;
  @Mock
  private IndexRebuildRangeRepository rangeRepository;
  @Mock
  private IndexFingerprintService fingerprintService;
  @Mock
  private IndexRebuildReadinessService readinessService;
  @InjectMocks
  private IndexRebuildStatusReporter reporter;

  @Test
  void getStatus_positive_reportsMissingTenantAsRequired() {
    when(stateRepository.findAll(ITEM)).thenReturn(List.of());
    when(rangeRepository.countByTenant(ITEM)).thenReturn(Map.of());
    when(fingerprintService.current(ITEM)).thenReturn("current");

    assertThat(reporter.getStatus(ITEM, List.of(TENANT_ID))).singleElement().satisfies(item -> {
      assertThat(item.getTenantId()).isEqualTo(TENANT_ID);
      assertThat(item.getStatus()).isEqualTo("NOT_STARTED");
      assertThat(item.getReindexRequired()).isTrue();
    });
  }

  @Test
  void getStatus_positive_reportsCompletedRunAndRanges() {
    var state = new IndexRebuildState(TENANT_ID, IndexRebuildStatus.COMPLETED, "current", null, null, null);
    when(stateRepository.findAll(ITEM)).thenReturn(List.of(state));
    when(rangeRepository.countByTenant(ITEM))
      .thenReturn(Map.of(TENANT_ID, new IndexRebuildRangeRepository.RangeCounts(2, 2)));
    when(fingerprintService.current(ITEM)).thenReturn("current");
    when(readinessService.isReady(state, "current")).thenReturn(true);

    assertThat(reporter.getStatus(ITEM, List.of(TENANT_ID))).singleElement().satisfies(item -> {
      assertThat(item.getStatus()).isEqualTo("COMPLETED");
      assertThat(item.getReindexRequired()).isFalse();
      assertThat(item.getTotalRanges()).isEqualTo(2);
      assertThat(item.getProcessedRanges()).isEqualTo(2);
    });
  }

  @Test
  void getStatus_positive_reportsBlockingDependency() {
    when(stateRepository.findAll(CALL_NUMBER)).thenReturn(List.of());
    when(rangeRepository.countByTenant(CALL_NUMBER)).thenReturn(Map.of());
    when(fingerprintService.current(CALL_NUMBER)).thenReturn("current");
    when(readinessService.findBlockedBy(List.of(ITEM), TENANT_ID)).thenReturn(List.of(ITEM));

    assertThat(reporter.getStatus(CALL_NUMBER, List.of(TENANT_ID))).singleElement()
      .satisfies(item -> assertThat(item.getBlockedBy()).containsExactly("item"));
  }
}
