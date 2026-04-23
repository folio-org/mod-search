package org.folio.search.service.reindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.common.TopicPartition;
import org.folio.search.client.InventoryStreamingClient;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.reindex.IndexFamilyEntity;
import org.folio.search.model.reindex.StreamingReindexStatusEntity;
import org.folio.search.model.reindex.runtime.V2ReindexPhaseType;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.repository.IndexRepository;
import org.folio.search.repository.NestedInstanceResourceRepository;
import org.folio.search.service.IndexFamilyService;
import org.folio.search.service.browse.V2BrowseFullRebuildService;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.service.ingest.InstanceSearchIndexingPipeline;
import org.folio.search.service.reindex.jdbc.StreamingReindexStatusRepository;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@UnitTest
@ExtendWith(MockitoExtension.class)
class StreamingReindexServiceTest {

  @Mock
  private InventoryStreamingClient streamingClient;
  @Mock
  private InstanceSearchIndexingPipeline indexingPipeline;
  @Mock
  private IndexFamilyService indexFamilyService;
  @Mock
  private ReindexKafkaConsumerManager consumerManager;
  @Mock
  private StreamingReindexStatusRepository statusRepository;
  @Mock
  private FolioExecutionContext context;
  @Mock
  private Executor streamingReindexExecutor;
  @Mock
  private Executor streamingReindexInstanceBatchExecutor;
  @Mock
  private ConsortiumTenantService consortiumTenantService;
  @Mock
  private SystemUserScopedExecutionService executionService;
  @Mock
  private MultiTenantSearchDocumentConverter searchDocumentConverter;
  @Mock
  private NestedInstanceResourceRepository nestedInstanceRepository;
  @Mock
  private V2BrowseFullRebuildService browseFullRebuildService;
  @Mock
  private IndexRepository indexRepository;
  @Mock
  private V2ReindexRuntimeStatusTracker runtimeStatusTracker;

  @InjectMocks
  private StreamingReindexService service;

  @Test
  void startStreamingReindex_v2_streamsCentralAndMemberTenants() {
    when(indexFamilyService.findByStatusAndVersion(
      eq(IndexFamilyStatus.BUILDING), any(QueryVersion.class))).thenReturn(List.of());
    var tenantId = "central";
    var memberTenantId = "member";
    var familyId = UUID.randomUUID();
    var snapshotCapturedAt = Instant.parse("2026-04-23T00:00:00Z");
    var family = new IndexFamilyEntity(familyId, 3, "target-index", IndexFamilyStatus.BUILDING,
      Timestamp.from(Instant.now()), null, null, QueryVersion.V2);

    when(consortiumTenantService.getConsortiumTenants(tenantId)).thenReturn(List.of(memberTenantId));
    when(indexFamilyService.allocateNewFamily(tenantId, QueryVersion.V2, null)).thenReturn(family);
    when(context.getOkapiUrl()).thenReturn("http://okapi");
    when(context.getToken()).thenReturn("scoped-token");
    when(statusRepository.findByJobIdAndResourceType(any(), eq("instance")))
      .thenReturn(Optional.of(streamingStatus("instance")));
    when(statusRepository.findByJobIdAndResourceType(any(), eq("holding")))
      .thenReturn(Optional.of(streamingStatus("holding")));
    when(statusRepository.findByJobIdAndResourceType(any(), eq("item")))
      .thenReturn(Optional.of(streamingStatus("item")));
    when(consumerManager.getConsumerLagToStagedCutoverSnapshot(familyId)).thenReturn(5L);
    when(consumerManager.getStagedCutoverSnapshotCapturedAt(familyId)).thenReturn(Optional.of(snapshotCapturedAt));
    when(consumerManager.getStagedCutoverSnapshotPartitionCount(familyId)).thenReturn(12);

    doAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call())
      .when(executionService).executeSystemUserScoped(anyString(), any());

    var synchronousService = newService(Runnable::run, Runnable::run);
    synchronousService.startStreamingReindex(tenantId, QueryVersion.V2, null);

