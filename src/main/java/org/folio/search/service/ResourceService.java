package org.folio.search.service;

import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.getNumberOfRequests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.integration.KafkaMessageProducer;
import org.folio.search.integration.ResourceFetchService;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.ResourceIndexingConfiguration;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.repository.IndexRepository;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.repository.ResourceRepository;
import org.folio.search.service.consortium.ConsortiumInstanceService;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.utils.SearchUtils;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ResourceService {

  private static final String PRIMARY_INDEXING_REPOSITORY_NAME = "primary";
  private static final String INSTANCE_ID_FIELD = "instanceId";

  private final KafkaMessageProducer messageProducer;
  private final IndexRepository indexRepository;
  private final ResourceFetchService resourceFetchService;
  private final PrimaryResourceRepository primaryResourceRepository;
  private final ResourceDescriptionService resourceDescriptionService;
  private final MultiTenantSearchDocumentConverter multiTenantSearchDocumentConverter;
  private final Map<String, ResourceRepository> resourceRepositoryBeans;
  private final ConsortiumTenantService consortiumTenantService;
  private final TenantScopedExecutionService tenantScopedExecutionService;
  private final ConsortiumInstanceService consortiumInstanceService;

  /**
   * Saves list of resources to elasticsearch.
   *
   * @param resources {@link List} of resources as {@link ResourceEvent} objects.
   * @return index operation response as {@link FolioIndexOperationResponse} object
   */
  public FolioIndexOperationResponse indexResources(List<ResourceEvent> resources) {
    log.debug("indexResources: by [resourceEvent.size: {}]", collectionToLogMsg(resources, true));

    if (CollectionUtils.isEmpty(resources)) {
      return getSuccessIndexOperationResponse();
    }

    var eventsToIndex = getEventsToIndex(resources);
    var elasticsearchDocuments = multiTenantSearchDocumentConverter.convert(eventsToIndex);
    return indexSearchDocuments(elasticsearchDocuments);
  }

  /**
   * Index list of resource id event to elasticsearch.
   *
   * @param resourceIdEvents list of {@link ResourceEvent} objects.
   * @return index operation response as {@link FolioIndexOperationResponse} object
   */
  public FolioIndexOperationResponse indexInstancesById(List<ResourceEvent> resourceIdEvents) {
    log.debug("indexResourcesById: by [resourceEvent.size: {}]", collectionToLogMsg(resourceIdEvents, true));

    if (CollectionUtils.isEmpty(resourceIdEvents)) {
      return getSuccessIndexOperationResponse();
    }

    var eventsToIndex = getEventsToIndex(resourceIdEvents);

    var eventsByTenant = eventsToIndex.stream()
      .filter(event -> INSTANCE_RESOURCE.equals(event.getResourceName()))
      .collect(groupingBy(ResourceEvent::getTenant));

    Map<String, List<SearchDocumentBody>> indexDocuments = new HashMap<>();
    Map<String, List<SearchDocumentBody>> removeDocuments = new HashMap<>();
    for (Map.Entry<String, List<ResourceEvent>> entry : eventsByTenant.entrySet()) {
      var tenant = entry.getKey();
      var events = entry.getValue();

      tenantScopedExecutionService.executeTenantScoped(tenant, () -> {
        var groupedByOperation = events.stream().collect(groupingBy(ResourceService::getEventIndexType));
        indexDocuments.putAll(processIndexInstanceEvents(tenant, groupedByOperation.get(INDEX)));
        removeDocuments.putAll(processDeleteInstanceEvents(groupedByOperation.get(DELETE)));
        return null;
      });
    }

    var bulkIndexResponse = indexSearchDocuments(mergeMaps(indexDocuments, removeDocuments));
    log.info("Records indexed to elasticsearch [indexRequests: {}, removeRequests: {}{}]",
      getNumberOfRequests(indexDocuments), getNumberOfRequests(removeDocuments), getErrorMessage(bulkIndexResponse));

    return bulkIndexResponse;
  }

  private List<ResourceEvent> getEventsToIndex(List<ResourceEvent> events) {
    var inConsortium = events.stream()
      .map(ResourceEvent::getTenant)
      .distinct()
      .anyMatch(tenantId -> consortiumTenantService.getCentralTenant(tenantId).isPresent());

    return inConsortium ? events : getEventsThatCanBeIndexed(events, SearchUtils::getIndexName);
  }

  private Map<String, List<SearchDocumentBody>> processIndexInstanceEvents(String tenant,
                                                                           List<ResourceEvent> resourceEvents) {
    var indexEvents = extractEventsForDataMove(resourceEvents);
    var fetchedInstances = resourceFetchService.fetchInstancesByIds(indexEvents, tenant);
    messageProducer.prepareAndSendContributorEvents(fetchedInstances);
    messageProducer.prepareAndSendSubjectEvents(fetchedInstances);
    return multiTenantSearchDocumentConverter.convert(consortiumInstanceService.saveInstances(fetchedInstances));
  }

  private Map<String, List<SearchDocumentBody>> processDeleteInstanceEvents(List<ResourceEvent> deleteEvents) {
    messageProducer.prepareAndSendContributorEvents(deleteEvents);
    messageProducer.prepareAndSendSubjectEvents(deleteEvents);
    return multiTenantSearchDocumentConverter.convert(consortiumInstanceService.deleteInstances(deleteEvents));
  }

  private FolioIndexOperationResponse indexSearchDocuments(Map<String, List<SearchDocumentBody>> eventsByResource) {
    var eventsByRepository = eventsByResource.entrySet().stream().collect(groupingBy(
      entry -> getIndexingRepositoryName(entry.getKey()), flatMapping(entry -> entry.getValue().stream(), toList())));

    var responses = new ArrayList<FolioIndexOperationResponse>();
    var primaryResources = eventsByRepository.get(PRIMARY_INDEXING_REPOSITORY_NAME);
    responses.add(primaryResourceRepository.indexResources(primaryResources));
    eventsByRepository.remove(PRIMARY_INDEXING_REPOSITORY_NAME);

    eventsByRepository.forEach((repository, events) ->
      responses.add(resourceRepositoryBeans.get(repository).indexResources(events)));

    var errorMessage = responses.stream()
      .map(FolioIndexOperationResponse::getErrorMessage)
      .filter(Objects::nonNull)
      .collect(joining(", "));

    return errorMessage.isEmpty() ? getSuccessIndexOperationResponse() : getErrorIndexOperationResponse(errorMessage);
  }

  private <T> List<T> getEventsThatCanBeIndexed(List<T> events, Function<T, String> eventToIndexNameFunc) {
    var esIndices = events.stream().map(eventToIndexNameFunc).collect(toSet());
    var existingIndices = esIndices.stream().filter(indexRepository::indexExists).collect(toSet());
    var eventsToIndex = new ArrayList<T>();
    var unknownEvents = new ArrayList<T>();

    for (var event : events) {
      if (existingIndices.contains(eventToIndexNameFunc.apply(event))) {
        eventsToIndex.add(event);
      } else {
        unknownEvents.add(event);
      }
    }

    if (!unknownEvents.isEmpty()) {
      var absentIndexNames = unknownEvents.stream().map(eventToIndexNameFunc).collect(toSet());
      log.warn("Ignoring incoming events [cause: Tenant(s) not initialized, indices {} are not exist, events: {}]",
        absentIndexNames, unknownEvents.toString().replaceAll("\\s+", " "));
    }

    return eventsToIndex;
  }

  /**
   * There may be a case when some data is moved between instances.
   * In such case old and new fields of the event will have different instanceId.
   * This method will create 2 events out of 1 and erase 'old' field in an original event.
   */
  private List<ResourceEvent> extractEventsForDataMove(List<ResourceEvent> resourceEvents) {
    if (resourceEvents == null) {
      return Collections.emptyList();
    }

    return resourceEvents.stream()
      .flatMap(resourceEvent -> {
        var oldMap = getOldAsMap(resourceEvent);
        var newMap = getNewAsMap(resourceEvent);
        var oldInstanceId = oldMap.get(INSTANCE_ID_FIELD);

        if (oldInstanceId != null && !oldInstanceId.equals(newMap.get(INSTANCE_ID_FIELD))) {
          var oldEvent = new ResourceEvent().id(String.valueOf(oldInstanceId))
            .resourceName(resourceEvent.getResourceName())
            .type(resourceEvent.getType())
            .tenant(resourceEvent.getTenant())
            ._new(resourceEvent.getOld());
          var newEvent = resourceEvent.old(null);
          return Stream.of(oldEvent, newEvent);
        }

        return Stream.of(resourceEvent);
      })
      .toList();
  }

  private static <K, V> Map<K, List<V>> mergeMaps(Map<K, List<V>> map1, Map<K, List<V>> map2) {
    var resultMap = new HashMap<K, List<V>>();
    map1.forEach((key, value) -> resultMap.computeIfAbsent(key, v -> new ArrayList<>()).addAll(value));
    map2.forEach((key, value) -> resultMap.computeIfAbsent(key, v -> new ArrayList<>()).addAll(value));
    return resultMap;
  }

  private String getIndexingRepositoryName(String resourceName) {
    return resourceDescriptionService.find(resourceName)
      .map(ResourceDescription::getIndexingConfiguration)
      .map(ResourceIndexingConfiguration::getResourceRepository)
      .filter(resourceRepositoryBeans::containsKey)
      .orElse(PRIMARY_INDEXING_REPOSITORY_NAME);
  }

  private static IndexActionType getEventIndexType(ResourceEvent event) {
    return event.getType() == ResourceEventType.DELETE ? DELETE : INDEX;
  }

  private static String getErrorMessage(FolioIndexOperationResponse bulkIndexResponse) {
    return bulkIndexResponse.getErrorMessage() != null ? ", errors: [" + bulkIndexResponse.getErrorMessage() + "]" : "";
  }
}
