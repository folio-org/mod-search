package org.folio.search.service.reindex;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.common.TopicPartition;
import org.folio.search.client.InventoryStreamingClient;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.reindex.IndexFamilyEntity;
import org.folio.search.model.reindex.StreamingReindexStatusEntity;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.model.types.ResourceType;
import org.folio.search.model.types.StreamingReindexStatus;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class StreamingReindexService {

  private final InventoryStreamingClient streamingClient;
  private final InstanceSearchIndexingPipeline indexingPipeline;
  private final IndexFamilyService indexFamilyService;
  private final ReindexKafkaConsumerManager consumerManager;
  private final StreamingReindexStatusRepository statusRepository;
  private final FolioExecutionContext context;
  private final Executor streamingReindexExecutor;
  private final ConsortiumTenantService consortiumTenantService;
  private final SystemUserScopedExecutionService executionService;
  private final MultiTenantSearchDocumentConverter searchDocumentConverter;
  private final NestedInstanceResourceRepository nestedInstanceRepository;
  private final V2BrowseFullRebuildService browseFullRebuildService;
  private final IndexRepository indexRepository;

  public StreamingReindexService(InventoryStreamingClient streamingClient,
                                 InstanceSearchIndexingPipeline indexingPipeline,
                                 IndexFamilyService indexFamilyService,
                                 ReindexKafkaConsumerManager consumerManager,
                                 StreamingReindexStatusRepository statusRepository,
                                 FolioExecutionContext context,
                                 @Qualifier("streamingReindexExecutor") Executor streamingReindexExecutor,
                                 ConsortiumTenantService consortiumTenantService,
                                 SystemUserScopedExecutionService executionService,
                                 MultiTenantSearchDocumentConverter searchDocumentConverter,
                                 NestedInstanceResourceRepository nestedInstanceRepository,
                                 V2BrowseFullRebuildService browseFullRebuildService,
                                 IndexRepository indexRepository) {
    this.streamingClient = streamingClient;
    this.indexingPipeline = indexingPipeline;
    this.indexFamilyService = indexFamilyService;
    this.consumerManager = consumerManager;
    this.statusRepository = statusRepository;
    this.context = context;
    this.streamingReindexExecutor = streamingReindexExecutor;
    this.consortiumTenantService = consortiumTenantService;
    this.executionService = executionService;
    this.searchDocumentConverter = searchDocumentConverter;
    this.nestedInstanceRepository = nestedInstanceRepository;
    this.browseFullRebuildService = browseFullRebuildService;
    this.indexRepository = indexRepository;
  }

  public StreamingReindexJob startStreamingReindex(String tenantId, QueryVersion version,
                                                   IndexSettings indexSettings) {
    log.info("startStreamingReindex:: initiating streaming reindex [tenant: {}, version: {}]", tenantId, version);

    var existingBuildingFamilies = indexFamilyService.findByTenantIdAndStatusAndVersion(
      tenantId, IndexFamilyStatus.BUILDING, version);
    if (!existingBuildingFamilies.isEmpty()) {
      var existingFamily = existingBuildingFamilies.getFirst();
      throw new RequestValidationException(
        "A BUILDING family already exists. Use the resume endpoint to continue, "
        + "or delete it to start fresh.", "familyId", existingFamily.getId().toString());
    }

    var reindexTenants = resolveReindexTenants(tenantId);
    var family = indexFamilyService.allocateNewFamily(tenantId, version, indexSettings);
    var jobId = UUID.randomUUID();

    try {
      createStatusRecords(jobId, family, version);
      streamingClient.clearCursors(family.getId());
      launchReindex(jobId, family, reindexTenants);
    } catch (Exception e) {
      handleReindexFailure(jobId, family.getId(), e);
      throw e;
    }

    log.info("startStreamingReindex:: reindex job submitted [jobId: {}, familyId: {}, tenant: {}, version: {}]",
      jobId, family.getId(), tenantId, version);

    return new StreamingReindexJob(jobId, family.getId());
  }

  private void executeV2StreamingReindex(UUID jobId, IndexFamilyEntity family,
                                          List<String> tenantIds, String okapiUrl) {
    var targetIndex = family.getIndexName();

    try {
      if (tenantIds.size() > 1) {
        log.info("executeV2StreamingReindex:: consortium mode, streaming {} tenants [jobId: {}]",
          tenantIds.size(), jobId);
      }

      disableRefreshInterval(targetIndex);
      try {
        var t0 = System.nanoTime();
        for (var tenantId : tenantIds) {
          executionService.executeSystemUserScoped(tenantId, () -> {
            streamAllFlatResources(jobId, family.getId(), tenantId, targetIndex, okapiUrl, context.getToken());
            return null;
          });
        }
        log.info("executeV2StreamingReindex:: streaming done [jobId: {}, elapsed: {}ms]",
          jobId, ms(System.nanoTime() - t0));

        t0 = System.nanoTime();
        consumerManager.captureTargetOffsets(family.getId(), tenantIds);
        log.info("executeV2StreamingReindex:: offsets captured [jobId: {}, elapsed: {}ms]",
          jobId, ms(System.nanoTime() - t0));
      } finally {
        enableRefreshInterval(targetIndex);
      }

      var t0 = System.nanoTime();
      browseFullRebuildService.rebuildBrowse(family.getId());
      log.info("executeV2StreamingReindex:: browse rebuilt [jobId: {}, elapsed: {}ms]",
        jobId, ms(System.nanoTime() - t0));

      updateJobStatus(jobId, StreamingReindexStatus.STREAMED, null);
    } catch (Exception e) {
      log.error("executeV2StreamingReindex:: streaming reindex failed [jobId: {}, familyId: {}]",
        jobId, family.getId(), e);
      handleReindexFailure(jobId, family.getId(), e);
    }
  }

  private void executeV1StreamingReindex(UUID jobId, IndexFamilyEntity family,
                                          List<String> tenantIds, String okapiUrl) {
    var targetIndex = family.getIndexName();

    try {
      // Phase 1: Capture start offsets BEFORE streaming begins
      final var startOffsets = consumerManager.captureCurrentOffsets(tenantIds);

      if (tenantIds.size() > 1) {
        log.info("executeV1StreamingReindex:: consortium mode, streaming {} tenants [jobId: {}]",
          tenantIds.size(), jobId);
      }

      // Phase 2: Stream backfill (no temp consumer running)
      for (var tenantId : tenantIds) {
        executionService.executeSystemUserScoped(tenantId, () -> {
          streamAndAssembleV1(jobId, family.getId(), tenantId, targetIndex, okapiUrl, context.getToken());
          return null;
        });
      }

      // Phase 3: Capture target offsets (where topics are NOW, after streaming)
      consumerManager.captureTargetOffsets(family.getId(), tenantIds);
      updateJobStatus(jobId, StreamingReindexStatus.STREAMED, null);

      // Phase 4: Start temp consumer using pre-captured start offsets from Phase 1
      consumerManager.startReindexConsumer(family.getId(), targetIndex,
        tenantIds, family.getGeneration(), QueryVersion.V1, startOffsets);

      log.info("executeV1StreamingReindex:: streaming complete, temp consumer started [jobId: {}, familyId: {}]",
        jobId, family.getId());
    } catch (Exception e) {
      log.error("executeV1StreamingReindex:: streaming reindex failed [jobId: {}, familyId: {}]",
        jobId, family.getId(), e);
      handleReindexFailure(jobId, family.getId(), e);
    }
  }

  private void streamAndAssembleV1(UUID jobId, UUID familyId, String tenantId,
                                    String targetIndex, String okapiUrl, String token) {
    log.info("streamAndAssembleV1:: streaming instances for V1 [tenant: {}, target: {}]",
      tenantId, targetIndex);

    var statusId = statusRepository.findByJobIdAndResourceType(jobId, StreamingResource.INSTANCE.value)
      .map(StreamingReindexStatusEntity::getId)
      .orElse(null);

    if (statusId != null) {
      updateResourceStatus(statusId, StreamingReindexStatus.IN_PROGRESS, null);
    }

    var recordCounter = new long[]{0};
    var failedBatchCounter = new long[]{0};

    streamingClient.streamInstances(okapiUrl, tenantId, token, familyId, instanceBatch -> {
      try {
        // Collect instance IDs from batch
        var instanceIds = instanceBatch.stream()
          .map(m -> (String) m.get("id"))
          .filter(Objects::nonNull)
          .toList();

        var holdings = streamingClient.fetchHoldingsByInstanceIds(okapiUrl, tenantId, token, instanceIds);
        var holdingIds = holdings.stream()
          .map(holding -> (String) holding.get("id"))
          .filter(Objects::nonNull)
          .toList();
        var items = streamingClient.fetchItemsByHoldingIds(okapiUrl, tenantId, token, holdingIds);

        // Group by instanceId
        var holdingsByInstance = holdings.stream()
          .collect(Collectors.groupingBy(h -> (String) h.get("instanceId")));
        var instanceIdsByHolding = holdings.stream()
          .map(holding -> Map.entry((String) holding.get("id"), (String) holding.get("instanceId")))
          .filter(entry -> entry.getKey() != null && entry.getValue() != null)
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
        var itemsByInstance = items.stream()
          .map(item -> {
            // Items may have instanceId directly or through holdingsRecordId
            var instId = (String) item.get("instanceId");
            if (instId != null) {
              return Map.entry(instId, item);
            }

            var holdingsRecordId = (String) item.get("holdingsRecordId");
            return Map.entry(holdingsRecordId != null ? instanceIdsByHolding.get(holdingsRecordId) : null, item);
          })
          .filter(entry -> entry.getKey() != null)
          .collect(Collectors.groupingBy(Map.Entry::getKey,
            Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        // Assemble nested docs and create ResourceEvents
        var events = instanceBatch.stream()
          .map(instance -> {
            var id = (String) instance.get("id");
            if (id == null) {
              return null;
            }
            instance.put("holdings", holdingsByInstance.getOrDefault(id, List.of()));
            instance.put("items", itemsByInstance.getOrDefault(id, List.of()));
            return new ResourceEvent()
              .id(id)
              .type(ResourceEventType.CREATE)
              .resourceName(ResourceType.INSTANCE.getName())
              .tenant(tenantId)
              ._new(instance);
          })
          .filter(Objects::nonNull)
          .toList();

        // Convert and write to BUILDING V1 index
        var documents = searchDocumentConverter.convert(events);
        for (var docList : documents.values()) {
          nestedInstanceRepository.indexResources(docList, targetIndex);
        }

        recordCounter[0] += instanceBatch.size();
        if (statusId != null) {
          statusRepository.updateRecordsProcessed(statusId, recordCounter[0]);
        }
      } catch (Exception e) {
        failedBatchCounter[0]++;
        if (statusId != null) {
          statusRepository.updateFailedBatches(statusId, failedBatchCounter[0]);
        }
        log.error("streamAndAssembleV1:: batch failed [tenant: {}, batchSize: {}]",
          tenantId, instanceBatch.size(), e);
        throw e instanceof RuntimeException
          ? (RuntimeException) e
          : new SearchServiceException("Failed to index V1 batch", e);
      }
    });

    if (statusId != null) {
      updateResourceStatus(statusId, StreamingReindexStatus.COMPLETED, null);
    }

    log.info("streamAndAssembleV1:: completed [tenant: {}, records: {}, failedBatches: {}]",
      tenantId, recordCounter[0], failedBatchCounter[0]);
  }

  private void streamAllFlatResources(UUID jobId, UUID familyId, String tenantId, String targetIndex,
                                       String okapiUrl, String token) {
    for (var resource : flatStreamingResources()) {
      streamFlatResource(jobId, familyId, resource, tenantId, targetIndex, okapiUrl, token);
    }
  }

  private void streamFlatResource(UUID jobId, UUID familyId, StreamingResource resourceType, String tenantId,
                                   String targetIndex, String okapiUrl, String token) {
    log.info("streamFlatResource:: streaming [resource: {}, tenant: {}, target: {}]",
      resourceType.value, tenantId, targetIndex);

    var statusId = statusRepository.findByJobIdAndResourceType(jobId, resourceType.value)
      .map(StreamingReindexStatusEntity::getId)
      .orElse(null);

    if (statusId != null) {
      updateResourceStatus(statusId, StreamingReindexStatus.IN_PROGRESS, null);
    }

    final var resourceStart = System.nanoTime();
    var recordCounter = new long[]{0};
    var failedBatchCounter = new long[]{0};

    var batchCallback = createFlatBatchCallback(resourceType, tenantId, targetIndex, statusId,
      recordCounter, failedBatchCounter);

    try {
      switch (resourceType) {
        case INSTANCE -> streamingClient.streamInstances(okapiUrl, tenantId, token, familyId, batchCallback);
        case HOLDING -> streamingClient.streamHoldings(okapiUrl, tenantId, token, familyId, batchCallback);
        case ITEM -> streamingClient.streamItems(okapiUrl, tenantId, token, familyId, batchCallback);
        default -> throw new IllegalStateException("Unsupported streaming resource: " + resourceType);
      }
    } catch (Exception e) {
      if (statusId != null) {
        updateResourceStatus(statusId, StreamingReindexStatus.FAILED, e.getMessage());
      }
      throw e;
    }

    if (statusId != null) {
      updateResourceStatus(statusId, StreamingReindexStatus.COMPLETED, null);
    }

    log.info("streamFlatResource:: completed [resource: {}, records: {}, failedBatches: {}, elapsed: {}ms]",
      resourceType.value, recordCounter[0], failedBatchCounter[0], ms(System.nanoTime() - resourceStart));

    if (resourceType == StreamingResource.INSTANCE) {
      indexingPipeline.logEnrichmentProfilingSummary();
    }
  }

  private java.util.function.Consumer<List<Map<String, Object>>> createFlatBatchCallback(
    StreamingResource resourceType, String tenantId, String targetIndex, UUID statusId,
    long[] recordCounter, long[] failedBatchCounter) {
    return page -> {
      try {
        var batchStart = System.nanoTime();
        indexingPipeline.indexBatchToFamily(resourceType.value, page, tenantId, targetIndex);
        var batchMs = ms(System.nanoTime() - batchStart);

        recordCounter[0] += page.size();
        log.info("streamFlatResource:: batch indexed [resource: {}, batchSize: {}, totalRecords: {}, elapsed: {}ms]",
          resourceType.value, page.size(), recordCounter[0], batchMs);
        if (statusId != null) {
          statusRepository.updateRecordsProcessed(statusId, recordCounter[0]);
        }
      } catch (Exception e) {
        failedBatchCounter[0]++;
        if (statusId != null) {
          statusRepository.updateFailedBatches(statusId, failedBatchCounter[0]);
        }
        log.error("streamFlatResource:: batch indexing failed [resource: {}, tenant: {}, batchSize: {}, "
            + "failedBatches: {}]",
          resourceType.value, tenantId, page.size(), failedBatchCounter[0], e);
        throw e instanceof RuntimeException
          ? (RuntimeException) e
          : new SearchServiceException("Failed to index streamed batch for " + resourceType.value, e);
      }
    };
  }

  private List<String> resolveReindexTenants(String tenantId) {
    try {
      return Stream.concat(Stream.of(tenantId), consortiumTenantService.getConsortiumTenants(tenantId).stream())
        .distinct()
        .toList();
    } catch (Exception e) {
      log.info("resolveReindexTenants:: consortium lookup failed, treating as single tenant [tenant: {}]", tenantId);
      return List.of(tenantId);
    }
  }

  private void createStatusRecords(UUID jobId, IndexFamilyEntity family, QueryVersion version) {
    for (var resourceType : trackedResources(version)) {
      var status = new StreamingReindexStatusEntity(
        UUID.randomUUID(), family.getTenantId(), family.getId(),
        resourceType.value, StreamingReindexStatus.PENDING.name(), 0, Timestamp.from(Instant.now()), null, null
      );
      status.setJobId(jobId);
      statusRepository.create(status);
    }
  }

  private void handleReindexFailure(UUID jobId, UUID familyId, Exception error) {
    updateJobStatus(jobId, StreamingReindexStatus.FAILED, error.getMessage());
    consumerManager.stopReindexConsumer(familyId);
    indexFamilyService.markFailed(familyId);
  }

  public StreamingReindexJob resumeStreamingReindex(UUID familyId) {
    var family = indexFamilyService.findById(familyId)
      .orElseThrow(() -> new RequestValidationException("Index family not found", "familyId", familyId.toString()));

    if (family.getStatus() != IndexFamilyStatus.BUILDING && family.getStatus() != IndexFamilyStatus.FAILED) {
      throw new RequestValidationException(
        "Only BUILDING or FAILED families can be resumed", "status", family.getStatus().getValue());
    }

    if (family.getStatus() == IndexFamilyStatus.FAILED) {
      indexFamilyService.updateStatus(family.getId(), IndexFamilyStatus.BUILDING);
    }

    var reindexTenants = resolveReindexTenants(family.getTenantId());
    var statuses = statusRepository.findByFamilyId(familyId);
    var committedOffsets = loadCommittedOffsetsIfEligible(family, statuses);

    if (committedOffsets.isPresent()) {
      return resumeKafkaConsumer(family, reindexTenants, statuses, committedOffsets.get());
    }

    return restartStreamingReindexFromScratch(family, reindexTenants, false);
  }

  private void launchReindex(UUID jobId, IndexFamilyEntity family, List<String> reindexTenants) {
    var okapiUrl = context.getOkapiUrl();
    if (family.getQueryVersion() == QueryVersion.V2) {
      consumerManager.startReindexConsumer(
        family.getId(), family.getIndexName(), reindexTenants, family.getGeneration());
      CompletableFuture.runAsync(
        () -> executeV2StreamingReindex(jobId, family, reindexTenants, okapiUrl),
        streamingReindexExecutor)
        .exceptionally(t -> {
          log.error("launchReindex:: unexpected error in V2 streaming reindex [jobId: {}, familyId: {}]",
            jobId, family.getId(), t);
          handleReindexFailure(jobId, family.getId(), t instanceof Exception ex ? ex : new RuntimeException(t));
          return null;
        });
      return;
    }

    CompletableFuture.runAsync(
      () -> executeV1StreamingReindex(jobId, family, reindexTenants, okapiUrl),
      streamingReindexExecutor)
      .exceptionally(t -> {
        log.error("launchReindex:: unexpected error in V1 streaming reindex [jobId: {}, familyId: {}]",
          jobId, family.getId(), t);
        handleReindexFailure(jobId, family.getId(), t instanceof Exception ex ? ex : new RuntimeException(t));
        return null;
      });
  }

  private List<StreamingResource> trackedResources(QueryVersion version) {
    return version == QueryVersion.V1 ? List.of(StreamingResource.INSTANCE) : flatStreamingResources();
  }

  private Optional<Map<TopicPartition, Long>> loadCommittedOffsetsIfEligible(
    IndexFamilyEntity family, List<StreamingReindexStatusEntity> statuses) {
    if (statuses.isEmpty()) {
      return Optional.empty();
    }

    var expectedResources = trackedResources(family.getQueryVersion()).stream()
      .map(resource -> resource.value)
      .collect(Collectors.toSet());
    var completedResources = statuses.stream()
      .filter(status -> StreamingReindexStatus.COMPLETED.name().equals(status.getStatus()))
      .map(StreamingReindexStatusEntity::getResourceType)
      .collect(Collectors.toSet());

    if (!completedResources.containsAll(expectedResources)) {
      return Optional.empty();
    }

    try {
      var offsets = consumerManager.getCommittedOffsets(family.getId(), family.getGeneration());
      return offsets.isEmpty() ? Optional.empty() : Optional.of(offsets);
    } catch (Exception e) {
      log.warn("resumeStreamingReindex:: failed to load committed offsets, falling back to restart "
          + "[familyId: {}, error: {}]", family.getId(), e.getMessage());
      return Optional.empty();
    }
  }

  private StreamingReindexJob resumeKafkaConsumer(IndexFamilyEntity family, List<String> reindexTenants,
                                                  List<StreamingReindexStatusEntity> statuses,
                                                  Map<TopicPartition, Long> committedOffsets) {
    var familyId = family.getId();
    var existingJobId = statuses.stream()
      .map(StreamingReindexStatusEntity::getJobId)
      .filter(Objects::nonNull)
      .findFirst()
      .orElseThrow(() -> new SearchServiceException("Streaming reindex status is missing job id"));

    consumerManager.resumeReindexConsumer(
      familyId, family.getIndexName(), reindexTenants, family.getGeneration(),
      family.getQueryVersion(), committedOffsets);

    log.info("resumeStreamingReindex:: resumed temporary Kafka consumer from committed offsets "
        + "[familyId: {}, version: {}, tenant: {}]",
      familyId, family.getQueryVersion(), family.getTenantId());

    return new StreamingReindexJob(existingJobId, familyId);
  }

  private StreamingReindexJob restartStreamingReindexFromScratch(IndexFamilyEntity family,
                                                                 List<String> reindexTenants,
                                                                 boolean clearStreamCursors) {
    var familyId = family.getId();
    var jobId = UUID.randomUUID();

    log.warn("resumeStreamingReindex:: reindex is not resumable from persisted state, restarting from scratch "
        + "[familyId: {}, version: {}, tenant: {}]",
      familyId, family.getQueryVersion(), family.getTenantId());

    statusRepository.deleteByFamilyId(familyId);
    createStatusRecords(jobId, family, family.getQueryVersion());
    if (clearStreamCursors) {
      streamingClient.clearCursors(familyId);
    }

    try {
      launchReindex(jobId, family, reindexTenants);
    } catch (Exception e) {
      handleReindexFailure(jobId, familyId, e);
      throw e;
    }

    return new StreamingReindexJob(jobId, familyId);
  }

  private List<StreamingResource> flatStreamingResources() {
    return List.of(StreamingResource.INSTANCE, StreamingResource.HOLDING, StreamingResource.ITEM);
  }

  private void updateJobStatus(UUID jobId, StreamingReindexStatus status, String errorMessage) {
    statusRepository.updateStatus(jobId, status.name(), errorMessage);
  }

  private void updateResourceStatus(UUID statusId, StreamingReindexStatus status, String errorMessage) {
    statusRepository.updateResourceStatus(statusId, status.name(), errorMessage);
  }

  private void disableRefreshInterval(String index) {
    log.info("disableRefreshInterval:: disabling refresh [index: {}]", index);
    indexRepository.updateIndexSettings(index, "{\"index.refresh_interval\": \"-1\"}");
  }

  private void enableRefreshInterval(String index) {
    log.info("enableRefreshInterval:: restoring refresh and forcing refresh [index: {}]", index);
    indexRepository.updateIndexSettings(index, "{\"index.refresh_interval\": \"1s\"}");
    indexRepository.refreshIndices(index);
  }

  private static long ms(long nanos) {
    return nanos / 1_000_000;
  }

  public record StreamingReindexJob(UUID jobId, UUID familyId) {
  }

  private enum StreamingResource {
    INSTANCE("instance"),
    HOLDING("holding"),
    ITEM("item");

    private final String value;

    StreamingResource(String value) {
      this.value = value;
    }
  }
}
