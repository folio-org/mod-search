package org.folio.search.service;

import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.CollectionUtils.mergeSafelyToList;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.folio.search.client.ResourceReindexClient;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ReindexJob;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.integration.ResourceFetchService;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.service.es.SearchMappingsHelper;
import org.folio.search.service.es.SearchSettingsHelper;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.utils.SearchUtils;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class IndexService {

  private final IndexRepository indexRepository;
  private final SearchMappingsHelper mappingHelper;
  private final SearchSettingsHelper settingsHelper;
  private final ResourceFetchService resourceFetchService;
  private final ResourceReindexClient resourceReindexClient;
  private final ResourceDescriptionService resourceDescriptionService;
  private final MultiTenantSearchDocumentConverter multiTenantSearchDocumentConverter;

  /**
   * Creates index for resource with pre-defined settings and mappings.
   *
   * @param resourceName name of resource as {@link String} value.
   * @param tenantId tenant id as {@link String} value.
   * @return {@link FolioCreateIndexResponse} if index was created successfully
   * @throws SearchServiceException if {@link IOException} has been occurred during index request execution
   */
  public FolioCreateIndexResponse createIndex(String resourceName, String tenantId) {
    validateResourceName(resourceName);
    var index = getElasticsearchIndexName(resourceName, tenantId);
    var settings = settingsHelper.getSettings(resourceName);
    var mappings = mappingHelper.getMappings(resourceName);

    log.info("Creating index for resource [resource: {}, tenant: {}, mappings: {}, settings: {}]",
      resourceName, tenantId, mappings, settings);
    return indexRepository.createIndex(index, settings, mappings);
  }

  /**
   * Updates elasticsearch index mappings for resource.
   *
   * @param resourceName resource name as {@link String} value.
   * @param tenantId tenant id as {@link String} value.
   * @return {@link AcknowledgedResponse} object.
   */
  public FolioIndexOperationResponse updateMappings(String resourceName, String tenantId) {
    validateResourceName(resourceName);
    var index = getElasticsearchIndexName(resourceName, tenantId);
    var mappings = mappingHelper.getMappings(resourceName);

    log.info("Updating mappings for resource [resource: {}, tenant: {}, mappings: {}]",
      resourceName, tenantId, mappings);
    return indexRepository.updateMappings(index, mappings);
  }

  /**
   * Saves list of resources to elasticsearch.
   *
   * @param resources {@link List} of resources as {@link ResourceEvent} objects.
   * @return index operation response as {@link FolioIndexOperationResponse} object
   */
  public FolioIndexOperationResponse indexResources(List<ResourceEvent> resources) {
    if (CollectionUtils.isEmpty(resources)) {
      return getSuccessIndexOperationResponse();
    }

    var eventsToIndex = getEventsThatCanBeIndexed(resources, SearchUtils::getElasticsearchIndexName);
    var elasticsearchDocuments = multiTenantSearchDocumentConverter.convert(eventsToIndex);
    var response = indexRepository.indexResources(elasticsearchDocuments);

    log.info("Records added/updated [size: {}]", eventsToIndex.size());
    return response;
  }

  /**
   * Index list of resource id event to elasticsearch.
   *
   * @param resourceIdEvents list of {@link ResourceIdEvent} objects.
   * @return index operation response as {@link FolioIndexOperationResponse} object
   */
  public FolioIndexOperationResponse indexResourcesById(List<ResourceIdEvent> resourceIdEvents) {
    if (CollectionUtils.isEmpty(resourceIdEvents)) {
      return getSuccessIndexOperationResponse();
    }

    var eventsToIndex = getEventsThatCanBeIndexed(resourceIdEvents, SearchUtils::getElasticsearchIndexName);

    var groupedByOperation = eventsToIndex.stream().collect(groupingBy(ResourceIdEvent::getAction));
    var fetchedInstances = resourceFetchService.fetchInstancesByIds(groupedByOperation.get(INDEX));
    var indexDocuments = multiTenantSearchDocumentConverter.convertAsMap(fetchedInstances);
    var removeDocuments = multiTenantSearchDocumentConverter.convertDeleteEventsAsMap(groupedByOperation.get(DELETE));
    var searchDocumentBodies = mergeSafelyToList(indexDocuments.values(), removeDocuments.values());

    var response = indexRepository.indexResources(searchDocumentBodies);
    log.info("Records indexed to elasticsearch [indexRequests: {}, removeRequests: {}]",
      indexDocuments.size(), removeDocuments.size());

    return response;
  }

  /**
   * Creates Elasticsearch index if it is not exist.
   *
   * @param resourceName - resource name as {@link String} object.
   * @param tenantId - tenant id as {@link String} object
   */
  public void createIndexIfNotExist(String resourceName, String tenantId) {
    var index = getElasticsearchIndexName(resourceName, tenantId);
    if (!indexRepository.indexExists(index)) {
      createIndex(resourceName, tenantId);
    }
  }

  /**
   * Runs reindex request for mod-inventory-storage.
   *
   * @param tenantId - tenant id as {@link String} object
   * @param reindexRequest - reindex request as {@link ReindexRequest} object
   */
  public ReindexJob reindexInventory(String tenantId, ReindexRequest reindexRequest) {
    var resourceName = getResourceNameToReindex(reindexRequest);
    if (reindexRequest != null && TRUE.equals(reindexRequest.getRecreateIndex())) {
      log.info("Recreating indices during reindex operation [tenant: {}]", tenantId);
      dropIndex(resourceName, tenantId);
      createIndex(resourceName, tenantId);
    }

    var reindexUri = fromUriString("http://{resource}-storage/reindex").buildAndExpand(resourceName).toUri();
    return resourceReindexClient.submitReindex(reindexUri);
  }

  /**
   * Drops Elasticsearch index for given resource name and tenant id.
   *
   * @param resource - resource name as {@link String} object.
   * @param tenant - tenant id as {@link String} object
   */
  public void dropIndex(String resource, String tenant) {
    var index = getElasticsearchIndexName(resource, tenant);
    if (indexRepository.indexExists(index)) {
      indexRepository.dropIndex(index);
    }
  }

  private <T> List<T> getEventsThatCanBeIndexed(List<T> events, Function<T, String> eventToIndexNameFunc) {
    var esIndices = events.stream().map(eventToIndexNameFunc).collect(toSet());
    var existingIndices = esIndices.stream().filter(indexRepository::indexExists).collect(toSet());
    var eventsToIndex = new ArrayList<T>();
    var unknownEvents = new ArrayList<T>();

    for (T event : events) {
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

  private String getResourceNameToReindex(ReindexRequest reindexRequest) {
    if (reindexRequest == null || StringUtils.isBlank(reindexRequest.getResourceName())) {
      return INSTANCE_RESOURCE;
    }

    validateResourceName(reindexRequest.getResourceName());
    return reindexRequest.getResourceName();
  }

  private void validateResourceName(String resourceName) {
    var resourceDescription = resourceDescriptionService.get(resourceName);
    if (resourceDescription == null || !resourceDescription.isPrimary()) {
      throw new RequestValidationException(
        "Reindex request contains invalid resource name", "resourceName", resourceName);
    }
  }
}
