package org.folio.search.service;

import static java.lang.Boolean.TRUE;
import static org.folio.search.utils.CommonUtils.listToLogMsg;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.client.ResourceReindexClient;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ReindexJob;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.es.SearchMappingsHelper;
import org.folio.search.service.es.SearchSettingsHelper;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.opensearch.action.support.master.AcknowledgedResponse;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class IndexService {

  private static final String RESOURCE_NAME_PARAMETER = "resourceName";
  private static final String RESOURCE_STORAGE_REINDEX_URI = "http://{resource}-storage/reindex";

  private final IndexRepository indexRepository;
  private final SearchMappingsHelper mappingHelper;
  private final SearchSettingsHelper settingsHelper;
  private final ResourceReindexClient resourceReindexClient;
  private final ResourceDescriptionService resourceDescriptionService;

  /**
   * Creates index for resource with pre-defined settings and mappings.
   *
   * @param resourceName name of resource as {@link String} value.
   * @param tenantId     tenant id as {@link String} value.
   * @return {@link FolioCreateIndexResponse} if index was created successfully
   * @throws SearchServiceException if {@link IOException} has been occurred during index request execution
   */
  public FolioCreateIndexResponse createIndex(String resourceName, String tenantId) {
    log.debug("createIndex:: by [resourceName: {}, tenantId: {}]",
      resourceName, tenantId);

    validateResourceName(resourceName,
      "Index cannot be created for the resource because resource description is not found.");

    var index = getIndexName(resourceName, tenantId);
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
   * @param tenantId     tenant id as {@link String} value.
   * @return {@link AcknowledgedResponse} object.
   */
  public FolioIndexOperationResponse updateMappings(String resourceName, String tenantId) {
    validateResourceName(resourceName, "Mappings cannot be updated, resource name is invalid.");
    var index = getIndexName(resourceName, tenantId);
    var mappings = mappingHelper.getMappings(resourceName);

    log.info("Updating mappings for resource [resource: {}, tenant: {}, mappings: {}]",
      resourceName, tenantId, mappings);
    return indexRepository.updateMappings(index, mappings);
  }

  /**
   * Creates Elasticsearch index if it is not exist.
   *
   * @param resourceName - resource name as {@link String} object.
   * @param tenantId     - tenant id as {@link String} object
   */
  public void createIndexIfNotExist(String resourceName, String tenantId) {
    var index = getIndexName(resourceName, tenantId);
    if (!indexRepository.indexExists(index)) {
      createIndex(resourceName, tenantId);
    }
  }

  /**
   * Runs reindex request for mod-inventory-storage.
   *
   * @param tenantId       - tenant id as {@link String} object
   * @param reindexRequest - reindex request as {@link ReindexRequest} object
   */
  public ReindexJob reindexInventory(String tenantId, ReindexRequest reindexRequest) {
    var resources = getResourceNamesToReindex(reindexRequest);
    if (reindexRequest != null && TRUE.equals(reindexRequest.getRecreateIndex())) {
      log.info("Recreating indices during reindex operation [tenant: {}, resources: {}]",
        tenantId, listToLogMsg(resources));
      resources.forEach(resourceName -> {
        dropIndex(resourceName, tenantId);
        createIndex(resourceName, tenantId);
      });
    }

    var resource = normalizeResourceName(resources.get(0));
    var reindexUri = fromUriString(RESOURCE_STORAGE_REINDEX_URI).buildAndExpand(resource).toUri();
    log.info("reindexInventory:: result: reindex job has been created [reindexUri: {}]", reindexUri);

    return resourceReindexClient.submitReindex(reindexUri);
  }

  /**
   * Drops Elasticsearch index for given resource name and tenant id.
   *
   * @param resource - resource name as {@link String} object.
   * @param tenant   - tenant id as {@link String} object
   */
  public void dropIndex(String resource, String tenant) {
    log.debug("dropIndex:: by [resource: {}, tenant: {}]", resource, tenant);

    var index = getIndexName(resource, tenant);
    if (indexRepository.indexExists(index)) {
      indexRepository.dropIndex(index);
    }
  }

  private List<String> getResourceNamesToReindex(ReindexRequest reindexRequest) {
    log.debug("getResourceNamesToReindex:: by [reindexRequest: {}]", reindexRequest);

    var resourceName = getReindexRequestResourceName(reindexRequest);
    var resourceDescription = resourceDescriptionService.get(resourceName);
    if (resourceDescription == null
      || resourceDescription.getParent() != null
      || !resourceDescription.isReindexSupported()) {
      throw new RequestValidationException(
        "Reindex request contains invalid resource name", RESOURCE_NAME_PARAMETER, resourceName);
    }

    var resourceNames = new ArrayList<String>();
    resourceNames.add(resourceName);
    resourceNames.addAll(resourceDescriptionService.getSecondaryResourceNames(resourceName));
    log.info("getResourceNamesToReindex: result: {}", listToLogMsg(resourceNames));

    return resourceNames;
  }

  private static String getReindexRequestResourceName(ReindexRequest req) {
    return req == null || StringUtils.isBlank(req.getResourceName()) ? INSTANCE_RESOURCE : req.getResourceName();
  }

  private void validateResourceName(String resourceName, String message) {
    if (resourceDescriptionService.get(resourceName) == null) {
      throw new RequestValidationException(message, RESOURCE_NAME_PARAMETER, resourceName);
    }
  }

  private static String normalizeResourceName(String url) {
    return url.replace("_", "-");
  }
}
