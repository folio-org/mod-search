package org.folio.search.service.reindex;

import static org.folio.search.service.reindex.ReindexConstants.MERGE_RANGE_ENTITY_TYPES;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.apache.commons.lang3.ThreadUtils;
import org.folio.search.exception.FolioIntegrationException;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.integration.InventoryService;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
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
  private ReindexStatusService statusService;
  @Mock
  private InventoryService inventoryService;
  @Mock
  private ExecutorService reindexExecutor;
  @InjectMocks
  private ReindexService reindexService;

  @Test
  void initFullReindex_negative_shouldFailForEcsMemberTenant() {
    when(consortiumService.isMemberTenantInConsortium(any(String.class))).thenReturn(true);

    assertThrows(RequestValidationException.class, () -> reindexService.initFullReindex("member"),
      "Not allowed to run reindex from member tenant of consortium environment");
  }

  @Test
  void initFullReindex_positive() throws InterruptedException {
    var tenant = "central";
    var member = "member";
    var id = UUID.randomUUID();
    var rangeEntity =
      new MergeRangeEntity(id, ReindexEntityType.INSTANCE, tenant, id, id, Timestamp.from(Instant.now()));

    when(consortiumService.isMemberTenantInConsortium(tenant)).thenReturn(false);
    when(consortiumService.getConsortiumTenants(tenant)).thenReturn(List.of(member));
    when(mergeRangeService.fetchMergeRanges(any(ReindexEntityType.class))).thenReturn(List.of(rangeEntity));
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(reindexExecutor).execute(any());
    final var expectedCallsCount = MERGE_RANGE_ENTITY_TYPES.size();

    reindexService.initFullReindex(tenant);
    ThreadUtils.sleep(Duration.ofSeconds(1));

    verify(mergeRangeService).deleteAllRangeRecords();
    verify(statusService).recreateMergeStatusRecords();
    verify(mergeRangeService).createMergeRanges(tenant);
    verify(executionService).executeAsyncSystemUserScoped(eq(member), any(Runnable.class));
    verify(statusService, times(expectedCallsCount))
      .updateMergeRangesStarted(any(ReindexEntityType.class), eq(1));
    verify(mergeRangeService, times(expectedCallsCount)).fetchMergeRanges(any(ReindexEntityType.class));
    verify(inventoryService, times(expectedCallsCount)).publishReindexRecordsRange(rangeEntity);
    verifyNoMoreInteractions(mergeRangeService);
  }

  @Test
  void initFullReindex_negative_abortMergeAndSetFailedStatusWhenPublishingRangesFailed() throws InterruptedException {
    var tenant = "central";
    var member = "member";
    var id = UUID.randomUUID();
    var rangeEntity =
      new MergeRangeEntity(id, ReindexEntityType.INSTANCE, tenant, id, id, Timestamp.from(Instant.now()));

    when(consortiumService.isMemberTenantInConsortium(tenant)).thenReturn(false);
    when(consortiumService.getConsortiumTenants(tenant)).thenReturn(List.of(member));
    when(mergeRangeService.fetchMergeRanges(any(ReindexEntityType.class))).thenReturn(List.of(rangeEntity));
    doThrow(FolioIntegrationException.class).when(inventoryService).publishReindexRecordsRange(rangeEntity);
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(reindexExecutor).execute(any());

    reindexService.initFullReindex(tenant);
    ThreadUtils.sleep(Duration.ofSeconds(1));

    verify(statusService)
      .updateMergeRangesStarted(any(ReindexEntityType.class), eq(1));
    verify(mergeRangeService).fetchMergeRanges(any(ReindexEntityType.class));
    verify(statusService).updateMergeRangesFailed();
  }
}
