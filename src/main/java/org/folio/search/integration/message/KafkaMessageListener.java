package org.folio.search.integration.message;

import static org.apache.commons.lang3.RegExUtils.replaceAll;
import static org.folio.search.configuration.RetryTemplateConfiguration.KAFKA_RETRY_TEMPLATE_NAME;
import static org.folio.search.configuration.SearchCacheNames.REFERENCE_DATA_CACHE;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchConverterUtils.getResourceEventId;
import static org.folio.search.utils.SearchConverterUtils.getResourceSource;
import static org.folio.search.utils.SearchUtils.SOURCE_CONSORTIUM_PREFIX;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.reindex.IndexFamilyEntity;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.IndexFamilyService;
import org.folio.search.service.ResourceService;
import org.folio.search.service.browse.V2BrowseDirtyIdEnqueueHelper;
import org.folio.search.service.browse.V2BrowseProjectionService;
import org.folio.search.service.config.ConfigSynchronizationService;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.ingest.InstanceSearchIndexingPipeline;
import org.folio.search.service.reindex.ReindexKafkaConsumerManager;
import org.folio.search.utils.FlatResourceTypeResolver;
import org.folio.search.utils.InstanceIdResolver;
import org.folio.search.utils.KafkaConstants;
import org.folio.search.utils.V2BrowseIdExtractor;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class KafkaMessageListener {

  private final ResourceService resourceService;
  private final FolioMessageBatchProcessor folioMessageBatchProcessor;
  private final SystemUserScopedExecutionService executionService;
  private final ConfigSynchronizationService configSynchronizationService;
  private final InstanceSearchIndexingPipeline instanceSearchIndexingPipeline;
  private final IndexFamilyService indexFamilyService;
  private final ConsortiumTenantProvider consortiumTenantProvider;
  private final V2BrowseDirtyIdEnqueueHelper enqueueHelper;
  private final V2BrowseProjectionService browseProjectionService;
  private final ReindexKafkaConsumerManager reindexKafkaConsumerManager;

  @KafkaListener(
    id = KafkaConstants.EVENT_LISTENER_ID,
    containerFactory = "instanceResourceListenerContainerFactory",
    topicPattern = "#{folioKafkaProperties.listener['events'].topicPattern}",
    groupId = "#{folioKafkaProperties.listener['events'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['events'].concurrency}")
  public void handleInstanceEvents(List<ConsumerRecord<String, ResourceEvent>> consumerRecords) {
    log.info("Processing instance related events from kafka events [number of events: {}]", consumerRecords.size());

    writeToVersionedIndexes(consumerRecords);
  }

  @KafkaListener(
    id = KafkaConstants.AUTHORITY_LISTENER_ID,
    containerFactory = "resourceListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['authorities'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['authorities'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['authorities'].topicPattern}")
  public void handleAuthorityEvents(List<ConsumerRecord<String, ResourceEvent>> consumerRecords) {
    log.info("Processing authority events from Kafka [number of events: {}]", consumerRecords.size());
    var batch = consumerRecords.stream()
      .map(ConsumerRecord::value)
      .filter(authority -> !StringUtils.startsWith(getResourceSource(authority), SOURCE_CONSORTIUM_PREFIX))
      .map(authority -> authority.id(getResourceEventId(authority)))
      .toList();

    indexResources(batch, resourceService::indexResources);
  }

  @KafkaListener(
    id = KafkaConstants.BROWSE_CONFIG_DATA_LISTENER_ID,
    containerFactory = "resourceListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['browse-config-data'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['browse-config-data'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['browse-config-data'].topicPattern}")
  @CacheEvict(cacheNames = REFERENCE_DATA_CACHE, allEntries = true)
  public void handleBrowseConfigDataEvents(List<ConsumerRecord<String, ResourceEvent>> consumerRecords) {
    log.info("Processing browse config data events from Kafka [number of events: {}]", consumerRecords.size());
    var batch = consumerRecords.stream()
      .map(ConsumerRecord::value)
      .filter(resourceEvent -> resourceEvent.getType() == DELETE)
      .toList();

    var batchByTenant = batch.stream().collect(Collectors.groupingBy(ResourceEvent::getTenant));

    batchByTenant.forEach((tenant, resourceEvents) -> executionService.executeSystemUserScoped(tenant, () -> {
      folioMessageBatchProcessor.consumeBatchWithFallback(batch, KAFKA_RETRY_TEMPLATE_NAME,
        resourceEvent -> {
          var eventsByResource = resourceEvent.stream().collect(Collectors.groupingBy(ResourceEvent::getResourceName));
          eventsByResource.forEach((resourceName, events) ->
            configSynchronizationService.sync(resourceEvent, ResourceType.byName(resourceName)));
        },
        KafkaMessageListener::logFailedEvent);
      return null;
    }));
  }

  @KafkaListener(
    id = KafkaConstants.LOCATION_LISTENER_ID,
    containerFactory = "resourceListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['location'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['location'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['location'].topicPattern}")
  public void handleLocationEvents(List<ConsumerRecord<String, ResourceEvent>> consumerRecords) {
    log.info("Processing location events from Kafka [number of events: {}]", consumerRecords.size());
    var batch = consumerRecords.stream()
      .map(ConsumerRecord::value)
      .map(location -> location.id(getResourceEventId(location) + "|" + location.getTenant()))
      .toList();

    indexResources(batch, resourceService::indexResources);
  }

  @KafkaListener(
    id = KafkaConstants.LINKED_DATA_LISTENER_ID,
    containerFactory = "resourceListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['linked-data'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['linked-data'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['linked-data'].topicPattern}")
  public void handleLinkedDataEvents(List<ConsumerRecord<String, ResourceEvent>> consumerRecords) {
    log.info("Processing linked data events from Kafka [number of events: {}]", consumerRecords.size());
    var batch = consumerRecords.stream()
      .map(ConsumerRecord::value)
      .map(ld -> ld.id(getResourceEventId(ld)))
      .toList();

    indexResources(batch, resourceService::indexResources);
  }

  private void writeToVersionedIndexes(List<ConsumerRecord<String, ResourceEvent>> consumerRecords) {
    var recordsByTenant = consumerRecords.stream()
      .filter(r -> r.value() != null && r.value().getTenant() != null)
      .collect(Collectors.groupingBy(r -> consortiumTenantProvider.getTenant(r.value().getTenant())));

    recordsByTenant.forEach((targetTenant, tenantRecords) ->
      executionService.executeSystemUserScoped(targetTenant, () -> {
        // V1: write to legacy index unless V1 has been explicitly decommissioned
        // (no V1 family but a V2 family exists, meaning the tenant has moved to V2-only)
        var v1Family = indexFamilyService.findActiveFamily(targetTenant, QueryVersion.V1);
        var v2Family = indexFamilyService.findActiveFamily(targetTenant, QueryVersion.V2);
        var v1CuttingOverFamily = indexFamilyService.findCuttingOverFamily(targetTenant, QueryVersion.V1);
        var v2CuttingOverFamily = indexFamilyService.findCuttingOverFamily(targetTenant, QueryVersion.V2);
        if (v1Family.isPresent() || v2Family.isEmpty()) {
          writeLegacy(tenantRecords);
        }
        v1CuttingOverFamily.ifPresent(family -> writeLegacyToFamily(tenantRecords, family.getIndexName()));

        var needsFlatWrite = v2Family.isPresent() || v2CuttingOverFamily.isPresent();
        if (needsFlatWrite) {
          var flatEvents = enrichEventsForFlat(tenantRecords);
          var touchedBrowseIds = extractTouchedBrowseIds(flatEvents);

          // V2: only enrich and write if an active V2 family exists
          if (v2Family.isPresent()) {
            var alias = indexFamilyService.getAliasName(targetTenant, QueryVersion.V2);
            writeFlatBatch(flatEvents, targetTenant, alias);
            enqueueTouchedBrowseIds(targetTenant, touchedBrowseIds);
          }

          v2CuttingOverFamily.ifPresent(family -> {
            writeFlatBatch(flatEvents, targetTenant, family.getIndexName());
            rebuildCuttingOverBrowseFamily(targetTenant, family, touchedBrowseIds);
          });
        }
        return null;
      })
    );
  }

  private void writeLegacy(List<ConsumerRecord<String, ResourceEvent>> tenantRecords) {
    var batch = getInstanceResourceEvents(tenantRecords);
    if (batch.isEmpty()) {
      return;
    }
    folioMessageBatchProcessor.consumeBatchWithFallback(batch, KAFKA_RETRY_TEMPLATE_NAME,
      resourceService::indexInstancesById, KafkaMessageListener::logFailedEvent);
  }

  private void writeLegacyToFamily(List<ConsumerRecord<String, ResourceEvent>> tenantRecords, String targetIndexName) {
    reindexKafkaConsumerManager.applyV1EventsToFamily(tenantRecords, targetIndexName);
  }

  private void writeFlatBatch(List<FlatIndexEvent> events, String targetTenant, String aliasName) {
    if (events.isEmpty()) {
      return;
    }
    var resourceEvents = events.stream().map(FlatIndexEvent::resourceEvent).toList();
    folioMessageBatchProcessor.consumeBatchWithFallback(resourceEvents, KAFKA_RETRY_TEMPLATE_NAME,
      batch -> {
        for (var event : batch) {
          var eventPayload = getEventPayload(event);
          if (eventPayload != null) {
            instanceSearchIndexingPipeline.indexFromEvent(
              event.getResourceName(), eventPayload, event.getType(), event.getTenant(), aliasName);
          }
        }
      },
      KafkaMessageListener::logFailedEvent);
  }

  private List<FlatIndexEvent> enrichEventsForFlat(List<ConsumerRecord<String, ResourceEvent>> consumerRecords) {
    return consumerRecords.stream()
      .filter(r -> r.value() != null && r.value().getTenant() != null)
      .map(r -> {
        var event = r.value();
        var flatResourceType = FlatResourceTypeResolver.resolve(r.topic());
        return new FlatIndexEvent(
          consortiumTenantProvider.getTenant(event.getTenant()),
          new ResourceEvent()
            .id(event.getId())
            .type(event.getType() != null ? event.getType() : CREATE)
            .tenant(event.getTenant())
            .resourceName(flatResourceType)
            ._new(event.getNew())
            .old(event.getOld()));
      })
      .toList();
  }

  @SuppressWarnings("unchecked")
  private V2BrowseIdExtractor.TouchedBrowseIds extractTouchedBrowseIds(List<FlatIndexEvent> events) {
    var touched = V2BrowseIdExtractor.TouchedBrowseIds.empty();

    for (var flatEvent : events) {
      var event = flatEvent.resourceEvent();
      var resourceType = event.getResourceName();

      // Union browse IDs from old and new payloads so removed and added entries are both recomputed
      var newPayload = event.getNew() instanceof Map ? (Map<String, Object>) event.getNew() : null;
      var oldPayload = event.getOld() instanceof Map ? (Map<String, Object>) event.getOld() : null;

      if (newPayload != null) {
        touched.merge(V2BrowseIdExtractor.computeFromRawRecord(resourceType, newPayload));
      }
      if (oldPayload != null) {
        touched.merge(V2BrowseIdExtractor.computeFromRawRecord(resourceType, oldPayload));
      }
    }

    return touched;
  }

  private void enqueueTouchedBrowseIds(String ownerTenantId, V2BrowseIdExtractor.TouchedBrowseIds touched) {
    if (!touched.isEmpty()) {
      enqueueHelper.enqueueTouched(ownerTenantId, touched);
    }
  }

  private void rebuildCuttingOverBrowseFamily(String ownerTenantId, IndexFamilyEntity family,
                                              V2BrowseIdExtractor.TouchedBrowseIds touched) {
    if (touched.isEmpty()) {
      return;
    }

    browseProjectionService.rebuildAll(
      touched,
      family.getIndexName(),
      indexFamilyService.getV2BrowsePhysicalIndexMap(ownerTenantId, family.getGeneration()));
  }

  private void indexResources(List<ResourceEvent> batch, Consumer<List<ResourceEvent>> indexConsumer) {
    var batchByTenant = batch.stream().collect(Collectors.groupingBy(ResourceEvent::getTenant));

    batchByTenant.forEach((tenant, resourceEvents) -> executionService.executeSystemUserScoped(tenant, () -> {
      folioMessageBatchProcessor.consumeBatchWithFallback(resourceEvents, KAFKA_RETRY_TEMPLATE_NAME,
        indexConsumer, KafkaMessageListener::logFailedEvent);
      return null;
    }));
  }

  private static List<ResourceEvent> getInstanceResourceEvents(
    List<ConsumerRecord<String, ResourceEvent>> events) {
    return events.stream()
      .map(KafkaMessageListener::getInstanceResourceEvent)
      .filter(Objects::nonNull)
      .distinct()
      .toList();
  }

  private static ResourceEvent getInstanceResourceEvent(ConsumerRecord<String, ResourceEvent> consumerRecord) {
    var instanceId = InstanceIdResolver.resolve(consumerRecord);
    var value = consumerRecord.value();
    if (instanceId == null) {
      log.warn("Failed to find instance id in record [record: {}]", replaceAll(value.toString(), "\\s+", " "));
      return null;
    }
    var operation = InstanceIdResolver.isInstanceResource(consumerRecord) ? value.getType() : CREATE;
    return value.id(instanceId).type(operation);
  }

  private static void logFailedEvent(ResourceEvent event, Exception e) {
    if (event == null) {
      log.warn("Failed to index resource event [event: null]", e);
      return;
    }

    var eventType = event.getType() != null ? event.getType().getValue() : "unknown";
    var resourceName = event.getResourceName() != null ? event.getResourceName() : "unknown";
    log.warn(new FormattedMessage(
      "Failed to index resource event [resource: {}, eventType: {}, tenantId: {}, id: {}]",
      resourceName, eventType, event.getTenant(), event.getId()
    ), e);
  }

  private record FlatIndexEvent(String targetTenant, ResourceEvent resourceEvent) {
  }
}