    verify(consumerManager).startReindexConsumer(eq(familyId), eq("target-index"),
      argThat(tenantIds -> tenantIds.size() == 2 && tenantIds.containsAll(List.of(tenantId, memberTenantId))),
      eq(3));
    verify(streamingClient).clearCursors(familyId);
    verify(streamingClient).streamInstances(eq("http://okapi"), eq(tenantId), eq("scoped-token"), eq(familyId), any());
    verify(streamingClient).streamHoldings(eq("http://okapi"), eq(tenantId), eq("scoped-token"), eq(familyId), any());
    verify(streamingClient).streamItems(eq("http://okapi"), eq(tenantId), eq("scoped-token"), eq(familyId), any());
    verify(consumerManager).captureStagedCutoverSnapshot(familyId);
    verify(statusRepository).updateStatus(any(), eq("STREAMED"));
    verify(browseFullRebuildService).rebuildBrowse(familyId);
  }

  @Test
  void startStreamingReindex_rejectsExistingBuildingFamily() {
    var tenantId = "central";
    var familyId = UUID.randomUUID();
    var family = new IndexFamilyEntity(familyId, 0, "target-index", IndexFamilyStatus.BUILDING,
      Timestamp.from(Instant.now()), null, null, QueryVersion.V2);

    when(indexFamilyService.findByStatusAndVersion(
      IndexFamilyStatus.BUILDING, QueryVersion.V2)).thenReturn(List.of(family));

    var error = org.junit.jupiter.api.Assertions.assertThrows(RequestValidationException.class,
      () -> service.startStreamingReindex(tenantId, QueryVersion.V2, null));

    org.assertj.core.api.Assertions.assertThat(error.getKey()).isEqualTo("familyId");
    org.assertj.core.api.Assertions.assertThat(error.getValue()).isEqualTo(familyId.toString());
    verify(indexFamilyService, never()).allocateNewFamily(tenantId, QueryVersion.V2, null);
    verify(consumerManager, never()).startReindexConsumer(any(), anyString(), any(), anyInt());
  }

  @Test
  void startStreamingReindex_v2_marksFamilyFailedWhenConsumerStartFails() {
    var tenantId = "central";
    var familyId = UUID.randomUUID();
    var family = new IndexFamilyEntity(familyId, 2, "target-index", IndexFamilyStatus.BUILDING,
      Timestamp.from(Instant.now()), null, null, QueryVersion.V2);

    when(indexFamilyService.findByStatusAndVersion(
      eq(IndexFamilyStatus.BUILDING), any(QueryVersion.class))).thenReturn(List.of());
    when(consortiumTenantService.getConsortiumTenants(tenantId)).thenReturn(List.of());
    when(indexFamilyService.allocateNewFamily(tenantId, QueryVersion.V2, null)).thenReturn(family);
    doThrow(new RuntimeException("consumer boom")).when(consumerManager)
      .startReindexConsumer(eq(familyId), eq("target-index"), eq(List.of(tenantId)), eq(2));

    org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
      () -> service.startStreamingReindex(tenantId, QueryVersion.V2, null));

    verify(statusRepository).updateStatus(any(), eq("FAILED"));
    verify(consumerManager).stopReindexConsumer(familyId);
    verify(indexFamilyService).markFailed(familyId);
  }

  @Test
  void startStreamingReindex_rejectsV1StreamingRequests() {
    var error = org.junit.jupiter.api.Assertions.assertThrows(RequestValidationException.class,
      () -> service.startStreamingReindex("central", QueryVersion.V1, null));

    assertThat(error.getKey()).isEqualTo("queryVersion");
    assertThat(error.getValue()).isEqualTo(QueryVersion.V1.getValue());
    verify(indexFamilyService, never()).allocateNewFamily(anyString(), eq(QueryVersion.V1), any());
    verify(streamingClient, never()).clearCursors(any());
  }

  @Test
  void streamAndAssembleV1_fetchesItemsByHoldingIdsAndAttachesThemToInstances() throws Exception {
    var tenantId = "central";
    var familyId = UUID.randomUUID();
    var jobId = UUID.randomUUID();

    when(statusRepository.findByJobIdAndResourceType(jobId, "instance"))
      .thenReturn(Optional.of(new StreamingReindexStatusEntity(
        UUID.randomUUID(), familyId, jobId, "instance", "PENDING")));
    when(searchDocumentConverter.convert(any())).thenReturn(Map.of());

    doAnswer(invocation -> {
      invocation.<java.util.function.Consumer<List<Map<String, Object>>>>getArgument(4).accept(List.of(
        new java.util.HashMap<>(Map.of("id", "instance-1"))
      ));
      return null;
    }).when(streamingClient).streamInstances(eq("http://okapi"), eq(tenantId), eq("scoped-token"), eq(familyId), any());
    when(streamingClient.fetchHoldingsByInstanceIds("http://okapi", tenantId, "scoped-token", List.of("instance-1")))
      .thenReturn(List.of(Map.of("id", "holding-1", "instanceId", "instance-1")));
    when(streamingClient.fetchItemsByHoldingIds("http://okapi", tenantId, "scoped-token", List.of("holding-1")))
      .thenReturn(List.of(Map.of("id", "item-1", "holdingsRecordId", "holding-1")));

    invokeStreamAndAssembleV1(service, jobId, familyId, tenantId, "target-index", "http://okapi", "scoped-token");

    verify(streamingClient).fetchItemsByHoldingIds("http://okapi", tenantId, "scoped-token", List.of("holding-1"));
    verify(streamingClient, never()).streamHoldings(anyString(), anyString(), anyString(), any(UUID.class), any());
    verify(streamingClient, never()).streamItems(anyString(), anyString(), anyString(), any(UUID.class), any());

    var eventsCaptor = ArgumentCaptor.forClass(List.class);
    verify(searchDocumentConverter, times(1)).convert(eventsCaptor.capture());

    @SuppressWarnings("unchecked")
    var events = (List<org.folio.search.domain.dto.ResourceEvent>) eventsCaptor.getValue();
    assertThat(events).hasSize(1);
    @SuppressWarnings("unchecked")
    var payload = (Map<String, Object>) events.getFirst().getNew();
    assertThat(payload.get("holdings")).isEqualTo(List.of(Map.of("id", "holding-1", "instanceId", "instance-1")));
    assertThat(payload.get("items")).isEqualTo(List.of(Map.of("id", "item-1", "holdingsRecordId", "holding-1")));
  }

  @Test
  void resumeStreamingReindex_resumesKafkaConsumerWhenBackfillCompleted() {
    var tenantId = "central";
    var familyId = UUID.randomUUID();
    var jobId = UUID.randomUUID();
    var snapshotCapturedAt = Instant.parse("2026-04-23T00:00:00Z");
    var family = new IndexFamilyEntity(familyId, 3, "target-index", IndexFamilyStatus.BUILDING,
      Timestamp.from(Instant.now()), null, null, QueryVersion.V2);
    var statuses = List.of(
      completedStatus(jobId, familyId, "instance"),
      completedStatus(jobId, familyId, "holding"),
      completedStatus(jobId, familyId, "item")
    );

    var committedOffsets = Map.of(new TopicPartition("topic", 0), 100L);
    when(context.getTenantId()).thenReturn(tenantId);
    when(indexFamilyService.findById(familyId)).thenReturn(Optional.of(family));
    when(consortiumTenantService.getConsortiumTenants(tenantId)).thenReturn(List.of());
    when(statusRepository.findByFamilyId(familyId)).thenReturn(statuses);
    when(consumerManager.getCommittedOffsets(familyId, 3)).thenReturn(committedOffsets);
    when(consumerManager.getConsumerLagToStagedCutoverSnapshot(familyId)).thenReturn(7L);
    when(consumerManager.getStagedCutoverSnapshotCapturedAt(familyId)).thenReturn(Optional.of(snapshotCapturedAt));
    when(consumerManager.getStagedCutoverSnapshotPartitionCount(familyId)).thenReturn(10);

    var actual = service.resumeStreamingReindex(familyId);

    assertThat(actual.jobId()).isEqualTo(jobId);
    assertThat(actual.familyId()).isEqualTo(familyId);
    verify(consumerManager).resumeReindexConsumer(
      eq(familyId), eq("target-index"), eq(List.of(tenantId)), eq(3),
      eq(QueryVersion.V2), eq(committedOffsets));
    verify(consumerManager).refreshStagedCutoverSnapshot(familyId);
    verify(indexFamilyService).updateStatus(familyId, IndexFamilyStatus.STAGED);
    verify(statusRepository, never()).deleteByFamilyId(familyId);
    verify(streamingClient, never()).streamInstances(anyString(), anyString(), anyString(), any(UUID.class), any());
  }

  @Test
  void resumeStreamingReindex_restartsFromScratchWhenBackfillNotCompleted() {
    var tenantId = "central";
    var familyId = UUID.randomUUID();
    var family = new IndexFamilyEntity(familyId, 3, "target-index", IndexFamilyStatus.BUILDING,
      Timestamp.from(Instant.now()), null, null, QueryVersion.V2);

    when(context.getTenantId()).thenReturn(tenantId);
    when(indexFamilyService.findById(familyId)).thenReturn(Optional.of(family));
    when(consortiumTenantService.getConsortiumTenants(tenantId)).thenReturn(List.of());
    when(statusRepository.findByFamilyId(familyId)).thenReturn(List.of(
      statusWithState(UUID.randomUUID(), familyId, "instance", "COMPLETED"),
      statusWithState(UUID.randomUUID(), familyId, "holding", "IN_PROGRESS"),
      statusWithState(UUID.randomUUID(), familyId, "item", "PENDING")
    ));
    when(context.getOkapiUrl()).thenReturn("http://okapi");
    when(context.getToken()).thenReturn("scoped-token");
    when(statusRepository.findByJobIdAndResourceType(any(), eq("instance")))
      .thenReturn(Optional.of(streamingStatus("instance")));
    when(statusRepository.findByJobIdAndResourceType(any(), eq("holding")))
      .thenReturn(Optional.of(streamingStatus("holding")));
    when(statusRepository.findByJobIdAndResourceType(any(), eq("item")))
      .thenReturn(Optional.of(streamingStatus("item")));

    doAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call())
      .when(executionService).executeSystemUserScoped(anyString(), any());

    var restartService = newService(Runnable::run, streamingReindexInstanceBatchExecutor);
    restartService.resumeStreamingReindex(familyId);

    verify(statusRepository).deleteByFamilyId(familyId);
    verify(consumerManager).startReindexConsumer(eq(familyId), eq("target-index"),
      eq(List.of(tenantId)), eq(3));
    verify(streamingClient, never()).clearCursors(familyId);
    verify(streamingClient).streamInstances(eq("http://okapi"), eq(tenantId), eq("scoped-token"), eq(familyId), any());
  }

  @Test
  void resumeStreamingReindex_resumesFailedFamilyFromCommittedOffsets() {
    var tenantId = "central";
    var familyId = UUID.randomUUID();
    var jobId = UUID.randomUUID();
    var family = new IndexFamilyEntity(familyId, 4, "target-index", IndexFamilyStatus.FAILED,
      Timestamp.from(Instant.now()), null, null, QueryVersion.V1);

    var committedOffsets = Map.of(new TopicPartition("topic", 0), 50L);
    when(context.getTenantId()).thenReturn(tenantId);
    when(indexFamilyService.findById(familyId)).thenReturn(Optional.of(family));
    when(consortiumTenantService.getConsortiumTenants(tenantId)).thenReturn(List.of());
    when(statusRepository.findByFamilyId(familyId))
      .thenReturn(List.of(completedStatus(jobId, familyId, "instance")));
    when(consumerManager.getCommittedOffsets(familyId, 4)).thenReturn(committedOffsets);

    var actual = service.resumeStreamingReindex(familyId);

    assertThat(actual.jobId()).isEqualTo(jobId);
    verify(indexFamilyService).updateStatus(familyId, IndexFamilyStatus.BUILDING);
    verify(indexFamilyService).updateStatus(familyId, IndexFamilyStatus.STAGED);
    verify(consumerManager).resumeReindexConsumer(
      eq(familyId), eq("target-index"), eq(List.of(tenantId)), eq(4),
      eq(QueryVersion.V1), eq(committedOffsets));
    verify(statusRepository, never()).deleteByFamilyId(familyId);
  }

  @Test
  void resumeStreamingReindex_clearsTrackedFailureForV2FamilyResumedFromCommittedOffsets() {
    var tenantId = "central";
    var familyId = UUID.randomUUID();
    var jobId = UUID.randomUUID();
    var snapshotCapturedAt = Instant.parse("2026-04-23T00:00:00Z");
    var family = new IndexFamilyEntity(familyId, 4, "target-index", IndexFamilyStatus.FAILED,
      Timestamp.from(Instant.now()), null, null, QueryVersion.V2);
    var committedOffsets = Map.of(new TopicPartition("topic", 0), 50L);

    when(context.getTenantId()).thenReturn(tenantId);
    when(indexFamilyService.findById(familyId)).thenReturn(Optional.of(family));
    when(consortiumTenantService.getConsortiumTenants(tenantId)).thenReturn(List.of());
    when(statusRepository.findByFamilyId(familyId)).thenReturn(List.of(
      completedStatus(jobId, familyId, "instance"),
      completedStatus(jobId, familyId, "holding"),
      completedStatus(jobId, familyId, "item")
    ));
    when(consumerManager.getCommittedOffsets(familyId, 4)).thenReturn(committedOffsets);
    when(consumerManager.getConsumerLagToStagedCutoverSnapshot(familyId)).thenReturn(2L);
    when(consumerManager.getStagedCutoverSnapshotCapturedAt(familyId)).thenReturn(Optional.of(snapshotCapturedAt));
    when(consumerManager.getStagedCutoverSnapshotPartitionCount(familyId)).thenReturn(8);

    service.resumeStreamingReindex(familyId);

    verify(consumerManager).refreshStagedCutoverSnapshot(familyId);
    verify(runtimeStatusTracker).resumeFamily(familyId, IndexFamilyStatus.STAGED);
    verify(runtimeStatusTracker).startPhase(eq(familyId), eq(V2ReindexPhaseType.CATCH_UP), eq(1L), any());
  }

  @Test
  void resumeStreamingReindex_recreatesSnapshotForResumedStagedV2Family() {
    var tenantId = "central";
    var familyId = UUID.randomUUID();
    var jobId = UUID.randomUUID();
    var snapshotCapturedAt = Instant.parse("2026-04-23T00:00:00Z");
    var family = new IndexFamilyEntity(familyId, 3, "target-index", IndexFamilyStatus.STAGED,
      Timestamp.from(Instant.now()), null, null, QueryVersion.V2);
    var statuses = List.of(
      completedStatus(jobId, familyId, "instance"),
      completedStatus(jobId, familyId, "holding"),
      completedStatus(jobId, familyId, "item")
    );
    var committedOffsets = Map.of(new TopicPartition("topic", 0), 100L);

    when(context.getTenantId()).thenReturn(tenantId);
    when(indexFamilyService.findById(familyId)).thenReturn(Optional.of(family));
    when(consortiumTenantService.getConsortiumTenants(tenantId)).thenReturn(List.of());
    when(statusRepository.findByFamilyId(familyId)).thenReturn(statuses);
    when(consumerManager.getCommittedOffsets(familyId, 3)).thenReturn(committedOffsets);
    when(consumerManager.getConsumerLagToStagedCutoverSnapshot(familyId)).thenReturn(1L);
    when(consumerManager.getStagedCutoverSnapshotCapturedAt(familyId)).thenReturn(Optional.of(snapshotCapturedAt));
    when(consumerManager.getStagedCutoverSnapshotPartitionCount(familyId)).thenReturn(6);

    service.resumeStreamingReindex(familyId);

    verify(consumerManager).resumeReindexConsumer(
      eq(familyId), eq("target-index"), eq(List.of(tenantId)), eq(3),
      eq(QueryVersion.V2), eq(committedOffsets));
    verify(consumerManager).refreshStagedCutoverSnapshot(familyId);
    verify(indexFamilyService, never()).updateStatus(familyId, IndexFamilyStatus.STAGED);
  }

  @Test
  void streamFlatResource_flagOffKeepsInstanceBatchesSequential() throws Exception {
    var familyId = UUID.randomUUID();
    var jobId = UUID.randomUUID();

    when(statusRepository.findByJobIdAndResourceType(jobId, "instance"))
      .thenReturn(Optional.of(streamingStatus("instance")));
    when(indexingPipeline.indexBatchToFamily(eq("instance"), anyList(), eq("tenant"), eq("target-index")))
      .thenReturn(batchProfiling(1));
    doAnswer(invocation -> {
      invocation.<java.util.function.Consumer<List<Map<String, Object>>>>getArgument(4)
        .accept(List.of(Map.of("id", "instance-1")));
      return null;
    }).when(streamingClient).streamInstances(
      eq("http://okapi"), eq("tenant"), eq("token"), eq(familyId), any());

    invokeStreamFlatResource(service, jobId, familyId, "INSTANCE", "tenant", "target-index", "http://okapi", "token");

    verify(indexingPipeline).indexBatchToFamily(eq("instance"), anyList(), eq("tenant"), eq("target-index"));
    verify(statusRepository).updateResourceStatus(any(), eq("COMPLETED"));
  }

  @Test
  void streamFlatResource_flagOnWaitsForInstanceDrainBeforeCompletion() throws Exception {
    final var familyId = UUID.randomUUID();
    final var jobId = UUID.randomUUID();
    var queueingExecutor = new QueueingExecutor();
    var parallelService = newService(Runnable::run, queueingExecutor);

    ReflectionTestUtils.setField(parallelService, "instanceBoundedParallelEnabled", true);
    ReflectionTestUtils.setField(parallelService, "instanceBoundedParallelMaxInFlight", 2);
    when(statusRepository.findByJobIdAndResourceType(jobId, "instance"))
      .thenReturn(Optional.of(streamingStatus("instance")));
    when(indexingPipeline.indexBatchToFamily(eq("instance"), anyList(), eq("tenant"), eq("target-index")))
      .thenAnswer(invocation -> batchProfiling(invocation.<List<?>>getArgument(1).size()));
    doAnswer(invocation -> {
      var consumer = invocation.<java.util.function.Consumer<List<Map<String, Object>>>>getArgument(4);
      consumer.accept(List.of(Map.of("id", "instance-1")));
      consumer.accept(List.of(Map.of("id", "instance-2")));
      return null;
    }).when(streamingClient).streamInstances(
      eq("http://okapi"), eq("tenant"), eq("token"), eq(familyId), any());

    var failure = new AtomicReference<Throwable>();
    var streamThread = new Thread(() -> {
      try {
        invokeStreamFlatResource(
          parallelService, jobId, familyId, "INSTANCE", "tenant", "target-index", "http://okapi", "token");
      } catch (Throwable error) {
        failure.set(error);
      }
    });

    streamThread.start();

    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(queueingExecutor.size()).isEqualTo(2));
    verify(statusRepository, never()).updateResourceStatus(any(), eq("COMPLETED"));

    queueingExecutor.runNext();
    verify(statusRepository, never()).updateResourceStatus(any(), eq("COMPLETED"));

    queueingExecutor.runNext();
    streamThread.join(TimeUnit.SECONDS.toMillis(1));

    assertThat(streamThread.isAlive()).isFalse();
    assertThat(failure.get()).isNull();
    verify(statusRepository).updateResourceStatus(any(), eq("COMPLETED"));
  }

  @Test
  void streamFlatResource_flagOnUsesConfiguredInFlightLimit() throws Exception {
    final var familyId = UUID.randomUUID();
    final var jobId = UUID.randomUUID();
    var queueingExecutor = new QueueingExecutor();
    var parallelService = newService(Runnable::run, queueingExecutor);

    ReflectionTestUtils.setField(parallelService, "instanceBoundedParallelEnabled", true);
    ReflectionTestUtils.setField(parallelService, "instanceBoundedParallelMaxInFlight", 3);
    when(statusRepository.findByJobIdAndResourceType(jobId, "instance"))
      .thenReturn(Optional.of(streamingStatus("instance")));
    when(indexingPipeline.indexBatchToFamily(eq("instance"), anyList(), eq("tenant"), eq("target-index")))
      .thenAnswer(invocation -> batchProfiling(invocation.<List<?>>getArgument(1).size()));
    doAnswer(invocation -> {
      var consumer = invocation.<java.util.function.Consumer<List<Map<String, Object>>>>getArgument(4);
      consumer.accept(List.of(Map.of("id", "instance-1")));
      consumer.accept(List.of(Map.of("id", "instance-2")));
      consumer.accept(List.of(Map.of("id", "instance-3")));
      return null;
    }).when(streamingClient).streamInstances(
      eq("http://okapi"), eq("tenant"), eq("token"), eq(familyId), any());

    var failure = new AtomicReference<Throwable>();
    var streamThread = new Thread(() -> {
      try {
        invokeStreamFlatResource(
          parallelService, jobId, familyId, "INSTANCE", "tenant", "target-index", "http://okapi", "token");
      } catch (Throwable error) {
        failure.set(error);
      }
    });

    streamThread.start();

    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(queueingExecutor.size()).isEqualTo(3));
    verify(statusRepository, never()).updateResourceStatus(any(), eq("COMPLETED"));

    queueingExecutor.runNext();
    queueingExecutor.runNext();
    verify(statusRepository, never()).updateResourceStatus(any(), eq("COMPLETED"));

    queueingExecutor.runNext();
    streamThread.join(TimeUnit.SECONDS.toMillis(1));

    assertThat(streamThread.isAlive()).isFalse();
    assertThat(failure.get()).isNull();
    verify(statusRepository).updateResourceStatus(any(), eq("COMPLETED"));
  }

  @Test
  void streamFlatResource_flagOnWithMaxInFlightOneStillOffloadsInstanceBatches() throws Exception {
    final var familyId = UUID.randomUUID();
    final var jobId = UUID.randomUUID();
    var queueingExecutor = new QueueingExecutor();
    var parallelService = newService(Runnable::run, queueingExecutor);

    ReflectionTestUtils.setField(parallelService, "instanceBoundedParallelEnabled", true);
    ReflectionTestUtils.setField(parallelService, "instanceBoundedParallelMaxInFlight", 1);
    when(statusRepository.findByJobIdAndResourceType(jobId, "instance"))
      .thenReturn(Optional.of(streamingStatus("instance")));
    when(indexingPipeline.indexBatchToFamily(eq("instance"), anyList(), eq("tenant"), eq("target-index")))
      .thenAnswer(invocation -> batchProfiling(invocation.<List<?>>getArgument(1).size()));
    doAnswer(invocation -> {
      invocation.<java.util.function.Consumer<List<Map<String, Object>>>>getArgument(4)
        .accept(List.of(Map.of("id", "instance-1")));
      return null;
    }).when(streamingClient).streamInstances(
      eq("http://okapi"), eq("tenant"), eq("token"), eq(familyId), any());

    var failure = new AtomicReference<Throwable>();
    var streamThread = new Thread(() -> {
      try {
        invokeStreamFlatResource(
          parallelService, jobId, familyId, "INSTANCE", "tenant", "target-index", "http://okapi", "token");
      } catch (Throwable error) {
        failure.set(error);
      }
    });

    streamThread.start();

    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(queueingExecutor.size()).isEqualTo(1));
    verify(statusRepository, never()).updateResourceStatus(any(), eq("COMPLETED"));

    queueingExecutor.runNext();
    streamThread.join(TimeUnit.SECONDS.toMillis(1));

    assertThat(streamThread.isAlive()).isFalse();
    assertThat(failure.get()).isNull();
    verify(statusRepository).updateResourceStatus(any(), eq("COMPLETED"));
  }

  @Test
  void streamFlatResource_flagOnDrainsQueuedInstanceWorkBeforeFailingResource() throws Exception {
    final var familyId = UUID.randomUUID();
    final var jobId = UUID.randomUUID();
    var queueingExecutor = new QueueingExecutor();
    var parallelService = newService(Runnable::run, queueingExecutor);

    ReflectionTestUtils.setField(parallelService, "instanceBoundedParallelEnabled", true);
    ReflectionTestUtils.setField(parallelService, "instanceBoundedParallelMaxInFlight", 2);
    when(statusRepository.findByJobIdAndResourceType(jobId, "instance"))
      .thenReturn(Optional.of(streamingStatus("instance")));
    when(indexingPipeline.indexBatchToFamily(eq("instance"), anyList(), eq("tenant"), eq("target-index")))
      .thenAnswer(invocation -> {
        var page = invocation.<List<Map<String, Object>>>getArgument(1);
        var id = page.getFirst().get("id");
        if ("instance-1".equals(id)) {
          throw new RuntimeException("boom");
        }
        return batchProfiling(page.size());
      });
    doAnswer(invocation -> {
      var consumer = invocation.<java.util.function.Consumer<List<Map<String, Object>>>>getArgument(4);
      consumer.accept(List.of(Map.of("id", "instance-1")));
      consumer.accept(List.of(Map.of("id", "instance-2")));
      return null;
    }).when(streamingClient).streamInstances(
      eq("http://okapi"), eq("tenant"), eq("token"), eq(familyId), any());

    var failure = new AtomicReference<Throwable>();
    var streamThread = new Thread(() -> {
      try {
        invokeStreamFlatResource(
          parallelService, jobId, familyId, "INSTANCE", "tenant", "target-index", "http://okapi", "token");
      } catch (Throwable error) {
        failure.set(error);
      }
    });

    streamThread.start();

    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(queueingExecutor.size()).isEqualTo(2));
    verify(statusRepository, never()).updateResourceStatus(any(), eq("FAILED"));

    queueingExecutor.runNext();
    verify(statusRepository, never()).updateResourceStatus(any(), eq("FAILED"));

    queueingExecutor.runNext();
    streamThread.join(TimeUnit.SECONDS.toMillis(1));

    assertThat(streamThread.isAlive()).isFalse();
    assertThat(failure.get()).isNotNull();
    verify(statusRepository).updateResourceStatus(any(), eq("FAILED"));
    verify(statusRepository, never()).updateResourceStatus(any(), eq("COMPLETED"));
    verify(indexingPipeline, times(2))
      .indexBatchToFamily(eq("instance"), anyList(), eq("tenant"), eq("target-index"));
  }

  @Test
  void awaitNextInstanceBatch_rethrowsErrorCauses() throws Exception {
    var inFlight = new ArrayDeque<CompletableFuture<Void>>();
    var failedBatch = new CompletableFuture<Void>();
    failedBatch.completeExceptionally(new AssertionError("boom"));
    inFlight.add(failedBatch);
    var stopSubmitting = new java.util.concurrent.atomic.AtomicBoolean();

    var method = StreamingReindexService.class.getDeclaredMethod(
      "awaitNextInstanceBatch", ArrayDeque.class, java.util.concurrent.atomic.AtomicBoolean.class);
    method.setAccessible(true);

    var thrown = org.junit.jupiter.api.Assertions.assertThrows(java.lang.reflect.InvocationTargetException.class,
      () -> method.invoke(service, inFlight, stopSubmitting));

    assertThat(thrown.getCause()).isInstanceOf(AssertionError.class).hasMessage("boom");
  }

  private static StreamingReindexStatusEntity streamingStatus(String resourceType) {
    return new StreamingReindexStatusEntity(
      UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), resourceType, "PENDING");
  }

  private static StreamingReindexStatusEntity completedStatus(UUID jobId, UUID familyId, String resourceType) {
    return statusWithState(jobId, familyId, resourceType, "COMPLETED");
  }

  private static StreamingReindexStatusEntity statusWithState(UUID jobId, UUID familyId,
                                                              String resourceType, String status) {
    return new StreamingReindexStatusEntity(UUID.randomUUID(), familyId, jobId, resourceType, status);
  }

  private StreamingReindexService newService(Executor outerExecutor, Executor batchExecutor) {
    return new StreamingReindexService(
      streamingClient, indexingPipeline, indexFamilyService, consumerManager, statusRepository, context,
      outerExecutor, batchExecutor,
      consortiumTenantService, executionService, searchDocumentConverter, nestedInstanceRepository,
      browseFullRebuildService, indexRepository, runtimeStatusTracker);
  }

  private static InstanceSearchIndexingPipeline.BatchProfiling batchProfiling(int docs) {
    return new InstanceSearchIndexingPipeline.BatchProfiling(1, 1, 1, 1, docs);
  }

  private static void invokeStreamFlatResource(StreamingReindexService service, UUID jobId, UUID familyId,
                                               String resourceType, String tenantId, String targetIndex,
                                               String okapiUrl, String token) throws Exception {
    var enumClass = Class.forName(StreamingReindexService.class.getName() + "$StreamingResource");
    @SuppressWarnings("unchecked")
    var enumValue = Enum.valueOf((Class<Enum>) enumClass, resourceType);
    var method = StreamingReindexService.class.getDeclaredMethod(
      "streamFlatResource", UUID.class, UUID.class, enumClass, String.class, String.class, String.class,
      String.class);
    method.setAccessible(true);
    method.invoke(service, jobId, familyId, enumValue, tenantId, targetIndex, okapiUrl, token);
  }

  private static void invokeStreamAndAssembleV1(StreamingReindexService service, UUID jobId, UUID familyId,
                                                String tenantId, String targetIndex, String okapiUrl,
                                                String token) throws Exception {
    var method = StreamingReindexService.class.getDeclaredMethod(
      "streamAndAssembleV1", UUID.class, UUID.class, String.class, String.class, String.class, String.class);
    method.setAccessible(true);
    method.invoke(service, jobId, familyId, tenantId, targetIndex, okapiUrl, token);
  }

  private static final class QueueingExecutor implements Executor {

    private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();

    @Override
    public void execute(Runnable command) {
      tasks.add(command);
    }

    private int size() {
      return tasks.size();
    }

    private void runNext() throws InterruptedException {
      var task = tasks.poll(1, TimeUnit.SECONDS);
      assertThat(task).isNotNull();
      task.run();
    }
  }
}
