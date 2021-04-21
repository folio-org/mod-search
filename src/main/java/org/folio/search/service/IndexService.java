package org.folio.search.service;

import static java.util.stream.Collectors.toList;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.folio.search.client.InstanceStorageClient;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ReindexJob;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.integration.ResourceFetchService;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.service.es.SearchMappingsHelper;
import org.folio.search.service.es.SearchSettingsHelper;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class IndexService {

  private final IndexRepository indexRepository;
  private final SearchMappingsHelper mappingHelper;
  private final SearchSettingsHelper settingsHelper;
  private final MultiTenantSearchDocumentConverter multiTenantSearchDocumentConverter;
  private final InstanceStorageClient instanceStorageClient;
  private final ResourceFetchService resourceFetchService;

  /**
   * Creates index for resource with pre-defined settings and mappings.
   *
   * @param resourceName name of resource as {@link String} value.
   * @param tenantId tenant id as {@link String} value.
   * @return {@link FolioCreateIndexResponse} if index was created successfully
   * @throws SearchServiceException if {@link IOException} has been occurred during execution request to
   *     elasticsearch
   */
  public FolioCreateIndexResponse createIndex(String resourceName, String tenantId) {
    var index = getElasticsearchIndexName(resourceName, tenantId);
    var settings = settingsHelper.getSettings(resourceName);
    var mappings = mappingHelper.getMappings(resourceName);

    log.info("Creating mappings for resource [resource: {}, tenant: {}, mappings: {}]",
      resourceName, tenantId, mappings);
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
    var index = getElasticsearchIndexName(resourceName, tenantId);
    var mappings = mappingHelper.getMappings(resourceName);

    log.info("Updating mappings for resource [resource: {}, tenant: {}, mappings: {}]",
      resourceName, tenantId, mappings);
    return indexRepository.updateMappings(index, mappings);
  }

  /**
   * Saves list of resources to elasticsearch.
   *
   * @param resources {@link List} of resources as {@link JsonNode} objects.
   */
  public FolioIndexOperationResponse indexResources(List<ResourceEventBody> resources) {
    if (CollectionUtils.isEmpty(resources)) {
      return getSuccessIndexOperationResponse();
    }

    checkThatResourceEventsCanBeIndexed(resources);
    return addResourcesToIndex(resources);
  }

  public FolioIndexOperationResponse indexResourcesById(List<ResourceIdEvent> ids) {
    if (CollectionUtils.isEmpty(ids)) {
      return getSuccessIndexOperationResponse();
    }

    checkThatResourceIdsCanBeIndexed(ids);
    return addResourcesToIndex(resourceFetchService.fetchInstancesByIds(ids));
  }

  public FolioIndexOperationResponse removeResources(List<ResourceIdEvent> resources) {
    if (CollectionUtils.isEmpty(resources)) {
      return getSuccessIndexOperationResponse();
    }

    checkThatResourceIdsCanBeIndexed(resources);

    var deleteEvents = multiTenantSearchDocumentConverter.convertDeleteEvents(resources);
    var response = indexRepository.removeResources(deleteEvents);

    log.info("Resources removed [size: {}]", resources.size());
    return response;
  }

  public void createIndexIfNotExist(String resourceName, String tenantId) {
    var index = getElasticsearchIndexName(resourceName, tenantId);
    if (!indexRepository.indexExists(index)) {
      createIndex(resourceName, tenantId);
    }
  }

  public ReindexJob reindexInventory() {
    return instanceStorageClient.submitReindex();
  }

  public void dropIndex(String resource, String tenant) {
    var index = getElasticsearchIndexName(resource, tenant);
    if (indexRepository.indexExists(index)) {
      indexRepository.dropIndex(index);
    }
  }

  private void checkThatResourceIdsCanBeIndexed(List<ResourceIdEvent> events) {
    checkThatDocumentsCanBeIndexed(events.stream()
      .map(id -> getElasticsearchIndexName(id.getType(), id.getTenant()))
      .collect(Collectors.toSet()));
  }

  private void checkThatResourceEventsCanBeIndexed(List<ResourceEventBody> events) {
    checkThatDocumentsCanBeIndexed(events.stream()
      .map(id -> getElasticsearchIndexName(id.getResourceName(), id.getTenant()))
      .collect(Collectors.toSet()));
  }

  private void checkThatDocumentsCanBeIndexed(Set<String> indexes) {
    var absentIndexNames = indexes.stream()
      .filter(index -> !indexRepository.indexExists(index))
      .collect(toList());

    if (CollectionUtils.isNotEmpty(absentIndexNames)) {
      throw new SearchServiceException(String.format(
        "Cancelling bulk operation [reason: Cannot index resources for non existing indices, "
          + "tenant not initialized? [indices=%s]]", absentIndexNames));
    }
  }

  private FolioIndexOperationResponse addResourcesToIndex(List<ResourceEventBody> resources) {
    var elasticsearchDocuments = multiTenantSearchDocumentConverter.convert(resources);
    var response = indexRepository.indexResources(elasticsearchDocuments);

    log.info("Instances added/updated [size: {}]", resources.size());
    return response;
  }
}
