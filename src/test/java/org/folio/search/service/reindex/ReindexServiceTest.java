package org.folio.search.service.reindex;

import static org.folio.search.exception.RequestValidationException.REQUEST_NOT_ALLOWED_MSG;
import static org.folio.search.model.types.ReindexEntityType.HOLDINGS;
import static org.folio.search.model.types.ReindexEntityType.INSTANCE;
import static org.folio.search.model.types.ReindexEntityType.ITEM;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import org.apache.commons.lang3.ThreadUtils;
import org.folio.search.converter.ReindexEntityTypeMapper;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.domain.dto.ReindexUploadDto;
import org.folio.search.exception.FolioIntegrationException;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.integration.folio.InventoryService;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexStatus;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ReindexServiceTest {

  @Mock
  private ConsortiumTenantService consortiumService;
  @Mock
  private SystemUserScopedExecutionService executionService;
  @Mock
  private ReindexMergeRangeIndexService mergeRangeService;
  @Mock
  private ReindexUploadRangeIndexService uploadRangeService;
  @Mock
  private ReindexStatusService statusService;
  @Mock
  private InventoryService inventoryService;
  @Mock
  private ExecutorService reindexExecutor;
  @Mock
  private ReindexEntityTypeMapper entityTypeMapper;
  @Mock
  private ReindexCommonService reindexCommonService;
  @InjectMocks
  private ReindexService reindexService;

  @Test
  void submitFullReindex_negative_shouldFailForEcsMemberTenant() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of("central"));

    assertThrows(RequestValidationException.class, () -> reindexService.submitFullReindex(TENANT_ID, null),
      REQUEST_NOT_ALLOWED_MSG);
  }

  @Test
  void submitFullReindex_positive() throws InterruptedException {
    var tenant = "central";
    var member = "member";
    var id = UUID.randomUUID();
    var bound = UUID.randomUUID().toString();
    var rangeEntity =
      new MergeRangeEntity(id, INSTANCE, tenant, bound, bound, Timestamp.from(Instant.now()), null, null);

    when(consortiumService.getCentralTenant(tenant)).thenReturn(Optional.of(tenant));
    when(mergeRangeService.createMergeRanges(tenant)).thenReturn(List.of(rangeEntity));
    when(executionService.executeSystemUserScoped(anyString(), any())).thenReturn(List.of());
    when(consortiumService.getConsortiumTenants(tenant)).thenReturn(List.of(member));
    when(mergeRangeService.fetchMergeRanges(any(ReindexEntityType.class))).thenReturn(List.of(rangeEntity));
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(reindexExecutor).execute(any());
    final var expectedCallsCount = ReindexEntityType.supportMergeTypes().size();
    final var indexSettings = new IndexSettings().refreshInterval(1).numberOfShards(2).numberOfReplicas(3);

    reindexService.submitFullReindex(tenant, indexSettings);
    ThreadUtils.sleep(Duration.ofSeconds(1));

    verify(reindexCommonService).deleteAllRecords();
    verify(statusService).recreateMergeStatusRecords();
    verify(mergeRangeService).createMergeRanges(tenant);
    verify(mergeRangeService).saveMergeRanges(anyList());
    verify(executionService).executeSystemUserScoped(eq(member), any(Callable.class));
    verify(executionService, times(expectedCallsCount)).executeSystemUserScoped(eq(tenant), any(Callable.class));
    verify(statusService, times(expectedCallsCount))
      .updateReindexMergeStarted(any(ReindexEntityType.class), eq(1));
    verify(mergeRangeService, times(expectedCallsCount)).fetchMergeRanges(any(ReindexEntityType.class));
    verify(mergeRangeService).truncateMergeRanges();
    verify(reindexCommonService, times(ReindexEntityType.supportUploadTypes().size()))
      .recreateIndex(any(), eq(tenant), eq(indexSettings));
    verifyNoMoreInteractions(mergeRangeService);
  }

  @Test
  void submitFullReindex_negative_abortMergeAndSetFailedStatusWhenPublishingRangesFailed() throws InterruptedException {
    var tenant = "central";
    var member = "member";
    var id = UUID.randomUUID();
    var bound = UUID.randomUUID().toString();
    var rangeEntity =
      new MergeRangeEntity(id, INSTANCE, tenant, bound, bound, Timestamp.from(Instant.now()), null, null);

    when(consortiumService.getCentralTenant(tenant)).thenReturn(Optional.of(tenant));
    when(consortiumService.getConsortiumTenants(tenant)).thenReturn(List.of(member));
    when(mergeRangeService.createMergeRanges(tenant)).thenReturn(List.of(rangeEntity));
    when(executionService.executeSystemUserScoped(anyString(), any()))
      .thenReturn(List.of()); // when creating ranges for one member tenant
    when(mergeRangeService.fetchMergeRanges(any(ReindexEntityType.class))).thenReturn(List.of(rangeEntity));

    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    })
      .doThrow(FolioIntegrationException.class)
      .when(reindexExecutor).execute(any());

    reindexService.submitFullReindex(tenant, null);
    ThreadUtils.sleep(Duration.ofSeconds(1));

    verify(mergeRangeService).saveMergeRanges(anyList());
    verify(statusService)
      .updateReindexMergeStarted(any(ReindexEntityType.class), eq(1));
    verify(mergeRangeService).fetchMergeRanges(any(ReindexEntityType.class));
    verify(statusService).updateReindexMergeFailed();
  }

  @Test
  void submitUploadReindex_negative_notAllowedToRunUploadForEcsMemberTenant() {
    // given
    var member = "member";
    var central = "central";
    when(consortiumService.getCentralTenant(member)).thenReturn(Optional.of(central));
    List<ReindexEntityType> entityTypes = List.of();

    // act & assert
    assertThrows(RequestValidationException.class,
      () -> reindexService.submitUploadReindex(member, entityTypes), REQUEST_NOT_ALLOWED_MSG);
  }

  @Test
  void submitUploadReindex_negative_notAllowedToRunUploadWhenMergeNotComplete() {
    // given
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    when(statusService.getStatusesByType())
      .thenReturn(Map.of(HOLDINGS, ReindexStatus.MERGE_IN_PROGRESS));
    var entityTypes = List.of(ReindexEntityType.INSTANCE);

    // act & assert
    assertThrows(RequestValidationException.class,
      () -> reindexService.submitUploadReindex(TENANT_ID, entityTypes),
      "Full Reindex Merge is either in progress or failed " + HOLDINGS.getType());
  }

  @Test
  void submitUploadReindex_negative_notAllowedToRunUploadWhenUploadInProgress() {
    // given
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    when(statusService.getStatusesByType())
      .thenReturn(Map.of(
        INSTANCE, ReindexStatus.MERGE_COMPLETED,
        ITEM, ReindexStatus.MERGE_IN_PROGRESS,
        HOLDINGS, ReindexStatus.MERGE_FAILED));
    var entityTypes = List.of(ReindexEntityType.INSTANCE);

    // act & assert
    assertThrows(RequestValidationException.class,
      () -> reindexService.submitUploadReindex(TENANT_ID, entityTypes),
      "Reindex Merge is not complete for entity " + HOLDINGS.getType());
  }

  @Test
  void submitUploadReindex_positive() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(reindexExecutor).execute(any());

    reindexService.submitUploadReindex(TENANT_ID, List.of(ReindexEntityType.INSTANCE));

    verify(statusService).recreateUploadStatusRecord(INSTANCE);
    verify(uploadRangeService).prepareAndSendIndexRanges(INSTANCE);
  }

  @Test
  void submitUploadReindex_positive_recreateIndex() {
    var uploadDto = new ReindexUploadDto().entityTypes(List.of(ReindexUploadDto.EntityTypesEnum.INSTANCE));
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    when(entityTypeMapper.convert(uploadDto.getEntityTypes())).thenReturn(List.of(INSTANCE));
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(reindexExecutor).execute(any());

    reindexService.submitUploadReindex(TENANT_ID, uploadDto);

    verify(statusService).recreateUploadStatusRecord(INSTANCE);
    verify(uploadRangeService).prepareAndSendIndexRanges(INSTANCE);
  }

  @Test
  void submitUploadReindex_negative_failedToPrepareUploadRangesAndSendThem() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    doThrow(RuntimeException.class).when(uploadRangeService).prepareAndSendIndexRanges(INSTANCE);

    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(reindexExecutor).execute(any());

    reindexService.submitUploadReindex(TENANT_ID, List.of(ReindexEntityType.INSTANCE));

    verify(statusService).updateReindexUploadFailed(INSTANCE);
  }
}
