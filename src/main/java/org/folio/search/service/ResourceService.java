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
import org.apache.commons.collections4.ListUtils;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.integration.KafkaMessageProducer;
import org.folio.search.integration.ResourceFetchService;
import org.folio.search.model.event.ConsortiumInstanceEvent;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.ResourceIndexingConfiguration;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.repository.IndexNameProvider;
import org.folio.search.repository.IndexRepository;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.repository.ResourceRepository;
import org.folio.search.service.consortium.ConsortiumInstanceService;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.service.metadata.ResourceDescriptionService;
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
  private final ConsortiumTenantExecutor consortiumTenantExecutor;
  private final ConsortiumInstanceService consortiumInstanceService;
  private final IndexNameProvider indexNameProvider;

  /**
   * Saves list of resourceEvents to elasticsearch.
   *
   * @param resourceEvents {@link List} of resourceEvents as {@link ResourceEvent} objects.
   * @return index operation response as {@link FolioIndexOperationResponse} object
   */
  public FolioIndexOperationResponse indexResources(List<ResourceEvent> resourceEvents) {
    log.debug("indexResources: by [resourceEvent.size: {}]", collectionToLogMsg(resourceEvents, true));

    if (CollectionUtils.isEmpty(resourceEvents)) {
      return getSuccessIndexOperationResponse();
    }

    var eventsToIndex = getEventsToIndex(resourceEvents);
    var elasticsearchDocuments = multiTenantSearchDocumentConverter.convert(eventsToIndex);
    var bulkIndexResponse = indexSearchDocuments(elasticsearchDocuments);
    log.info("Records indexed to elasticsearch [indexRequests: {}. {}]",
      getNumberOfRequests(elasticsearchDocuments), getErrorMessage(bulkIndexResponse));

    return bulkIndexResponse;
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

    var groupedByOperation = eventsToIndex.stream().collect(groupingBy(ResourceService::getEventIndexType));
    var indexDocuments = processIndexInstanceEvents(groupedByOperation.get(INDEX));
    var removeDocuments = processDeleteInstanceEvents(groupedByOperation.get(DELETE));

    var bulkIndexResponse = indexSearchDocuments(mergeMaps(indexDocuments, removeDocuments));
    log.info("Records indexed to elasticsearch [indexRequests: {}, removeRequests: {}{}]",
      getNumberOfRequests(indexDocuments), getNumberOfRequests(removeDocuments), getErrorMessage(bulkIndexResponse));

    return bulkIndexResponse;
  }

  public FolioIndexOperationResponse indexConsortiumInstances(List<ConsortiumInstanceEvent> consortiumInstances) {
    if (CollectionUtils.isEmpty(consortiumInstances)) {
      return getSuccessIndexOperationResponse();
    }

    var validConsortiumInstances = consortiumInstances.stream()
      .filter(event -> consortiumTenantService.getCentralTenant(event.getTenant()).isPresent())
      .distinct()
      .toList();

    if (log.isDebugEnabled()) {
      var invalidInstances = ListUtils.subtract(consortiumInstances, validConsortiumInstances);
      log.debug("Skip indexing consortium instances [{}]", invalidInstances);
    }

    var centralTenant = consortiumTenantService.getCentralTenant(validConsortiumInstances.get(0).getTenant())
      .orElseThrow(() -> new IllegalStateException("Central tenant must exist"));

    var instanceIds = validConsortiumInstances.stream().map(ConsortiumInstanceEvent::getInstanceId).collect(toSet());

    return consortiumTenantExecutor.execute(centralTenant, () -> {
      var resourceEvents = consortiumInstanceService.fetchInstances(instanceIds);
      var indexDocuments = multiTenantSearchDocumentConverter.convert(resourceEvents);
      var bulkIndexResponse = indexSearchDocuments(indexDocuments);
      log.info("Records indexed to central index [requests: {}{}]",
        getNumberOfRequests(indexDocuments), getErrorMessage(bulkIndexResponse));
      return bulkIndexResponse;
    });
  }

  private List<ResourceEvent> getEventsToIndex(List<ResourceEvent> events) {
    return getEventsThatCanBeIndexed(events, indexNameProvider::getIndexName);
  }

  private Map<String, List<SearchDocumentBody>> processIndexInstanceEvents(List<ResourceEvent> resourceEvents) {
    var indexEvents = extractEventsForDataMove(resourceEvents);
    var fetchedInstances = resourceFetchService.fetchInstancesByIds(indexEvents);
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
