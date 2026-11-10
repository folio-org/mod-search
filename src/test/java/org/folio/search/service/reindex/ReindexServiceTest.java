package org.folio.search.service.reindex;

import static java.util.Collections.emptyList;
import static org.folio.search.exception.RequestValidationException.REQUEST_NOT_ALLOWED_FOR_CONSORTIUM_MEMBER_MSG;
import static org.folio.search.exception.RequestValidationException.REQUEST_NOT_ALLOWED_WITH_TARGET_TENANT_MSG;
import static org.folio.search.model.types.ReindexEntityType.HOLDINGS;
import static org.folio.search.model.types.ReindexEntityType.INSTANCE;
import static org.folio.search.model.types.ReindexEntityType.ITEM;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ThreadUtils;
import org.folio.search.converter.ReindexEntityTypeMapper;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.domain.dto.ReindexUploadDto;
import org.folio.search.exception.FolioIntegrationException;
import org.folio.search.exception.ReindexException;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.integration.folio.InventoryService;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexStatus;
import org.folio.search.service.EgressExecutionContextService;
import org.folio.search.service.ItemIndexService;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.ObjectProvider;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ReindexServiceTest {

  @Mock
  private ConsortiumTenantService consortiumService;
  @Mock
  private EgressExecutionContextService executionService;
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
  @Mock
  private ObjectProvider<ItemIndexService> itemIndexServiceProvider;
  @Mock
  private ItemIndexService itemIndexService;
  @InjectMocks
  private ReindexService reindexService;

  @Test
  void submitFullReindex_negative_shouldFailForEcsMemberTenant() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of("central"));

    assertThrows(RequestValidationException.class, () -> reindexService.submitFullReindex(TENANT_ID, null),
      REQUEST_NOT_ALLOWED_FOR_CONSORTIUM_MEMBER_MSG);
  }

  @Test
  void submitFullReindex_positive() throws InterruptedException {
    var tenant = "central";
    var member = "member";
    var rangeEntity = buildMergeRangeEntity(UUID.randomUUID(), tenant);

    when(consortiumService.getCentralTenant(tenant)).thenReturn(Optional.of(tenant));
    when(mergeRangeService.createMergeRanges(tenant)).thenReturn(List.of(rangeEntity));
    when(executionService.execute(anyString(), any(Callable.class))).thenReturn(List.of());
    when(consortiumService.getConsortiumTenants(tenant)).thenReturn(List.of(member));
    when(mergeRangeService.fetchMergeRanges(any(ReindexEntityType.class))).thenReturn(List.of(rangeEntity));
    doAnswer(executeRunnable()).when(reindexExecutor).execute(any());
    final var expectedCallsCount = ReindexEntityType.supportMergeTypes().size();
    final var indexSettings = new IndexSettings().refreshInterval(1).numberOfShards(2).numberOfReplicas(3);

    reindexService.submitFullReindex(tenant, indexSettings);
    ThreadUtils.sleep(Duration.ofSeconds(1));

    verify(reindexCommonService).deleteAllRecords(null);
    verify(statusService).recreateMergeStatusRecords(null);
    verify(reindexCommonService, times(ReindexEntityType.supportUploadTypes().size()))
      .recreateIndex(any(), eq(tenant), eq(indexSettings));
    verify(mergeRangeService).saveMergeRanges(anyList());
    verify(statusService, times(expectedCallsCount))
      .updateReindexMergeStarted(any(ReindexEntityType.class), eq(1));
    verify(mergeRangeService, times(expectedCallsCount)).fetchMergeRanges(any(ReindexEntityType.class));
    verify(mergeRangeService).truncateMergeRanges();
    verifyNoMoreInteractions(mergeRangeService);
  }

  @Test
  void submitFullReindex_negative_abortMergeAndSetFailedStatusWhenPublishingRangesFailed() throws InterruptedException {
    var tenant = "central";
    var member = "member";
    var rangeEntity = buildMergeRangeEntity(UUID.randomUUID(), tenant);
    final int entityTypeCount = ReindexEntityType.supportMergeTypes().size();

    when(consortiumService.getCentralTenant(tenant)).thenReturn(Optional.of(tenant));
    when(consortiumService.getConsortiumTenants(tenant)).thenReturn(List.of(member));
    when(mergeRangeService.createMergeRanges(tenant)).thenReturn(List.of(rangeEntity));
    when(executionService.execute(anyString(), any(Callable.class)))
      .thenReturn(List.of())                       // createMergeRanges for member tenant
      .thenThrow(FolioIntegrationException.class); // publishing phase — all entity types fail
    when(mergeRangeService.fetchMergeRanges(any(ReindexEntityType.class))).thenReturn(List.of(rangeEntity));
    doAnswer(executeRunnable()).when(reindexExecutor).execute(any());

    reindexService.submitFullReindex(tenant, null);
    ThreadUtils.sleep(Duration.ofSeconds(1));

    verify(mergeRangeService).saveMergeRanges(anyList());
    verify(mergeRangeService, times(entityTypeCount)).fetchMergeRanges(any(ReindexEntityType.class));
    verify(statusService, times(entityTypeCount)).updateReindexMergeStarted(any(ReindexEntityType.class), eq(1));
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
      () -> reindexService.submitUploadReindex(member, entityTypes), REQUEST_NOT_ALLOWED_FOR_CONSORTIUM_MEMBER_MSG);
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
    doAnswer(executeRunnable()).when(reindexExecutor).execute(any());

    reindexService.submitUploadReindex(TENANT_ID, List.of(ReindexEntityType.INSTANCE));

    verify(statusService).getTargetTenantId();
    verify(statusService).upsertUploadStatusRecord(eq(INSTANCE), any());
    verify(reindexCommonService).enableAutoVacuumEntityTables();
    verify(uploadRangeService).prepareAndSendIndexRanges(INSTANCE);
    verify(reindexCommonService, never()).recreateIndex(any(), any(), any());
  }

  @Test
  void submitUploadReindex_positive_recreateIndex() {
    var uploadDto = new ReindexUploadDto().entityTypes(List.of(ReindexUploadDto.EntityTypesEnum.INSTANCE));
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    when(entityTypeMapper.convert(uploadDto.getEntityTypes())).thenReturn(List.of(INSTANCE));
    doAnswer(executeRunnable()).when(reindexExecutor).execute(any());

    reindexService.submitUploadReindex(TENANT_ID, uploadDto);

    verify(statusService).getTargetTenantId();
    verify(statusService).upsertUploadStatusRecord(eq(INSTANCE), any());
    verify(reindexCommonService).recreateIndex(eq(INSTANCE), eq(TENANT_ID), any());
    verify(reindexCommonService).enableAutoVacuumEntityTables();
    verify(uploadRangeService).prepareAndSendIndexRanges(INSTANCE);
  }

  @Test
  void submitUploadReindex_positive_filtersOutNonUploadEntityTypes() {
    // mapper returns HOLDINGS which has supportsUpload=false, so it should be filtered out
    var uploadDto = new ReindexUploadDto()
      .entityTypes(List.of(ReindexUploadDto.EntityTypesEnum.INSTANCE, ReindexUploadDto.EntityTypesEnum.SUBJECT));
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    when(entityTypeMapper.convert(uploadDto.getEntityTypes())).thenReturn(List.of(INSTANCE, HOLDINGS));
    doAnswer(executeRunnable()).when(reindexExecutor).execute(any());

    reindexService.submitUploadReindex(TENANT_ID, uploadDto);

    verify(uploadRangeService).prepareAndSendIndexRanges(INSTANCE);
    verify(uploadRangeService, never()).prepareAndSendIndexRanges(HOLDINGS);
  }

  @Test
  void submitUploadReindex_negative_failedToPrepareUploadRangesAndSendThem() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    doThrow(RuntimeException.class).when(uploadRangeService).prepareAndSendIndexRanges(INSTANCE);

    doAnswer(executeRunnable()).when(reindexExecutor).execute(any());

    reindexService.submitUploadReindex(TENANT_ID, List.of(ReindexEntityType.INSTANCE));

    verify(statusService).updateReindexUploadFailed(INSTANCE);
  }

  @Test
  void submitFailedMergeRangesReindex_negative_shouldFailForEcsMemberTenant() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of("central"));

    assertThrows(RequestValidationException.class, () -> reindexService.submitFailedMergeRangesReindex(TENANT_ID),
      REQUEST_NOT_ALLOWED_FOR_CONSORTIUM_MEMBER_MSG);
  }

  @Test
  @SneakyThrows
  void submitFailedMergeRangesReindex_negative_noFailedRanges() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    when(mergeRangeService.fetchFailedMergeRanges()).thenReturn(emptyList());

    reindexService.submitFailedMergeRangesReindex(TENANT_ID).get();

    verifyNoInteractions(statusService);
    verifyNoInteractions(inventoryService);
  }

  @Test
  @SneakyThrows
  void submitFailedMergeRangesReindex_positive() {
    var failedRanges = List.of(
      createMergeRangeEntity(ReindexEntityType.ITEM),
      createMergeRangeEntity(ReindexEntityType.HOLDINGS),
      createMergeRangeEntity(ReindexEntityType.HOLDINGS),
      createMergeRangeEntity(ReindexEntityType.INSTANCE),
      createMergeRangeEntity(ReindexEntityType.INSTANCE),
      createMergeRangeEntity(ReindexEntityType.INSTANCE));

    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    when(mergeRangeService.fetchFailedMergeRanges()).thenReturn(failedRanges);
    doAnswer(executeRunnable()).when(reindexExecutor).execute(any());
    doAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call())
      .when(executionService).execute(anyString(), any(Callable.class));

    reindexService.submitFailedMergeRangesReindex(TENANT_ID).get();

    verify(statusService).updateReindexMergeInProgress(
      Set.of(ReindexEntityType.ITEM, ReindexEntityType.HOLDINGS, ReindexEntityType.INSTANCE));
    failedRanges.forEach(range ->
      verify(inventoryService).publishReindexRecordsRange(range));
  }

  @Test
  void submitItemReindex_negative_shouldFailForEcsMemberTenant() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of("central"));

    assertThrows(RequestValidationException.class, () -> reindexService.submitItemReindex(TENANT_ID, null),
      REQUEST_NOT_ALLOWED_FOR_CONSORTIUM_MEMBER_MSG);

    verifyNoInteractions(statusService, reindexCommonService, mergeRangeService);
  }

  @Test
  void submitItemReindex_negative_shouldFailWhenItemIndexIsDisabled() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());
    when(itemIndexServiceProvider.getIfAvailable()).thenReturn(null);

    assertThrows(RequestValidationException.class, () -> reindexService.submitItemReindex(TENANT_ID, null));

    verifyNoInteractions(statusService, reindexCommonService, mergeRangeService);
  }

  @ParameterizedTest
  @EnumSource(value = ReindexStatus.class, names = {"MERGE_IN_PROGRESS", "UPLOAD_IN_PROGRESS", "STAGING_IN_PROGRESS",
                                                    "MERGE_FAILED", "UPLOAD_FAILED", "STAGING_FAILED"})
  void submitItemReindex_negative_shouldFailWhenAnotherReindexIsInProgressOrFailed(ReindexStatus status) {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());
    when(itemIndexServiceProvider.getIfAvailable()).thenReturn(itemIndexService);
    when(statusService.getStatusesByType()).thenReturn(Map.of(INSTANCE, status, ITEM, ReindexStatus.MERGE_COMPLETED));

    assertThrows(RequestValidationException.class, () -> reindexService.submitItemReindex(TENANT_ID, null));

    verify(statusService, never()).startItemOnlyReindex();
    verifyNoInteractions(reindexCommonService, mergeRangeService);
  }

  @Test
  void submitItemReindex_negative_shouldFailWhenInstanceIsBetweenPhases() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());
    when(itemIndexServiceProvider.getIfAvailable()).thenReturn(itemIndexService);
    when(statusService.getStatusesByType()).thenReturn(Map.of(INSTANCE, ReindexStatus.MERGE_COMPLETED));

    assertThrows(RequestValidationException.class, () -> reindexService.submitItemReindex(TENANT_ID, null));

    verify(statusService, never()).startItemOnlyReindex();
  }

  @Test
  void submitItemReindex_negative_shouldFailWhenItemOnlyReindexIsInProgress() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());
    when(itemIndexServiceProvider.getIfAvailable()).thenReturn(itemIndexService);
    when(statusService.getStatusesByType()).thenReturn(Map.of(ITEM, ReindexStatus.MERGE_IN_PROGRESS));
    when(statusService.isItemOnlyReindex()).thenReturn(true);

    assertThrows(RequestValidationException.class, () -> reindexService.submitItemReindex(TENANT_ID, null));
  }

  @Test
  void submitItemReindex_negative_shouldFailWhenRangesOfPreviousRunAreActive() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());
    when(itemIndexServiceProvider.getIfAvailable()).thenReturn(itemIndexService);
    when(statusService.getStatusesByType()).thenReturn(Map.of(ITEM, ReindexStatus.MERGE_FAILED));
    when(statusService.isItemOnlyReindex()).thenReturn(true);
    when(mergeRangeService.hasActiveMergeRanges(eq(ITEM), any())).thenReturn(true);

    assertThrows(RequestValidationException.class, () -> reindexService.submitItemReindex(TENANT_ID, null));

    verify(statusService, never()).startItemOnlyReindex();
    verifyNoInteractions(reindexCommonService);
  }

  @Test
  void submitItemReindex_negative_shouldFailWhenInstanceIsBetweenStagingAndUpload() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());
    when(itemIndexServiceProvider.getIfAvailable()).thenReturn(itemIndexService);
    when(statusService.getStatusesByType()).thenReturn(Map.of(INSTANCE, ReindexStatus.STAGING_COMPLETED));

    assertThrows(RequestValidationException.class, () -> reindexService.submitItemReindex(TENANT_ID, null));

    verify(statusService, never()).startItemOnlyReindex();
  }

  @Test
  void submitItemReindex_positive_finalStagingStatusOfMergeOnlyTypesDoesNotBlock() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());
    when(itemIndexServiceProvider.getIfAvailable()).thenReturn(itemIndexService);
    when(statusService.getStatusesByType()).thenReturn(Map.of(INSTANCE, ReindexStatus.UPLOAD_COMPLETED,
      HOLDINGS, ReindexStatus.STAGING_COMPLETED, ITEM, ReindexStatus.STAGING_COMPLETED));
    doAnswer(executeRunnable()).when(reindexExecutor).execute(any());

    reindexService.submitItemReindex(TENANT_ID, null);

    verify(statusService).startItemOnlyReindex();
  }

  @Test
  void submitItemReindex_negative_marksItemFailedWhenIndexCannotBeRecreated() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());
    when(itemIndexServiceProvider.getIfAvailable()).thenReturn(itemIndexService);
    when(statusService.getStatusesByType()).thenReturn(Map.of());
    doThrow(new ReindexException("error")).when(reindexCommonService).recreateIndexOrThrow(ITEM, TENANT_ID, null);

    assertThrows(ReindexException.class, () -> reindexService.submitItemReindex(TENANT_ID, null));

    verify(statusService).updateReindexMergeFailed(ITEM);
    verify(statusService, never()).updateReindexMergeFailed();
    verify(mergeRangeService, never()).createItemMergeRanges(any());
  }

  @Test
  void submitUploadReindex_positive_itemOnlyRunDoesNotBlockOtherEntityTypes() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    when(statusService.getStatusesByType()).thenReturn(Map.of(ITEM, ReindexStatus.MERGE_FAILED));
    when(statusService.isItemOnlyReindex()).thenReturn(true);
    doAnswer(executeRunnable()).when(reindexExecutor).execute(any());

    reindexService.submitUploadReindex(TENANT_ID, List.of(INSTANCE));

    verify(statusService).upsertUploadStatusRecord(eq(INSTANCE), any());
  }

  @Test
  void submitUploadReindex_negative_itemOnlyRunBlocksCallNumbers() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    when(statusService.getStatusesByType()).thenReturn(Map.of(ITEM, ReindexStatus.MERGE_IN_PROGRESS));
    var entityTypes = List.of(ReindexEntityType.CALL_NUMBER);

    assertThrows(RequestValidationException.class, () -> reindexService.submitUploadReindex(TENANT_ID, entityTypes));
  }

  @Test
  void submitItemReindex_positive_restartsAfterFailedItemOnlyRun() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());
    when(itemIndexServiceProvider.getIfAvailable()).thenReturn(itemIndexService);
    when(statusService.getStatusesByType()).thenReturn(Map.of(INSTANCE, ReindexStatus.UPLOAD_COMPLETED,
      ITEM, ReindexStatus.MERGE_FAILED));
    when(statusService.isItemOnlyReindex()).thenReturn(true);
    when(consortiumService.getConsortiumTenants(TENANT_ID)).thenReturn(List.of());
    var range = createMergeRangeEntity(ITEM);
    when(mergeRangeService.createItemMergeRanges(TENANT_ID)).thenReturn(List.of(range));
    when(mergeRangeService.fetchMergeRanges(ITEM)).thenReturn(List.of(range));
    doAnswer(executeRunnable()).when(reindexExecutor).execute(any());

    reindexService.submitItemReindex(TENANT_ID, null);

    verify(statusService).startItemOnlyReindex();
  }

  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void submitItemReindex_positive_touchesOnlyItemData() {
    var member = "member";
    var centralRange = createMergeRangeEntity(ITEM);
    var memberRange = createMergeRangeEntity(ITEM);
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    when(itemIndexServiceProvider.getIfAvailable()).thenReturn(itemIndexService);
    when(statusService.getStatusesByType()).thenReturn(Map.of(INSTANCE, ReindexStatus.UPLOAD_COMPLETED,
      HOLDINGS, ReindexStatus.MERGE_COMPLETED, ITEM, ReindexStatus.MERGE_COMPLETED));
    when(consortiumService.getConsortiumTenants(TENANT_ID)).thenReturn(List.of(member));
    when(mergeRangeService.createItemMergeRanges(TENANT_ID)).thenReturn(List.of(centralRange));
    when(executionService.execute(eq(member), any(Callable.class))).thenReturn(List.of(memberRange));
    when(mergeRangeService.fetchMergeRanges(ITEM)).thenReturn(List.of(centralRange, memberRange));
    doAnswer(executeRunnable()).when(reindexExecutor).execute(any());
    doAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call())
      .when(executionService).execute(eq(TENANT_ID), any(Callable.class));

    final var indexSettings = new IndexSettings().refreshInterval(1).numberOfShards(2).numberOfReplicas(3);

    reindexService.submitItemReindex(TENANT_ID, indexSettings);

    verify(statusService).startItemOnlyReindex();
    verify(reindexCommonService).recreateIndexOrThrow(ITEM, TENANT_ID, indexSettings);
    verify(reindexCommonService, never()).deleteAllRecords(any());
    verify(reindexCommonService, never()).disableAutoVacuumEntityTables();
    verify(statusService, never()).recreateMergeStatusRecords(any());
    verify(mergeRangeService).deleteMergeRanges(ITEM);
    verify(mergeRangeService, never()).truncateMergeRanges();
    verify(mergeRangeService, never()).createMergeRanges(anyString());
    verify(mergeRangeService).saveMergeRanges(List.of(centralRange, memberRange));
    verify(statusService).updateReindexMergeStarted(ITEM, 2);
    verify(statusService, never()).updateReindexMergeFailed();
  }

  @Test
  void submitItemReindex_negative_marksOnlyItemFailedWhenRangesCreationFails() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());
    when(itemIndexServiceProvider.getIfAvailable()).thenReturn(itemIndexService);
    when(statusService.getStatusesByType()).thenReturn(Map.of());
    when(mergeRangeService.createItemMergeRanges(TENANT_ID)).thenThrow(new FolioIntegrationException("error"));
    doAnswer(executeRunnable()).when(reindexExecutor).execute(any());

    reindexService.submitItemReindex(TENANT_ID, null);

    verify(statusService).updateReindexMergeFailed(ITEM);
    verify(statusService, never()).updateReindexMergeFailed();
    verify(mergeRangeService, never()).saveMergeRanges(anyList());
  }

  @Test
  @SneakyThrows
  void submitFailedItemRangesReindex_negative_shouldFailForEcsMemberTenant() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of("central"));

    assertThrows(RequestValidationException.class, () -> reindexService.submitFailedItemRangesReindex(TENANT_ID));
  }

  @Test
  @SneakyThrows
  void submitFailedItemRangesReindex_negative_noFailedRanges() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    when(mergeRangeService.fetchFailedMergeRanges(ITEM)).thenReturn(emptyList());

    reindexService.submitFailedItemRangesReindex(TENANT_ID).get();

    verifyNoInteractions(statusService, inventoryService);
  }

  @Test
  @SneakyThrows
  void submitFailedItemRangesReindex_positive_publishesOnlyItemRanges() {
    var failedRanges = List.of(createMergeRangeEntity(ITEM), createMergeRangeEntity(ITEM));
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    when(mergeRangeService.fetchFailedMergeRanges(ITEM)).thenReturn(failedRanges);
    doAnswer(executeRunnable()).when(reindexExecutor).execute(any());
    doAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call())
      .when(executionService).execute(anyString(), any(Callable.class));

    reindexService.submitFailedItemRangesReindex(TENANT_ID).get();

    verify(mergeRangeService, never()).fetchFailedMergeRanges();
    verify(statusService).updateReindexMergeInProgress(Set.of(ITEM));
    verify(statusService, never()).updateReindexMergeInProgress(Set.of(ITEM, HOLDINGS, INSTANCE));
    failedRanges.forEach(range -> verify(inventoryService).publishReindexRecordsRange(range));
  }

  @Test
  void submitFullReindex_negative_shouldThrowWhenReindexAlreadyInProgressWithUploadStatus() {
    var tenant = "central";
    when(consortiumService.getCentralTenant(tenant)).thenReturn(Optional.of(tenant));
    when(statusService.getStatusesByType()).thenReturn(Map.of(INSTANCE, ReindexStatus.UPLOAD_IN_PROGRESS));

    assertThrows(RequestValidationException.class,
      () -> reindexService.submitFullReindex(tenant, null),
      "Reindex is already in progress for: instance");
  }

  @Test
  void submitFullReindex_negative_shouldThrowWhenReindexAlreadyInProgressWithMergeStatus() {
    var tenant = "central";
    when(consortiumService.getCentralTenant(tenant)).thenReturn(Optional.of(tenant));
    when(statusService.getStatusesByType()).thenReturn(Map.of(HOLDINGS, ReindexStatus.MERGE_IN_PROGRESS));

    assertThrows(RequestValidationException.class,
      () -> reindexService.submitFullReindex(tenant, null),
      "Reindex is already in progress for: holdings");
  }

  @Test
  void submitFullReindex_negative_shouldThrowForNonConsortiumTenantWithTargetTenantId() {
    var tenant = "standalone";
    var targetTenant = "other";
    when(consortiumService.getCentralTenant(tenant)).thenReturn(Optional.empty());

    assertThrows(RequestValidationException.class,
      () -> reindexService.submitFullReindex(tenant, null, targetTenant),
      REQUEST_NOT_ALLOWED_WITH_TARGET_TENANT_MSG);
  }

  @Test
  void submitFullReindex_negative_shouldThrowWhenTargetTenantIsNotConsortiumMember() {
    var tenant = "central";
    var targetTenant = "nonMember";
    when(consortiumService.getCentralTenant(tenant)).thenReturn(Optional.of(tenant));
    when(consortiumService.getConsortiumTenants(tenant)).thenReturn(List.of("member1", "member2"));

    assertThrows(RequestValidationException.class,
      () -> reindexService.submitFullReindex(tenant, null, targetTenant),
      REQUEST_NOT_ALLOWED_WITH_TARGET_TENANT_MSG);
  }

  @Test
  void submitFullReindex_positive_withTargetTenantId() throws InterruptedException {
    var tenant = "central";
    var targetTenant = "member";
    var rangeEntity = buildMergeRangeEntity(UUID.randomUUID(), targetTenant);

    when(consortiumService.getCentralTenant(tenant)).thenReturn(Optional.of(tenant));
    when(consortiumService.getConsortiumTenants(tenant)).thenReturn(List.of(targetTenant));
    when(statusService.getStatusesByType()).thenReturn(Map.of());
    when(executionService.execute(anyString(), any(Callable.class))).thenReturn(List.of(rangeEntity));
    when(mergeRangeService.fetchMergeRanges(any(ReindexEntityType.class))).thenReturn(List.of(rangeEntity));
    doAnswer(executeRunnable()).when(reindexExecutor).execute(any());
    final var expectedCallsCount = ReindexEntityType.supportMergeTypes().size();

    reindexService.submitFullReindex(tenant, null, targetTenant);
    ThreadUtils.sleep(Duration.ofSeconds(1));

    verify(reindexCommonService).deleteAllRecords(targetTenant);
    verify(statusService).recreateMergeStatusRecords(targetTenant);
    verify(reindexCommonService, times(ReindexEntityType.supportUploadTypes().size()))
      .ensureIndexExists(any(), eq(tenant), any());
    verify(reindexCommonService, never()).recreateIndex(any(), any(), any());
    verify(mergeRangeService).truncateMergeRanges();
    verify(mergeRangeService).saveMergeRanges(anyList());
    verify(statusService, times(expectedCallsCount))
      .updateReindexMergeStarted(any(ReindexEntityType.class), eq(1));
  }

  @Test
  void submitUploadReindex_negative_notAllowedToRunUploadWhenMergePhaseIsFailed() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    when(statusService.getStatusesByType())
      .thenReturn(Map.of(INSTANCE, ReindexStatus.MERGE_FAILED));
    var entityTypes = List.of(ReindexEntityType.INSTANCE);

    assertThrows(RequestValidationException.class,
      () -> reindexService.submitUploadReindex(TENANT_ID, entityTypes),
      "Merge phase is in progress or failed for: instance");
  }

  @Test
  void submitUploadReindex_negative_notAllowedToRunUploadWhenUploadAlreadyInProgress() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    when(statusService.getStatusesByType())
      .thenReturn(Map.of(INSTANCE, ReindexStatus.UPLOAD_IN_PROGRESS));
    var entityTypes = List.of(ReindexEntityType.INSTANCE);

    assertThrows(RequestValidationException.class,
      () -> reindexService.submitUploadReindex(TENANT_ID, entityTypes),
      "Reindex Upload in Progress");
  }

  @Test
  void submitUploadReindexWithTenantCleanup_positive_withTargetTenantId() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    doAnswer(executeRunnable()).when(reindexExecutor).execute(any());

    reindexService.submitUploadReindexWithTenantCleanup(TENANT_ID, List.of(INSTANCE), "member");

    verify(reindexCommonService).deleteInstanceDocumentsByTenantId("member");
    verify(statusService).upsertUploadStatusRecord(eq(INSTANCE), any());
    verify(uploadRangeService).prepareAndSendIndexRanges(INSTANCE);
    verifyNoInteractions(mergeRangeService);
  }

  @Test
  void submitUploadReindexWithTenantCleanup_positive_withNullTargetTenantId() {
    when(consortiumService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));
    doAnswer(executeRunnable()).when(reindexExecutor).execute(any());

    reindexService.submitUploadReindexWithTenantCleanup(TENANT_ID, List.of(INSTANCE), null);

    verify(reindexCommonService, never()).deleteInstanceDocumentsByTenantId(any());
    verify(statusService).upsertUploadStatusRecord(eq(INSTANCE), any());
    verify(uploadRangeService).prepareAndSendIndexRanges(INSTANCE);
    verify(reindexCommonService).enableAutoVacuumEntityTables();
  }

  @Test
  void submitUploadReindexWithTenantCleanup_negative_shouldThrowReindexExceptionWhenCleanupFails() {
    doThrow(new RuntimeException("ES error")).when(reindexCommonService)
      .deleteInstanceDocumentsByTenantId("member");

    var entityTypes = List.of(INSTANCE);

    assertThrows(ReindexException.class,
      () -> reindexService.submitUploadReindexWithTenantCleanup(TENANT_ID, entityTypes, "member"),
      "Failed to cleanup tenant documents before upload");
    verifyNoInteractions(mergeRangeService);
  }

  private MergeRangeEntity buildMergeRangeEntity(UUID id, String tenant) {
    var bound = UUID.randomUUID().toString();
    return new MergeRangeEntity(id, null, INSTANCE, tenant, bound, bound, Timestamp.from(Instant.now()), null, null);
  }

  private MergeRangeEntity createMergeRangeEntity(ReindexEntityType entityType) {
    var id = UUID.randomUUID();
    return new MergeRangeEntity(id, null, entityType, TENANT_ID, id.toString(), id.toString(),
      Timestamp.from(Instant.now()), null, null);
  }

  private Answer<?> executeRunnable() {
    return invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    };
  }
}
