package org.folio.search.service;

import static java.lang.Boolean.TRUE;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.client.ResourceReindexClient;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.IndexDynamicSettings;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.domain.dto.ReindexJob;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.repository.IndexNameProvider;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.consortium.TenantProvider;
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
  private final IndexNameProvider indexNameProvider;
  private final TenantProvider tenantProvider;

  /**
   * Creates index for resource with pre-defined settings and mappings.
   *
   * @param resourceName name of resource as {@link String} value.
   * @param tenantId     tenant id as {@link String} value.
   * @return {@link FolioCreateIndexResponse} if index was created successfully
   * @throws SearchServiceException if {@link IOException} has been occurred during index request execution
   */
  public FolioCreateIndexResponse createIndex(String resourceName, String tenantId) {
    validateResourceName(resourceName,
      "Index cannot be created for the resource because resource description is not found.");

    var settings = settingsHelper.getSettings(resourceName);
    return doCreateIndex(resourceName, tenantId, settings);
  }

  /**
   * Creates index for resource with pre-defined mappings and modified settings.
   *
   * @param resourceName name of resource as {@link String} value.
   * @param tenantId     tenant id as {@link String} value.
   * @return {@link FolioCreateIndexResponse} if index was created successfully
   * @throws SearchServiceException if {@link IOException} has been occurred during index request execution
   */
  public FolioCreateIndexResponse createIndex(String resourceName, String tenantId, IndexSettings indexSettings) {
    validateResourceName(resourceName,
      "Index cannot be created for the resource because resource description is not found.");

    var settings = prepareIndexSettings(resourceName, indexSettings);
    return doCreateIndex(resourceName, tenantId, settings.toString());
  }

  /**
   * Updates elasticsearch index settings for resource.
   *
   * @param resourceName  resource name as {@link String} value.
   * @param tenantId      tenant id as {@link String} value.
   * @param indexSettings index settings as {@link IndexSettings} value.
   * @return {@link AcknowledgedResponse} object.
   */
  public FolioIndexOperationResponse updateIndexSettings(String resourceName, String tenantId,
                                                         IndexDynamicSettings indexSettings) {
    log.debug("updateIndexSettings:: by [resourceName: {}, tenantId: {}]", resourceName, tenantId);

    validateResourceName(resourceName, "Index Settings cannot be updated, resource name is invalid.");

    var index = indexNameProvider.getIndexName(resourceName, tenantId);
    var settings = prepareIndexDynamicSettings(indexSettings);

    log.info("Attempts to update settings by [indexName: {}, settings: {}]", index, settings);
    return indexRepository.updateIndexSettings(index, settings.toString());
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
    var index = indexNameProvider.getIndexName(resourceName, tenantId);
    var mappings = mappingHelper.getMappings(resourceName);

    log.info("Attempts to update mappings by [indexName: {}, mappings: {}]", index, mappings);
    return indexRepository.updateMappings(index, mappings);
  }

  /**
   * Creates Elasticsearch index if it is not exist.
   *
   * @param resourceName - resource name as {@link String} object.
   * @param tenantId     - tenant id as {@link String} object
   */
  public void createIndexIfNotExist(String resourceName, String tenantId) {
    var index = indexNameProvider.getIndexName(resourceName, tenantId);
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
    if (reindexRequest != null && TRUE.equals(reindexRequest.getRecreateIndex())
      && notConsortiumMemberTenant(tenantId)) {
      resources.forEach(resourceName -> {
        dropIndex(resourceName, tenantId);
        createIndex(resourceName, tenantId, reindexRequest.getIndexSettings());
      });
    }
    var resource = normalizeResourceName(resources.get(0));
    var reindexUri = fromUriString(RESOURCE_STORAGE_REINDEX_URI).buildAndExpand(resource).toUri();
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

    var index = indexNameProvider.getIndexName(resource, tenant);
    if (indexRepository.indexExists(index)) {
      indexRepository.dropIndex(index);
    }
  }

  private FolioCreateIndexResponse doCreateIndex(String resourceName, String tenantId, String indexSettings) {
    log.debug("createIndex:: by [resourceName: {}, tenantId: {}]", resourceName, tenantId);

    var index = indexNameProvider.getIndexName(resourceName, tenantId);
    var mappings = mappingHelper.getMappings(resourceName);

    log.info("Attempts to create index by [indexName: {}, mappings: {}, settings: {}]",
      index, mappings, indexSettings);
    return indexRepository.createIndex(index, indexSettings, mappings);
  }

  private List<String> getResourceNamesToReindex(ReindexRequest reindexRequest) {
    log.debug("getResourceNamesToReindex:: by [reindexRequest: {}]", reindexRequest);

    var resourceName = getReindexRequestResourceName(reindexRequest);
    var resourceDescription = resourceDescriptionService.find(resourceName);
    if (resourceDescription.isEmpty()
      || resourceDescription.get().getParent() != null
      || !resourceDescription.get().isReindexSupported()) {
      throw new RequestValidationException(
        "Reindex request contains invalid resource name", RESOURCE_NAME_PARAMETER, resourceName);
    }

    var resourceNames = new ArrayList<String>();
    resourceNames.add(resourceName);
    resourceNames.addAll(resourceDescriptionService.getSecondaryResourceNames(resourceName));
    return resourceNames;
  }

  private JsonNode prepareIndexSettings(String resourceName, IndexSettings indexSettings) {
    var settings = settingsHelper.getSettingsJson(resourceName);

    if (indexSettings != null) {
      var indexSettingsJson = (ObjectNode) settings.get("index");

      Optional.ofNullable(indexSettings.getNumberOfShards())
        .ifPresent(shardsNum -> indexSettingsJson.put("number_of_shards", shardsNum));

      updateNumberOfReplicas(indexSettingsJson, indexSettings.getNumberOfReplicas());
      updateRefreshInterval(indexSettingsJson, indexSettings.getRefreshInterval());
    }

    return settings;
  }

  private JsonNode prepareIndexDynamicSettings(IndexDynamicSettings indexSettings) {
    var settings = settingsHelper.getSettingsJson("dynamicSettings");

    if (indexSettings != null) {
      var indexSettingsJson = (ObjectNode) settings.get("index");

      updateNumberOfReplicas(indexSettingsJson, indexSettings.getNumberOfReplicas());
      updateRefreshInterval(indexSettingsJson, indexSettings.getRefreshInterval());
    }

    return settings;
  }

  private void updateNumberOfReplicas(ObjectNode settings, Integer numberOfReplicas) {
    Optional.ofNullable(numberOfReplicas)
      .ifPresent(replicasNum -> settings.put("number_of_replicas", replicasNum));
  }

  private void updateRefreshInterval(ObjectNode settings, Integer refreshInt) {
    if (refreshInt != null && refreshInt != 0) {
      settings.put("refresh_interval", refreshInt == -1 ? "-1" : refreshInt + "s");
    }
  }

  private static String getReindexRequestResourceName(ReindexRequest req) {
    return req == null || StringUtils.isBlank(req.getResourceName()) ? INSTANCE_RESOURCE : req.getResourceName();
  }

  private void validateResourceName(String resourceName, String message) {
    var resourceDescription = resourceDescriptionService.find(resourceName)
      .orElseThrow(() -> new RequestValidationException(message, RESOURCE_NAME_PARAMETER, resourceName));
    log.trace("Resource description was found for {}", resourceDescription.getName());
  }

  private boolean notConsortiumMemberTenant(String tenantId) {
    return tenantId.equals(tenantProvider.getTenant(tenantId));
  }

  private static String normalizeResourceName(String url) {
    return url.replace("_", "-");
  }
}
