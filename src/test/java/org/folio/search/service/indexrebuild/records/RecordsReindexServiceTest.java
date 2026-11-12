package org.folio.search.service.indexrebuild.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.service.indexrebuild.IndexRebuildTarget.ITEM;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.IndexRebuildStatusItem;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.exception.ReindexException;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.IndexService;
import org.folio.search.service.indexrebuild.IndexRebuildReadinessService;
import org.folio.search.service.indexrebuild.IndexRebuildScope;
import org.folio.search.service.indexrebuild.IndexRebuildScopeResolver;
import org.folio.search.service.indexrebuild.IndexRebuildStateService;
import org.folio.search.service.indexrebuild.IndexRebuildStatusReporter;
import org.folio.search.service.indexrebuild.item.ItemRecordsRebuildStrategy;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildRangeRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.folio.search.service.indexrebuild.model.IndexRebuildRange;
import org.folio.search.service.indexrebuild.model.IndexRebuildRangeStatus;
import org.folio.search.service.indexrebuild.model.IndexRebuildRunMode;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RecordsReindexServiceTest {

  private static final String MEMBER = "member";
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
  private RecordsRangePublisher rangePublisher;
  @Mock
  private RecordsFinalizer finalizer;
  @Mock
  private IndexService indexService;
  @Mock
  private PrimaryResourceRepository resourceRepository;
  @Mock
  private ExecutorService executor;
  private SearchConfigurationProperties searchConfiguration;
  private RecordsRebuildStrategy strategy;
  private RecordsReindexService service;

  @BeforeEach
  void setUp() {
    searchConfiguration = new SearchConfigurationProperties();
    searchConfiguration.setIndexing(new SearchConfigurationProperties.IndexingSettings());
    searchConfiguration.getIndexing().setItemIndexEnabled(true);
    strategy = new ItemRecordsRebuildStrategy(null, searchConfiguration);
    service = new RecordsReindexService(new RecordsRebuildStrategies(List.of(strategy)), scopeResolver,
      readinessService, stateService, stateRepository, rangeRepository, statusReporter, rangePublisher, finalizer,
      indexService, resourceRepository, executor);
    lenient().doAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    }).when(executor).execute(any());
  }

  @Test
  void start_positive_recreatesIndexAndPublishesRanges() {
    var range = range(TENANT_ID);
    var settings = new IndexSettings();
    when(scopeResolver.resolve(TENANT_ID, null)).thenReturn(WHOLE);
    when(indexService.createIndex(ResourceType.ITEM, TENANT_ID, settings)).thenReturn(new FolioCreateIndexResponse());
    when(rangePublisher.createRanges(strategy, TENANT_ID)).thenReturn(List.of(range));

    service.start(ITEM, TENANT_ID, null, settings);

    verify(readinessService).validateCanStart(ITEM, WHOLE);
    verify(stateService).start(ITEM, WHOLE.tenants(), IndexRebuildRunMode.PER_TENANT);
    verify(indexService).dropIndex(ResourceType.ITEM, TENANT_ID);
    verify(rangeRepository).saveAll(List.of(range));
    verify(rangePublisher).publish(strategy, List.of(range));
  }

  @Test
  void start_positive_memberRerunDeletesOnlyTenantDocuments() {
    var scope = new IndexRebuildScope(List.of(MEMBER), false);
    when(scopeResolver.resolve(TENANT_ID, MEMBER)).thenReturn(scope);
    when(resourceRepository.deleteResourceByTenantId(ResourceType.ITEM, MEMBER))
      .thenReturn(new FolioIndexOperationResponse().status(FolioIndexOperationResponse.StatusEnum.SUCCESS));

    service.start(ITEM, TENANT_ID, MEMBER, null);

    verifyNoInteractions(indexService);
    verify(rangePublisher).createRanges(strategy, MEMBER);
  }

  @Test
  void start_negative_itemIndexDisabled() {
    searchConfiguration.getIndexing().setItemIndexEnabled(false);

    assertThatThrownBy(() -> service.start(ITEM, TENANT_ID, null, null)).isInstanceOf(RequestValidationException.class);

    verifyNoInteractions(stateService);
  }

  @Test
  void start_negative_indexCannotBeDropped() {
    when(scopeResolver.resolve(TENANT_ID, null)).thenReturn(WHOLE);
    doThrow(new IllegalStateException("error")).when(indexService).dropIndex(ResourceType.ITEM, TENANT_ID);

    assertThatThrownBy(() -> service.start(ITEM, TENANT_ID, null, null)).isInstanceOf(ReindexException.class);

    verify(stateService).markFailed(eq(ITEM), eq(WHOLE.tenants()), anyString());
    verifyNoInteractions(rangePublisher);
  }

  @Test
  void start_negative_indexNotCreated() {
    when(scopeResolver.resolve(TENANT_ID, null)).thenReturn(WHOLE);
    when(indexService.createIndex(ResourceType.ITEM, TENANT_ID, null)).thenReturn(
      new FolioCreateIndexResponse().status(FolioCreateIndexResponse.StatusEnum.ERROR).errorMessage("error"));

    assertThatThrownBy(() -> service.start(ITEM, TENANT_ID, null, null)).isInstanceOf(ReindexException.class);

    verifyNoInteractions(rangePublisher);
  }

  @Test
  void start_negative_publishFailureMarksRunFailed() {
    when(scopeResolver.resolve(TENANT_ID, null)).thenReturn(WHOLE);
    when(indexService.createIndex(ResourceType.ITEM, TENANT_ID, null)).thenReturn(new FolioCreateIndexResponse());
    when(rangePublisher.createRanges(strategy, TENANT_ID)).thenThrow(new IllegalStateException("error"));

    service.start(ITEM, TENANT_ID, null, null);

    verify(stateService).markFailed(eq(ITEM), eq(WHOLE.tenants()), anyString());
  }

  @Test
  void retryFailed_positive_republishesUnfinishedRanges() {
    var range = range(TENANT_ID);
    when(scopeResolver.resolve(TENANT_ID, null)).thenReturn(WHOLE);
    when(stateRepository.prepareRetry(ITEM, TENANT_ID)).thenReturn(true);
    when(rangeRepository.findUnfinished(ITEM, TENANT_ID)).thenReturn(List.of(range));

    service.retryFailed(ITEM, TENANT_ID);

    verify(rangeRepository).resetFailed(ITEM, TENANT_ID);
    verify(rangePublisher).publish(strategy, List.of(range));
    verify(finalizer, never()).tryFinalize(any(), any());
  }

  @Test
  void retryFailed_positive_finalizesWhenNoRangesLeft() {
    when(scopeResolver.resolve(TENANT_ID, null)).thenReturn(WHOLE);
    when(stateRepository.prepareRetry(ITEM, TENANT_ID)).thenReturn(true);
    when(rangeRepository.findUnfinished(ITEM, TENANT_ID)).thenReturn(List.of());

    service.retryFailed(ITEM, TENANT_ID);

    verify(finalizer).tryFinalize(strategy, TENANT_ID);
  }

  @Test
  void retryFailed_positive_skipsTenantsWithoutFailedRun() {
    when(scopeResolver.resolve(TENANT_ID, null)).thenReturn(WHOLE);
    when(stateRepository.prepareRetry(ITEM, TENANT_ID)).thenReturn(false);

    service.retryFailed(ITEM, TENANT_ID);

    verify(rangeRepository, never()).resetFailed(any(), any());
    verify(rangePublisher).publish(strategy, List.of());
  }

  @Test
  void retryFailed_negative_dependentRunActive() {
    when(scopeResolver.resolve(TENANT_ID, null)).thenReturn(WHOLE);
    doThrow(new RequestValidationException("active", "resource", "call_number"))
      .when(readinessService).validateCanResume(ITEM, WHOLE.tenants());

    assertThatThrownBy(() -> service.retryFailed(ITEM, TENANT_ID)).isInstanceOf(RequestValidationException.class);
    verify(stateRepository, never()).prepareRetry(any(), any());
  }

  @Test
  void getStatus_positive_reportsTenantsOfScope() {
    var expected = List.of(new IndexRebuildStatusItem().tenantId(TENANT_ID));
    when(scopeResolver.resolve(TENANT_ID, null)).thenReturn(WHOLE);
    when(statusReporter.getStatus(ITEM, WHOLE.tenants())).thenReturn(expected);

    assertThat(service.getStatus(ITEM, TENANT_ID)).isEqualTo(expected);
  }

  private static IndexRebuildRange range(String tenant) {
    return new IndexRebuildRange(UUID.randomUUID(), UUID.randomUUID(), ITEM, tenant, "0", "f",
      IndexRebuildRangeStatus.PENDING, null);
  }
}
