package org.folio.search.service;

import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
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
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.ResourceIndexingConfiguration;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.repository.ResourceRepository;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.reindex.InstanceFetchService;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ResourceService {

  private static final String PRIMARY_INDEXING_REPOSITORY_NAME = "primary";
  private static final String INSTANCE_ID_FIELD = "instanceId";

  private final InstanceFetchService resourceFetchService;
  private final PrimaryResourceRepository primaryResourceRepository;
  private final ResourceDescriptionService resourceDescriptionService;
  private final MultiTenantSearchDocumentConverter searchDocumentConverter;
  private final Map<String, ResourceRepository> resourceRepositoryBeans;
  private final ConsortiumTenantExecutor consortiumTenantExecutor;
  private final InstanceChildrenResourceService instanceChildrenResourceService;

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

    var elasticsearchDocuments = searchDocumentConverter.convert(resourceEvents);
    var bulkIndexResponse = indexSearchDocuments(elasticsearchDocuments);
    log.info("indexResources: indexed to elasticsearch [eventType: {}, indexRequests: {} {}]",
      resourceEvents.getFirst().getType(), getNumberOfRequests(elasticsearchDocuments),
      getErrorMessage(bulkIndexResponse));

    return bulkIndexResponse;
  }

  /**
   * Index list of resource id event to elasticsearch.
   *
   * @param resourceIdEvents list of {@link ResourceEvent} objects.
   * @return index operation response as {@link FolioIndexOperationResponse} object
   */
  public FolioIndexOperationResponse indexInstancesById(List<ResourceEvent> resourceIdEvents) {
    log.debug("indexInstancesById: by [resourceIdEvents.size: {}]", collectionToLogMsg(resourceIdEvents, true));

    if (CollectionUtils.isEmpty(resourceIdEvents)) {
      return getSuccessIndexOperationResponse();
    }

    var groupedByOperation = resourceIdEvents.stream().collect(groupingBy(ResourceService::getEventIndexType));
    var indexDocuments = processIndexInstanceEvents(groupedByOperation.get(INDEX));
    var removeDocuments = processDeleteInstanceEvents(groupedByOperation.get(DELETE));

    var bulkIndexResponse = indexSearchDocuments(mergeMaps(indexDocuments, removeDocuments));
    log.info("indexInstancesById: indexed to elasticsearch [indexRequests: {}, removeRequests: {}{}]",
      getNumberOfRequests(indexDocuments), getNumberOfRequests(removeDocuments), getErrorMessage(bulkIndexResponse));

    return bulkIndexResponse;
  }

  private Map<String, List<SearchDocumentBody>> processIndexInstanceEvents(List<ResourceEvent> resourceEvents) {
    var indexEvents = extractEventsForDataMove(resourceEvents);
    var fetchedInstances = Optional.ofNullable(consortiumTenantExecutor.execute(
        () -> resourceFetchService.fetchInstancesByIds(indexEvents)))
      .orElse(Collections.emptyList()).stream()
      .filter(Objects::nonNull)
      .toList();

    return searchDocumentConverter.convert(fetchedInstances);
  }

  private Map<String, List<SearchDocumentBody>> processDeleteInstanceEvents(List<ResourceEvent> deleteEvents) {
    return searchDocumentConverter.convert(deleteEvents);
  }

  private FolioIndexOperationResponse indexSearchDocuments(Map<String, List<SearchDocumentBody>> eventsByResource) {
    var eventsByRepository = eventsByResource.entrySet().stream().collect(groupingBy(
      entry -> getIndexingRepositoryName(ResourceType.byName(entry.getKey())),
      flatMapping(entry -> entry.getValue().stream(), toList())));

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

  private String getIndexingRepositoryName(ResourceType resourceName) {
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
