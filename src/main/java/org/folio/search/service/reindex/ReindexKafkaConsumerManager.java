package org.folio.search.service.reindex;

import static java.util.stream.Collectors.toMap;
import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.NestedInstanceResourceRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.service.ingest.InstanceSearchIndexingPipeline;
import org.folio.search.utils.FlatResourceTypeResolver;
import org.folio.search.utils.InstanceIdResolver;
import org.folio.search.utils.KafkaUtils;
import org.folio.search.utils.SearchConverterUtils;
import org.folio.spring.config.properties.FolioEnvironment;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.BatchAcknowledgingMessageListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Manages temporary Kafka consumer groups for keeping BUILDING families current during reindex.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class ReindexKafkaConsumerManager {

  private static final List<String> INVENTORY_TOPIC_SUFFIXES = List.of(
    "inventory.instance", "inventory.holdings-record", "inventory.item", "inventory.bound-with");
  private static final Duration ADMIN_TIMEOUT = Duration.ofSeconds(15);
  private static final long MAX_RETRY_ATTEMPTS = 10L;

  @Qualifier("instanceResourceListenerContainerFactory")
  private final ConcurrentKafkaListenerContainerFactory<String, ResourceEvent> kafkaListenerContainerFactory;
  private final InstanceSearchIndexingPipeline indexingPipeline;
  private final KafkaProperties kafkaProperties;
  private final NestedInstanceResourceRepository nestedInstanceRepository;
  private final MultiTenantSearchDocumentConverter searchDocumentConverter;
  private final InstanceFetchService instanceFetchService;
  private final SystemUserScopedExecutionService executionService;

  private final Map<UUID, ReindexConsumerState> activeConsumers = new ConcurrentHashMap<>();
  private final Map<UUID, Map<TopicPartition, Long>> v1TargetOffsets = new ConcurrentHashMap<>();
  private final Map<UUID, StagedCutoverSnapshot> stagedCutoverSnapshots = new ConcurrentHashMap<>();

  public void resumeReindexConsumer(UUID familyId, String targetIndexName, Collection<String> tenantIds,
                                    int generation, QueryVersion version,
                                    Map<TopicPartition, Long> committedOffsets) {
    if (committedOffsets.isEmpty()) {
      throw new SearchServiceException("No committed offsets found for temporary reindex consumer");
    }
    startReindexConsumer(familyId, targetIndexName, tenantIds, generation, version,
      committedOffsets, false);
  }

  public Map<TopicPartition, Long> getCommittedOffsets(UUID familyId, int generation) {
    return loadCommittedOffsets(buildGroupId(generation, familyId));
  }

  /**
   * Starts a V2 (flat) temporary reindex consumer that runs concurrently with streaming.
   * Uses batch listener with per-record indexing.
   */
  public void startReindexConsumer(UUID familyId, String targetIndexName, Collection<String> tenantIds,
                                   int generation) {
    startReindexConsumer(familyId, targetIndexName, tenantIds, generation, QueryVersion.V2, null, true);
  }

  /**
   * Starts a version-aware temporary reindex consumer.
   * V2: batch listener with per-record indexing (concurrent with streaming).
   * V1: batch listener with last-write-wins (sequential, after streaming).
   */
  public void startReindexConsumer(UUID familyId, String targetIndexName, Collection<String> tenantIds,
                                   int generation, QueryVersion version,
                                   Map<TopicPartition, Long> preCapturedStartOffsets) {
    startReindexConsumer(familyId, targetIndexName, tenantIds, generation, version,
      preCapturedStartOffsets, true);
  }

  private void startReindexConsumer(UUID familyId, String targetIndexName, Collection<String> tenantIds,
                                    int generation, QueryVersion version,
                                    Map<TopicPartition, Long> preCapturedStartOffsets,
                                    boolean seekToStartOffsets) {
    var created = new boolean[]{false};
    activeConsumers.computeIfAbsent(familyId, id -> {
      var groupId = buildGroupId(generation, id);
      var scopedTenantIds = Set.copyOf(tenantIds);
      var resolvedTopics = resolveTopicNames(scopedTenantIds);
      var startOffsets = preCapturedStartOffsets != null
        ? filterToTopics(preCapturedStartOffsets, resolvedTopics) : captureStartOffsets(resolvedTopics);

      log.info("startReindexConsumer:: starting temporary consumer [familyId: {}, groupId: {}, target: {},"
          + " version: {}, partitions: {}, tenants: {}, topics: {}]",
        id, groupId, targetIndexName, version, startOffsets.size(), scopedTenantIds.size(),
        resolvedTopics.size());

      var container = kafkaListenerContainerFactory.createContainer(
        resolvedTopics.toArray(String[]::new));
      container.getContainerProperties().setGroupId(groupId);
      container.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
      if (seekToStartOffsets) {
        container.getContainerProperties().setConsumerRebalanceListener(new StartOffsetRebalanceListener(startOffsets));
      }
      container.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, MAX_RETRY_ATTEMPTS)));

      if (version == QueryVersion.V1) {
        configureV1BatchListener(container, targetIndexName, scopedTenantIds);
      } else {
        configureV2BatchListener(container, targetIndexName, scopedTenantIds);
      }

      container.start();
      created[0] = true;
      return new ReindexConsumerState(container, groupId, startOffsets, resolvedTopics);
    });

    if (created[0]) {
      log.info("startReindexConsumer:: temporary consumer started [familyId: {}, version: {}]", familyId, version);
    } else {
      log.warn("startReindexConsumer:: consumer already running [familyId: {}]", familyId);
    }
  }

  /**
   * Captures current end offsets without starting a consumer.
   * Used by V1 to capture offsets BEFORE streaming begins.
   */
  public Map<TopicPartition, Long> captureCurrentOffsets(Collection<String> tenantIds) {
    return captureStartOffsets(resolveTopicNames(tenantIds));
  }

  public void stopReindexConsumer(UUID familyId) {
    var state = activeConsumers.remove(familyId);
    v1TargetOffsets.remove(familyId);
    stagedCutoverSnapshots.remove(familyId);
    if (state != null) {
      state.container().stop();
      log.info("stopReindexConsumer:: stopped temporary consumer [familyId: {}]", familyId);
    }
  }

  public boolean isConsumerRunning(UUID familyId) {
    var state = activeConsumers.get(familyId);
    return state != null && state.container().isRunning();
  }

  public long getConsumerLag(UUID familyId) {
    var state = activeConsumers.get(familyId);
    if (state == null || state.startOffsets().isEmpty()) {
      return 0L;
    }

    try (var adminClient = AdminClient.create(kafkaProperties.buildAdminProperties(null))) {
      var latestOffsets = adminClient.listOffsets(state.startOffsets().keySet().stream()
          .collect(toMap(tp -> tp, tp -> OffsetSpec.latest())))
        .all()
        .get(ADMIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      var committedOffsets = adminClient.listConsumerGroupOffsets(state.groupId())
        .partitionsToOffsetAndMetadata()
        .get(ADMIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

      long lag = 0L;
      for (var entry : state.startOffsets().entrySet()) {
        var topicPartition = entry.getKey();
        var latestOffset = latestOffsets.get(topicPartition).offset();
        var committedOffset = committedOffsets.getOrDefault(topicPartition, new OffsetAndMetadata(entry.getValue()))
          .offset();
        lag += Math.max(latestOffset - committedOffset, 0L);
      }
      return lag;
    } catch (Exception e) {
      throw new SearchServiceException("Failed to calculate temporary reindex consumer lag", e);
    }
  }

  public void captureTargetOffsets(UUID familyId) {
    var state = activeConsumers.get(familyId);
    if (state == null) {
      throw new SearchServiceException("No active consumer found for family " + familyId);
    }
    captureTargetOffsetsForTopics(familyId, state.resolvedTopics());
  }

  public void captureTargetOffsets(UUID familyId, Collection<String> tenantIds) {
    captureTargetOffsetsForTopics(familyId, resolveTopicNames(tenantIds));
  }

  private void captureTargetOffsetsForTopics(UUID familyId, Set<String> resolvedTopics) {
    var captured = captureStartOffsets(resolvedTopics);
    v1TargetOffsets.put(familyId, captured);
    log.info("captureTargetOffsets:: captured target offsets [familyId: {}, partitions: {}]",
      familyId, captured.size());
  }

  /**
   * Captures a fixed V2 staged cutover snapshot that remains sticky for the lifetime of the
   * temporary consumer on this JVM. This is intentionally separate from the V1 target-offset
   * capture semantics used after nested backfill completes.
   */
  public void captureStagedCutoverSnapshot(UUID familyId) {
    if (stagedCutoverSnapshots.containsKey(familyId)) {
      log.info("captureStagedCutoverSnapshot:: reusing existing staged cutover snapshot [familyId: {}]", familyId);
      return;
    }
    refreshStagedCutoverSnapshot(familyId);
  }

  public void refreshStagedCutoverSnapshot(UUID familyId) {
    var state = activeConsumers.get(familyId);
    if (state == null) {
      throw new SearchServiceException("No active consumer found for family " + familyId);
    }

    var capturedAt = Instant.now();
    var captured = captureStartOffsets(state.resolvedTopics());
    stagedCutoverSnapshots.put(familyId, new StagedCutoverSnapshot(capturedAt, captured));
    log.info("refreshStagedCutoverSnapshot:: captured staged cutover snapshot [familyId: {}, capturedAt: {},"
        + " partitions: {}]",
      familyId, capturedAt, captured.size());
  }

  public boolean hasStagedCutoverSnapshot(UUID familyId) {
    return stagedCutoverSnapshots.containsKey(familyId);
  }

  public Optional<Instant> getStagedCutoverSnapshotCapturedAt(UUID familyId) {
    return Optional.ofNullable(stagedCutoverSnapshots.get(familyId))
      .map(StagedCutoverSnapshot::capturedAt);
  }

  public int getStagedCutoverSnapshotPartitionCount(UUID familyId) {
    return Optional.ofNullable(stagedCutoverSnapshots.get(familyId))
      .map(StagedCutoverSnapshot::partitionOffsets)
      .map(Map::size)
      .orElse(0);
  }

  public long getConsumerLagToTarget(UUID familyId) {
    var state = activeConsumers.get(familyId);
    var captured = v1TargetOffsets.get(familyId);
    if (state == null || state.startOffsets().isEmpty()) {
      return 0L;
    }
    if (captured == null || captured.isEmpty()) {
      return getConsumerLag(familyId);
    }

    return getConsumerLagToOffsets(familyId, captured, "Failed to calculate consumer lag to target offsets");
  }

  public long getConsumerLagToStagedCutoverSnapshot(UUID familyId) {
    var state = activeConsumers.get(familyId);
    if (state == null || state.startOffsets().isEmpty()) {
      throw new SearchServiceException("No active consumer found for family " + familyId);
    }

    var snapshot = stagedCutoverSnapshots.get(familyId);
    if (snapshot == null) {
      throw new SearchServiceException("No staged cutover snapshot found for family " + familyId);
    }

    return getConsumerLagToOffsets(state, snapshot.partitionOffsets(),
      "Failed to calculate consumer lag to staged cutover snapshot");
  }

  private long getConsumerLagToOffsets(UUID familyId, Map<TopicPartition, Long> targetOffsets, String errorMessage) {
    var state = activeConsumers.get(familyId);
    if (state == null || state.startOffsets().isEmpty()) {
      return 0L;
    }

    return getConsumerLagToOffsets(state, targetOffsets, errorMessage);
  }

  private long getConsumerLagToOffsets(ReindexConsumerState state, Map<TopicPartition, Long> targetOffsets,
                                       String errorMessage) {
    try (var adminClient = AdminClient.create(kafkaProperties.buildAdminProperties(null))) {
      var committedOffsets = adminClient.listConsumerGroupOffsets(state.groupId())
        .partitionsToOffsetAndMetadata()
        .get(ADMIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

      long lag = 0L;
      for (var entry : targetOffsets.entrySet()) {
        var topicPartition = entry.getKey();
        var targetOffset = entry.getValue();
        var committedOffset = committedOffsets.getOrDefault(topicPartition,
          new OffsetAndMetadata(state.startOffsets().getOrDefault(topicPartition, 0L))).offset();
        lag += Math.max(targetOffset - committedOffset, 0L);
      }
      return lag;
    } catch (Exception e) {
      throw new SearchServiceException(errorMessage, e);
    }
  }

  public int getTrackedPartitionCount(UUID familyId) {
    var captured = v1TargetOffsets.get(familyId);
    if (captured != null && !captured.isEmpty()) {
      return captured.size();
    }
    var state = activeConsumers.get(familyId);
    return state != null ? state.startOffsets().size() : 0;
  }

  public void applyV1EventsToFamily(List<ConsumerRecord<String, ResourceEvent>> records, String targetIndexName) {
    processCollapsedV1EventsByTenant(collapseV1Events(records), targetIndexName);
  }

  private void configureV2BatchListener(ConcurrentMessageListenerContainer<String, ResourceEvent> container,
                                         String targetIndexName, Set<String> scopedTenantIds) {
    container.getContainerProperties().setMessageListener(
      (BatchAcknowledgingMessageListener<String, ResourceEvent>) (records, ack) -> {
        for (var record : records) {
          var event = record.value();
          var resourceType = FlatResourceTypeResolver.resolve(record.topic());
          var eventPayload = SearchConverterUtils.getEventPayload(event);
          var eventType = event.getType() != null ? event.getType() : ResourceEventType.CREATE;
          if (eventPayload != null && event.getTenant() != null && scopedTenantIds.contains(event.getTenant())) {
            indexingPipeline.indexToFamily(resourceType, eventPayload, eventType, event.getTenant(), targetIndexName);
          }
        }

        if (ack != null) {
          ack.acknowledge();
        }
      });
  }

  @SuppressWarnings("unchecked")
  private void configureV1BatchListener(ConcurrentMessageListenerContainer<String, ResourceEvent> container,
                                         String targetIndexName, Set<String> scopedTenantIds) {
    container.getContainerProperties().setMessageListener(
      (BatchAcknowledgingMessageListener<String, ResourceEvent>) (records, ack) -> {
        var collapsed = collapseV1Events(records, scopedTenantIds);
        processCollapsedV1EventsByTenant(collapsed, targetIndexName);

        if (ack != null) {
          ack.acknowledge();
        }
      });
  }

  private void processCollapsedV1EventsByTenant(Map<String, CollapsedEvent> collapsed, String targetIndexName) {
    var eventsByTenant = collapsed.values().stream()
      .collect(Collectors.groupingBy(CollapsedEvent::tenantId, LinkedHashMap::new, Collectors.toList()));

    for (var tenantEntry : eventsByTenant.entrySet()) {
      executionService.executeSystemUserScoped(tenantEntry.getKey(), () -> {
        processV1CollapsedEvents(tenantEntry.getValue(), targetIndexName);
        return null;
      });
    }
  }

  private Map<String, CollapsedEvent> collapseV1Events(
    List<ConsumerRecord<String, ResourceEvent>> records) {
    return collapseV1Events(records, null);
  }

  private Map<String, CollapsedEvent> collapseV1Events(
    List<ConsumerRecord<String, ResourceEvent>> records, Set<String> scopedTenantIds) {
    var collapsed = new LinkedHashMap<String, CollapsedEvent>();

    for (var record : records) {
      var event = record.value();
      if (event == null || event.getTenant() == null) {
        continue;
      }
      if (scopedTenantIds != null && !scopedTenantIds.contains(event.getTenant())) {
        continue;
      }

      var instanceId = InstanceIdResolver.resolve(record);
      if (instanceId == null) {
        continue;
      }

      var isInstanceTopic = InstanceIdResolver.isInstanceResource(record);
      var eventType = event.getType();

      if (isInstanceTopic && eventType == ResourceEventType.DELETE) {
        collapsed.put(instanceId, new CollapsedEvent(instanceId, event.getTenant(), CollapsedAction.DELETE));
      } else {
        collapsed.putIfAbsent(instanceId, new CollapsedEvent(instanceId, event.getTenant(), CollapsedAction.REINDEX));
      }
    }

    return collapsed;
  }

  private void processV1CollapsedEvents(List<CollapsedEvent> events, String targetIndexName) {
    // Process deletes individually
    events.stream()
      .filter(e -> e.action() == CollapsedAction.DELETE)
      .forEach(e -> nestedInstanceRepository.deleteById(e.instanceId(), targetIndexName));

    // Batch-fetch all reindex events together to avoid N+1
    var reindexEvents = events.stream()
      .filter(e -> e.action() == CollapsedAction.REINDEX)
      .map(e -> new ResourceEvent()
        .id(e.instanceId())
        .type(ResourceEventType.CREATE)
        .resourceName(ResourceType.INSTANCE.getName())
        .tenant(e.tenantId()))
      .toList();

    if (reindexEvents.isEmpty()) {
      return;
    }

    var fetchedInstances = instanceFetchService.fetchInstancesByIds(reindexEvents);
    if (fetchedInstances.isEmpty()) {
      log.warn("processV1CollapsedEvents:: no instances found [count: {}]", reindexEvents.size());
      return;
    }

    var documents = searchDocumentConverter.convert(fetchedInstances);
    for (var docList : documents.values()) {
      nestedInstanceRepository.indexResources(docList, targetIndexName);
    }
  }

  private static Map<TopicPartition, Long> filterToTopics(Map<TopicPartition, Long> offsets,
                                                           Set<String> resolvedTopics) {
    return offsets.entrySet().stream()
      .filter(e -> resolvedTopics.contains(e.getKey().topic()))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static Set<String> resolveTopicNames(Collection<String> tenantIds) {
    return tenantIds.stream()
      .flatMap(tenantId -> INVENTORY_TOPIC_SUFFIXES.stream()
        .map(suffix -> KafkaUtils.getTenantTopicName(suffix, tenantId)))
      .collect(Collectors.toSet());
  }

  private Map<TopicPartition, Long> captureStartOffsets(Set<String> topicNames) {
    try (var consumer = createOffsetProbeConsumer()) {
      var existingTopics = consumer.listTopics(ADMIN_TIMEOUT).keySet();
      var matchingTopics = topicNames.stream()
        .filter(existingTopics::contains)
        .toList();
      var partitions = matchingTopics.stream()
        .flatMap(topic -> consumer.partitionsFor(topic, ADMIN_TIMEOUT).stream()
          .map(info -> new TopicPartition(info.topic(), info.partition())))
        .toList();
      if (partitions.isEmpty()) {
        return Map.of();
      }
      return consumer.endOffsets(partitions, ADMIN_TIMEOUT);
    } catch (Exception e) {
      throw new SearchServiceException("Failed to capture starting offsets for temporary reindex consumer", e);
    }
  }

  private Consumer<String, ResourceEvent> createOffsetProbeConsumer() {
    var config = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
    config.put(GROUP_ID_CONFIG, FolioEnvironment.getFolioEnvName() + "-mod-search-reindex-offset-probe");
    config.put(AUTO_OFFSET_RESET_CONFIG, "latest");
    config.put(ConsumerConfig.CLIENT_ID_CONFIG,
      FolioEnvironment.getFolioEnvName() + "-mod-search-reindex-offset-probe-" + UUID.randomUUID());
    var valueDeserializer = new JsonDeserializer<>(ResourceEvent.class, false);
    return new KafkaConsumer<>(config, new StringDeserializer(), valueDeserializer);
  }

  private Map<TopicPartition, Long> loadCommittedOffsets(String groupId) {
    try (var adminClient = AdminClient.create(kafkaProperties.buildAdminProperties(null))) {
      var committedOffsets = adminClient.listConsumerGroupOffsets(groupId)
        .partitionsToOffsetAndMetadata()
        .get(ADMIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

      return committedOffsets.entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .collect(toMap(Map.Entry::getKey, entry -> entry.getValue().offset()));
    } catch (Exception e) {
      throw new SearchServiceException("Failed to load committed offsets for temporary reindex consumer", e);
    }
  }

  private static String buildGroupId(int generation, UUID familyId) {
    return FolioEnvironment.getFolioEnvName() + "-mod-search-reindex-" + generation + "-" + familyId;
  }

  private record ReindexConsumerState(ConcurrentMessageListenerContainer<String, ResourceEvent> container,
                                      String groupId,
                                      Map<TopicPartition, Long> startOffsets,
                                      Set<String> resolvedTopics) {
  }

  private record StagedCutoverSnapshot(Instant capturedAt, Map<TopicPartition, Long> partitionOffsets) {
  }

  private record CollapsedEvent(String instanceId, String tenantId, CollapsedAction action) {
  }

  private enum CollapsedAction {
    REINDEX, DELETE
  }

  @RequiredArgsConstructor
  private static final class StartOffsetRebalanceListener implements ConsumerAwareRebalanceListener {

    private final Map<TopicPartition, Long> startOffsets;

    @Override
    public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
      for (var partition : partitions) {
        var offset = startOffsets.get(partition);
        if (offset != null) {
          consumer.seek(partition, offset);
        }
      }
    }
  }
}
