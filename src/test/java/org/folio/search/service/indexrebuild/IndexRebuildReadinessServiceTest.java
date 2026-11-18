package org.folio.search.service.indexrebuild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.service.indexrebuild.IndexRebuildTarget.ITEM;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.search.exception.RequestValidationException;
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
class IndexRebuildReadinessServiceTest {

  private static final String CURRENT = "current";
  private static final String OTHER_TENANT = "other";

  @Mock
  private IndexRebuildStateRepository stateRepository;
  @Mock
  private IndexFingerprintService fingerprintService;
  @InjectMocks
  private IndexRebuildReadinessService service;

  @Test
  void validateCanStart_positive_wholeIndexIgnoresOutdatedIndex() {
    assertThatCode(() -> service.validateCanStart(ITEM, new IndexRebuildScope(List.of(TENANT_ID), true)))
      .doesNotThrowAnyException();
  }

  @Test
  void validateCanStart_positive_memberRerunOnCurrentIndex() {
    mockStates(state(OTHER_TENANT, IndexRebuildStatus.COMPLETED, CURRENT));

    assertThatCode(() -> service.validateCanStart(ITEM, new IndexRebuildScope(List.of(TENANT_ID), false)))
      .doesNotThrowAnyException();
  }

  @Test
  void validateCanStart_negative_memberRerunOnOutdatedIndex() {
    mockStates(state(OTHER_TENANT, IndexRebuildStatus.COMPLETED, "old"));
    var scope = new IndexRebuildScope(List.of(TENANT_ID), false);

    assertThatThrownBy(() -> service.validateCanStart(ITEM, scope))
      .isInstanceOf(RequestValidationException.class)
      .hasMessageContaining("outdated");
  }

  @Test
  void validateDependenciesReady_positive_dependencyCurrentForAllTenants() {
    mockStates(state(TENANT_ID, IndexRebuildStatus.COMPLETED, CURRENT));

    assertThatCode(() -> service.validateDependenciesReady(List.of(ITEM), List.of(TENANT_ID)))
      .doesNotThrowAnyException();
  }

  @Test
  void validateDependenciesReady_negative_dependencyNeverExecuted() {
    mockStates();

    assertDependencyBlocks();
  }

  @Test
  void validateDependenciesReady_negative_dependencyOutdated() {
    mockStates(state(TENANT_ID, IndexRebuildStatus.COMPLETED, "old"));

    assertDependencyBlocks();
  }

  @Test
  void validateDependenciesReady_negative_dependencyFailed() {
    mockStates(state(TENANT_ID, IndexRebuildStatus.FAILED, CURRENT));

    assertDependencyBlocks();
  }

  @Test
  void validateNoDependentRunActive_negative_dependentRunActive() {
    when(stateRepository.isAnyActive(IndexRebuildTarget.CALL_NUMBER)).thenReturn(true);

    assertThatThrownBy(() -> service.validateNoDependentRunActive(ITEM))
      .isInstanceOf(RequestValidationException.class);
  }

  @Test
  void validateCanResume_negative_dependencyNotReady() {
    var tenants = List.of(TENANT_ID);

    assertThatThrownBy(() -> service.validateCanResume(IndexRebuildTarget.CALL_NUMBER, tenants))
      .isInstanceOf(RequestValidationException.class)
      .hasMessageContaining("dependencies");
  }

  @Test
  void validateCanResume_negative_dependentRunActive() {
    when(stateRepository.isAnyActive(IndexRebuildTarget.CALL_NUMBER)).thenReturn(true);
    var tenants = List.of(TENANT_ID);

    assertThatThrownBy(() -> service.validateCanResume(ITEM, tenants))
      .isInstanceOf(RequestValidationException.class)
      .hasMessageContaining("dependent");
  }

  @Test
  void validateNoDependentRunActive_positive_noDependents() {
    assertThatCode(() -> service.validateNoDependentRunActive(IndexRebuildTarget.CALL_NUMBER))
      .doesNotThrowAnyException();
  }

  @Test
  void findBlockedBy_positive_returnsNotReadyDependencies() {
    mockStates();

    assertThat(service.findBlockedBy(List.of(ITEM), TENANT_ID)).containsExactly(ITEM);
  }

  private void assertDependencyBlocks() {
    var dependencies = List.of(ITEM);
    var tenants = List.of(TENANT_ID);

    assertThatThrownBy(() -> service.validateDependenciesReady(dependencies, tenants))
      .isInstanceOf(RequestValidationException.class);
  }

  private void mockStates(IndexRebuildState... states) {
    when(fingerprintService.current(ITEM)).thenReturn(CURRENT);
    when(stateRepository.findAll(ITEM)).thenReturn(List.of(states));
  }

  private static IndexRebuildState state(String tenant, IndexRebuildStatus status, String fingerprint) {
    return new IndexRebuildState(tenant, status, fingerprint, null, null, null);
  }
}
