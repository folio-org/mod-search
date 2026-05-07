package org.folio.search.service;

import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_ID_FIELD;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.event.IndexInstanceEvent;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.ResourceIndexingConfiguration;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.repository.ResourceRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.reindex.InstanceFetchService;
import org.folio.search.utils.SearchUtils;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ResourceService {

  private static final String PRIMARY_INDEXING_REPOSITORY_NAME = "primary";

  private final InstanceFetchService resourceFetchService;
  private final PrimaryResourceRepository primaryResourceRepository;
  private final ResourceDescriptionService resourceDescriptionService;
  private final MultiTenantSearchDocumentConverter searchDocumentConverter;
  private final Map<String, ResourceRepository> resourceRepositoryBeans;
  private final InventoryEntityPersistenceService inventoryEntityPersistenceService;

  /**
   * Saves list of resourceEvents to elasticsearch. For inventory resource types (instance, holdings, item),
   * also persists the entities to the merge range DB tables so that sub-resource processing can join against them.
   * Holdings and item events do not have their own index documents; instead they trigger a full re-index of their
   * parent instance (fetched from the merge-range DB with all holdings and items joined).
   *
   * @param resourceEvents {@link List} of resourceEvents as {@link ResourceEvent} objects.
   * @return index operation response as {@link FolioIndexOperationResponse} object
   */
  public FolioIndexOperationResponse indexResources(List<ResourceEvent> resourceEvents) {
    log.debug("indexResources: by [resourceEvent.size: {}]", collectionToLogMsg(resourceEvents, true));

    if (CollectionUtils.isEmpty(resourceEvents)) {
      return getSuccessIndexOperationResponse();
    }

    persistInventoryEntities(resourceEvents);
    reindexParentInstancesForSubResources(resourceEvents);

    var directIndexEvents = resourceEvents.stream()
      .filter(e -> !isInventoryEvent(e))
      .toList();
    if (CollectionUtils.isEmpty(directIndexEvents)) {
      return getSuccessIndexOperationResponse();
    }

    var elasticsearchDocuments = searchDocumentConverter.convert(directIndexEvents);
    var bulkIndexResponse = indexSearchDocuments(elasticsearchDocuments);
    log.info("indexResources: indexed to elasticsearch [eventType: {}, indexRequests: {} {}]",
      directIndexEvents.getFirst().getType(), SearchUtils.getNumberOfRequests(elasticsearchDocuments),
      getErrorMessage(bulkIndexResponse));

    return bulkIndexResponse;
  }

  /**
   * Index list of instance events to elasticsearch.
   *
   * @param instanceEvents list of {@link IndexInstanceEvent} objects.
   * @return index operation response as {@link FolioIndexOperationResponse} object
   */
  public FolioIndexOperationResponse indexInstanceEvents(List<IndexInstanceEvent> instanceEvents) {
    log.debug("indexInstanceEvents: by [instanceEvents.size: {}]", collectionToLogMsg(instanceEvents, true));

    if (CollectionUtils.isEmpty(instanceEvents)) {
      return getSuccessIndexOperationResponse();
    }

    var fetchedEvents = resourceFetchService.fetchInstancesByIds(instanceEvents);
    return indexFetchedInstances(fetchedEvents);
  }

  private void persistInventoryEntities(List<ResourceEvent> resourceEvents) {
    resourceEvents.stream()
      .filter(ResourceService::isInventoryEvent)
      .collect(Collectors.groupingBy(ResourceEvent::getTenant))
      .forEach(inventoryEntityPersistenceService::persistInventoryEntities);
  }

  private void reindexParentInstancesForSubResources(List<ResourceEvent> resourceEvents) {
    var instanceEvents = resourceEvents.stream()
      .filter(ResourceService::isInventoryEvent)
      .filter(e -> e.getNew() != null)
      .map(e -> new IndexInstanceEvent(e.getTenant(), getId(e)))
      .filter(e -> e.instanceId() != null)
      .distinct()
      .toList();
    if (!instanceEvents.isEmpty()) {
      indexInstanceEvents(instanceEvents);
    }
  }

  private String getId(ResourceEvent e) {
    var newAsMap = getNewAsMap(e);
    String result;
    if (isInstanceEvent(e)) {
      result = MapUtils.getString(newAsMap, ID_FIELD);
    } else {
      result = MapUtils.getString(newAsMap, INSTANCE_ID_FIELD);
    }
    return result;
  }

  private FolioIndexOperationResponse indexFetchedInstances(List<ResourceEvent> fetchedEvents) {
    var groupedByOperation = fetchedEvents.stream().collect(groupingBy(ResourceService::getEventIndexType));
    var bulkIndexResponse = indexSearchDocuments(searchDocumentConverter.convert(fetchedEvents));
    log.info("indexInstancesById: indexed to elasticsearch [indexRequests: {}, removeRequests: {}{}]",
      getNumberOfRequests(groupedByOperation.get(INDEX)),
      getNumberOfRequests(groupedByOperation.get(DELETE)),
      getErrorMessage(bulkIndexResponse));

    return bulkIndexResponse;
  }

  private int getNumberOfRequests(List<ResourceEvent> indexDocuments) {
    return indexDocuments != null ? indexDocuments.size() : 0;
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

  private static boolean isInventoryEvent(ResourceEvent event) {
    var name = event.getResourceName();
    return ResourceType.INSTANCE.getName().equals(name)
           || ResourceType.HOLDINGS.getName().equals(name)
           || ResourceType.ITEM.getName().equals(name)
           || ResourceType.BOUND_WITH.getName().equals(name);
  }

  private static boolean isInstanceEvent(ResourceEvent event) {
    var name = event.getResourceName();
    return ResourceType.INSTANCE.getName().equals(name);
  }

  private static String getErrorMessage(FolioIndexOperationResponse bulkIndexResponse) {
    return bulkIndexResponse.getErrorMessage() != null ? ", errors: [" + bulkIndexResponse.getErrorMessage() + "]" : "";
  }
}
