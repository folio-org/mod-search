package org.folio.search.service.indexrebuild.derived;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.service.indexrebuild.IndexRebuildTarget.CALL_NUMBER;
import static org.folio.search.service.indexrebuild.IndexRebuildTarget.ITEM;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.exception.ReindexException;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.event.IndexRebuildRangeEvent;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.IndexService;
import org.folio.search.service.indexrebuild.IndexRebuildReadinessService;
import org.folio.search.service.indexrebuild.IndexRebuildScope;
import org.folio.search.service.indexrebuild.IndexRebuildScopeResolver;
import org.folio.search.service.indexrebuild.IndexRebuildStateService;
import org.folio.search.service.indexrebuild.IndexRebuildStatusReporter;
import org.folio.search.service.indexrebuild.callnumber.CallNumberRebuildStrategy;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildRangeRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.folio.search.service.indexrebuild.model.IndexRebuildRange;
import org.folio.search.service.indexrebuild.model.IndexRebuildRangeStatus;
import org.folio.search.service.indexrebuild.model.IndexRebuildRunMode;
import org.folio.spring.testing.type.UnitTest;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DerivedReindexServiceTest {

  private static final IndexRebuildScope WHOLE = new IndexRebuildScope(List.of(TENANT_ID), true);

  @Mock
  private IndexRebuildScopeResolver scopeResolver;
  @Mock
  private IndexRebuildReadinessService readinessService;
  @Mock
  private IndexRebuildStateService stateService;
  @Mock
  private IndexRebuildStateRepository stateRepository;
  @Mock
  private IndexRebuildRangeRepository rangeRepository;
  @Mock
  private IndexRebuildStatusReporter statusReporter;
  @Mock
  private DerivedFinalizer finalizer;
  @Mock
  private DerivedIndexReader reader;
  @Mock
  private IndexService indexService;
  @Mock
  private FeatureConfigService featureConfigService;
  @Mock
  private FolioMessageProducer<IndexRebuildRangeEvent> producer;
  @Mock
  private ExecutorService executor;
  @Captor
  private ArgumentCaptor<List<IndexRebuildRange>> rangesCaptor;
  @Captor
  private ArgumentCaptor<List<IndexRebuildRangeEvent>> eventsCaptor;
  private DerivedReindexService service;

  @BeforeEach
  void setUp() {
    var reindexConfig = new ReindexConfigurationProperties();
    reindexConfig.setUploadRangeLevel(2);
    var strategy = new CallNumberRebuildStrategy(featureConfigService, null);
    service = new DerivedReindexService(new DerivedRebuildStrategies(List.of(strategy)), scopeResolver,
      readinessService, stateService, stateRepository, rangeRepository, statusReporter, finalizer, reader,
      indexService, reindexConfig, producer, executor);
    lenient().doAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    }).when(executor).execute(any());
    lenient().when(featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CALL_NUMBERS)).thenReturn(true);
  }

  @Test
  void start_positive_recreatesIndexAndCreatesRangesOverWholeKeySpace() {
    when(scopeResolver.resolve(TENANT_ID, null)).thenReturn(WHOLE);
    when(indexService.createIndex(ResourceType.INSTANCE_CALL_NUMBER, TENANT_ID, null))
      .thenReturn(new FolioCreateIndexResponse());

    service.start(CALL_NUMBER, TENANT_ID, null, null);

    verify(readinessService).validateCanStart(CALL_NUMBER, WHOLE);
    verify(stateService).start(CALL_NUMBER, WHOLE.tenants(), IndexRebuildRunMode.WHOLE_INDEX);
    verify(reader).refresh(ITEM, TENANT_ID);
    verify(indexService).dropIndex(ResourceType.INSTANCE_CALL_NUMBER, TENANT_ID);
    verify(rangeRepository).saveAll(rangesCaptor.capture());
    assertThat(rangesCaptor.getValue()).hasSize(256).allMatch(range -> range.scopeTenantId() == null);
    verify(producer).sendMessages(eventsCaptor.capture());
    assertThat(eventsCaptor.getValue()).hasSize(256)
      .allMatch(event -> CALL_NUMBER.getResource().equals(event.getResource()));
  }

  @Test
  void start_positive_singleTenantKeepsIndexAndScopesRanges() {
    var scope = new IndexRebuildScope(List.of(MEMBER_TENANT_ID), false);
    when(scopeResolver.resolve(TENANT_ID, MEMBER_TENANT_ID)).thenReturn(scope);

    service.start(CALL_NUMBER, TENANT_ID, MEMBER_TENANT_ID, null);

    verify(indexService, never()).dropIndex(any(), any());
    verify(rangeRepository).saveAll(rangesCaptor.capture());
    assertThat(rangesCaptor.getValue()).hasSize(256)
      .allMatch(range -> MEMBER_TENANT_ID.equals(range.scopeTenantId()));
  }

  @Test
  void start_negative_featureDisabled() {
    when(featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CALL_NUMBERS)).thenReturn(false);

    assertThatThrownBy(() -> service.start(CALL_NUMBER, TENANT_ID, null, null))
      .isInstanceOf(RequestValidationException.class);

    verifyNoInteractions(stateService);
  }

  @Test
  void start_negative_targetNotDerived() {
    assertThatThrownBy(() -> service.start(ITEM, TENANT_ID, null, null)).isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(stateService);
  }

  @Test
  void start_negative_indexCannotBeRecreated() {
    when(scopeResolver.resolve(TENANT_ID, null)).thenReturn(WHOLE);
    doThrow(new IllegalStateException("error")).when(indexService)
      .dropIndex(ResourceType.INSTANCE_CALL_NUMBER, TENANT_ID);

    assertThatThrownBy(() -> service.start(CALL_NUMBER, TENANT_ID, null, null))
      .isInstanceOf(ReindexException.class);

    verify(stateRepository).markAllFailed(eq(CALL_NUMBER), anyString());
    verifyNoInteractions(producer);
  }

  @Test
  void retryFailed_positive_resendsUnfinishedRanges() {
    var range = new IndexRebuildRange(UUID.randomUUID(), UUID.randomUUID(), CALL_NUMBER, TENANT_ID, "0", "1",
      IndexRebuildRangeStatus.PENDING, null);
    when(scopeResolver.resolve(TENANT_ID, null)).thenReturn(WHOLE);
    when(stateRepository.prepareAllRetry(CALL_NUMBER)).thenReturn(true);
    when(rangeRepository.findAllUnfinished(CALL_NUMBER)).thenReturn(new ArrayList<>(List.of(range)));

    service.retryFailed(CALL_NUMBER, TENANT_ID);

    verify(rangeRepository).resetAllFailed(CALL_NUMBER);
    verify(producer).sendMessages(any());
  }

  @Test
  void retryFailed_positive_finalizesWhenNoRangesLeft() {
    when(scopeResolver.resolve(TENANT_ID, null)).thenReturn(WHOLE);
    when(stateRepository.prepareAllRetry(CALL_NUMBER)).thenReturn(true);
    when(rangeRepository.findAllUnfinished(CALL_NUMBER)).thenReturn(List.of());

    service.retryFailed(CALL_NUMBER, TENANT_ID);

    verify(finalizer).tryFinalize(CALL_NUMBER, TENANT_ID);
    verifyNoInteractions(producer);
  }

  @Test
  void retryFailed_negative_sourceIndexNotReady() {
    when(scopeResolver.resolve(TENANT_ID, null)).thenReturn(WHOLE);
    doThrow(new RequestValidationException("not ready", "tenantId", TENANT_ID))
      .when(readinessService).validateCanResume(CALL_NUMBER, WHOLE.tenants());

    assertThatThrownBy(() -> service.retryFailed(CALL_NUMBER, TENANT_ID))
      .isInstanceOf(RequestValidationException.class);
    verify(stateRepository, never()).prepareAllRetry(any());
  }

  @Test
  void retryFailed_positive_nothingToRetry() {
    when(scopeResolver.resolve(TENANT_ID, null)).thenReturn(WHOLE);
    when(stateRepository.prepareAllRetry(CALL_NUMBER)).thenReturn(false);

    service.retryFailed(CALL_NUMBER, TENANT_ID);

    verify(rangeRepository, never()).resetAllFailed(any());
  }
}
