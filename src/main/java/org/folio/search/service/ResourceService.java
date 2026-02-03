package org.folio.search.service;

import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
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
      resourceEvents.getFirst().getType(), SearchUtils.getNumberOfRequests(elasticsearchDocuments),
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

  private static String getErrorMessage(FolioIndexOperationResponse bulkIndexResponse) {
    return bulkIndexResponse.getErrorMessage() != null ? ", errors: [" + bulkIndexResponse.getErrorMessage() + "]" : "";
  }
}
