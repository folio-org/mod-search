package org.folio.search.service;

import static java.lang.Boolean.TRUE;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_HUB;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_INSTANCE;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_WORK;
import static org.folio.search.utils.SearchResponseHelper.getSuccessFolioCreateIndexResponse;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.client.ResourceReindexClient;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.IndexDynamicSettings;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.domain.dto.ReindexJob;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.IndexNameProvider;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.consortium.TenantProvider;
import org.folio.search.service.es.SearchMappingsHelper;
import org.folio.search.service.es.SearchSettingsHelper;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Log4j2
@Primary
@Service
@RequiredArgsConstructor
public class IndexService {

  static final String CONCRETE_INDEX_SUFFIX = "_0";

  private static final String RESOURCE_NAME_PARAMETER = "resourceName";
  private static final String RESOURCE_STORAGE_REINDEX_URI = "{resource}-storage/reindex";

  @Value("${folio.index.alias-enabled:false}")
  private boolean aliasEnabled;

  private final IndexRepository indexRepository;
  private final SearchMappingsHelper mappingHelper;
  private final SearchSettingsHelper settingsHelper;
  private final ResourceReindexClient resourceReindexClient;
  private final ResourceDescriptionService resourceDescriptionService;
  private final IndexNameProvider indexNameProvider;
  private final TenantProvider tenantProvider;
  private final LocationService locationService;

  /**
   * Creates index for resource with pre-defined settings and mappings.
   *
   * @param resourceType name of resource as {@link ResourceType} value.
   * @param tenantId     tenant id as {@link String} value.
   * @return {@link FolioCreateIndexResponse} if index was created successfully
   * @throws SearchServiceException if {@link IOException} has been occurred during index request execution
   */
  public FolioCreateIndexResponse createIndex(ResourceType resourceType, String tenantId) {
    validateResourceName(resourceType,
      "Index cannot be created for the resource because resource description is not found.");

    var settings = settingsHelper.getSettings(resourceType);
    return doCreateIndex(resourceType, tenantId, settings);
  }

  /**
   * Creates index for resource with pre-defined mappings and modified settings.
   *
   * @param resourceName name of resource as {@link String} value.
   * @param tenantId     tenant id as {@link String} value.
   * @return {@link FolioCreateIndexResponse} if index was created successfully
   * @throws SearchServiceException if {@link IOException} has been occurred during index request execution
   */
  public FolioCreateIndexResponse createIndex(ResourceType resourceName, String tenantId, IndexSettings indexSettings) {
    validateResourceName(resourceName,
      "Index cannot be created for the resource because resource description is not found.");

    var settings = prepareIndexSettings(resourceName, indexSettings);
    return doCreateIndex(resourceName, tenantId, settings.toString());
  }

  /**
   * Updates elasticsearch index settings for resource.
   *
   * @param resourceType  resource name as {@link ResourceType} value.
   * @param tenantId      tenant id as {@link String} value.
   * @param indexSettings index settings as {@link IndexSettings} value.
   * @return {@link AcknowledgedResponse} object.
   */
  public FolioIndexOperationResponse updateIndexSettings(ResourceType resourceType, String tenantId,
                                                         IndexDynamicSettings indexSettings) {
    log.debug("updateIndexSettings:: by [resourceType: {}, tenantId: {}]", resourceType, tenantId);

    validateResourceName(resourceType, "Index Settings cannot be updated, resource name is invalid.");

    var index = indexNameProvider.getIndexName(resourceType, tenantId);
    var settings = prepareIndexDynamicSettings(indexSettings);

    log.info("updateIndexSettings:: Attempting to update settings by [indexName: {}, settings: {}]", index, settings);
    return indexRepository.updateIndexSettings(index, settings.toString());
  }

  /**
   * Updates elasticsearch index mappings for resource.
   *
   * @param resourceType resource name as {@link ResourceType} value.
   * @param tenantId     tenant id as {@link String} value.
   * @return {@link AcknowledgedResponse} object.
   */
  public FolioIndexOperationResponse updateMappings(ResourceType resourceType, String tenantId) {
    validateResourceName(resourceType, "Mappings cannot be updated, resource name is invalid.");
    var index = indexNameProvider.getIndexName(resourceType, tenantId);
    var mappings = mappingHelper.getMappings(resourceType);

    log.info("updateMappings:: Attempting to update mappings by [indexName: {}, mappings: {}]", index, mappings);
    return indexRepository.updateMappings(index, mappings);
  }

  /**
   * Creates Elasticsearch index if it is not exist.
   *
   * @param resourceType - resource name as {@link String} object.
   * @param tenantId     - tenant id as {@link String} object
   */
  public void createIndexIfNotExist(ResourceType resourceType, String tenantId) {
    var index = indexNameProvider.getIndexName(resourceType, tenantId);
    if (!indexRepository.indexExists(index)) {
      createIndex(resourceType, tenantId);
    }
  }

  /**
   * Creates Elasticsearch index if it is not exist with provided settings.
   *
   * @param resourceType - resource name as {@link ResourceType} object.
   * @param tenantId     - tenant id as {@link String} object
   * @param indexSettings - index settings as {@link IndexSettings} object
   */
  public void createIndexIfNotExist(ResourceType resourceType, String tenantId, IndexSettings indexSettings) {
    var index = indexNameProvider.getIndexName(resourceType, tenantId);
    if (!indexRepository.indexExists(index)) {
      createIndex(resourceType, tenantId, indexSettings);
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
    var resource = normalizeResourceName(resources.getFirst());
    if (reindexRequest != null && TRUE.equals(reindexRequest.getRecreateIndex())
        && notConsortiumMemberTenant(tenantId)) {
      resources.forEach(resourceName -> {
        dropIndex(resourceName, tenantId);
        createIndex(resourceName, tenantId, reindexRequest.getIndexSettings());
      });
    }

    if (ResourceType.LOCATION.getName().equals(resource)) {
      return reindexInventoryLocations(tenantId, resources);
    } else if (isLinkedDataResource(resource)) {
      return new ReindexJob();
    } else {
      return reindexInventoryAsync(resource);
    }
  }

  /**
   * Runs reindex request for mod-inventory-storage.
   *
   * @param resource - resource name as {@link String} object
   */
  public ReindexJob reindexInventoryAsync(String resource) {
    var reindexUri = fromUriString(RESOURCE_STORAGE_REINDEX_URI).buildAndExpand(resource).toUri();
    log.info("reindexInventoryAsync:: Starting reindex for uri {}", reindexUri);
    return resourceReindexClient.submitReindex(reindexUri);
  }

  /**
   * Runs synchronous locations and location-units reindex in mod-search.
   */
  public ReindexJob reindexInventoryLocations(String tenantId, List<ResourceType> resources) {
    log.info("reindexInventoryLocations:: Starting reindex");
    var response = new ReindexJob().id(UUID.randomUUID().toString())
      .jobStatus("Completed")
      .submittedDate(new Date().toString());

    resources.forEach(resourceType -> locationService.reindex(tenantId, resourceType));
    log.info("reindexInventoryLocations:: Reindex completed");

    return response;
  }

  /**
   * Drops Elasticsearch index for given resource name and tenant id.
   * When alias mode is enabled, resolves the alias to its concrete index and drops that.
   *
   * @param resource - resource name as {@link String} object.
   * @param tenant   - tenant id as {@link String} object
   */
  public void dropIndex(ResourceType resource, String tenant) {
    log.debug("dropIndex:: by [resource: {}, tenant: {}]", resource, tenant);

    var logicalName = indexNameProvider.getIndexName(resource, tenant);
    var indexToDrop = aliasEnabled
      ? indexRepository.getAliasWriteIndex(logicalName).orElse(logicalName + CONCRETE_INDEX_SUFFIX)
      : logicalName;

    if (indexRepository.indexExists(indexToDrop)) {
      log.info("dropIndex:: Dropping index [indexToDrop: {}, logicalName: {}]", indexToDrop, logicalName);
      indexRepository.dropIndex(indexToDrop);
    }
  }

  /**
   * Atomically switches the alias for every resource type of a tenant from its current concrete index
   * to the concrete index at {@code toVersion} (e.g. version 1 → {@code folio_instance_diku_v2_1}).
   * The target concrete index must already exist and be populated before calling this method.
   *
   * @param tenantId  the tenant whose aliases should be switched
   * @param toVersion the numeric version suffix of the target concrete index
   * @return map of resource name → "fromIndex -> toIndex" describing each switch performed
   */
  public Map<String, String> switchAlias(String tenantId, int toVersion) {
    log.info("switchAlias:: Switching all aliases [tenantId: {}, toVersion: {}]", tenantId, toVersion);
    return resourceDescriptionService.getResourceTypes().stream()
      .collect(Collectors.toMap(ResourceType::getName, rt -> switchAliasForResource(rt, tenantId, toVersion)));
  }

  private String switchAliasForResource(ResourceType resourceType, String tenantId, int toVersion) {
    var aliasName = indexNameProvider.getIndexName(resourceType, tenantId);
    var currentConcrete = indexRepository.getAliasWriteIndex(aliasName)
      .orElseThrow(() -> new SearchServiceException(
        "No alias found for [" + aliasName + "]. "
          + "Ensure INDEX_ALIAS_ENABLED=true was set when this tenant was initialized."));
    var targetConcrete = aliasName + "_" + toVersion;
    if (!indexRepository.indexExists(targetConcrete)) {
      throw new SearchServiceException(
        "Target index [" + targetConcrete + "] does not exist. Create and populate it before switching.");
    }
    log.info("switchAlias:: [alias: {}, from: {}, to: {}]", aliasName, currentConcrete, targetConcrete);
    indexRepository.switchAlias(aliasName, currentConcrete, targetConcrete);
    return currentConcrete + " -> " + targetConcrete;
  }

  private FolioCreateIndexResponse doCreateIndex(ResourceType resourceName, String tenantId, String indexSettings) {
    log.debug("createIndex:: by [resourceName: {}, tenantId: {}, aliasEnabled: {}]",
      resourceName, tenantId, aliasEnabled);

    var aliasName = indexNameProvider.getIndexName(resourceName, tenantId);
    var mappings = mappingHelper.getMappings(resourceName);

    if (aliasEnabled) {
      var concreteIndex = aliasName + CONCRETE_INDEX_SUFFIX;
      log.info("doCreateIndex:: Creating concrete index and alias [alias: {}, concrete: {}]", aliasName, concreteIndex);
      var response = indexRepository.createIndex(concreteIndex, indexSettings, mappings);
      if (response.getStatus() != null && response.getStatus().equals("error")) {
        return response;
      }
      indexRepository.createAlias(aliasName, concreteIndex);
      return getSuccessFolioCreateIndexResponse(List.of(aliasName, concreteIndex));
    }

    log.info("doCreateIndex:: Attempting to create index by [indexName: {}, mappings: {}, settings: {}]",
      aliasName, mappings, indexSettings);
    return indexRepository.createIndex(aliasName, indexSettings, mappings);
  }

  private List<ResourceType> getResourceNamesToReindex(ReindexRequest reindexRequest) {
    log.debug("getResourceNamesToReindex:: by [reindexRequest: {}]", reindexRequest);

    var resourceName = getReindexRequestResourceType(reindexRequest);
    var resourceDescription = resourceDescriptionService.find(resourceName);
    if (resourceDescription.isEmpty()
        || resourceDescription.get().getParent() != null
        || !resourceDescription.get().isReindexSupported()) {
      throw new RequestValidationException(
        "Reindex request contains invalid resource name", RESOURCE_NAME_PARAMETER, resourceName.getName());
    }

    var resourceNames = new ArrayList<ResourceType>();
    resourceNames.add(resourceName);
    resourceNames.addAll(resourceDescriptionService.getSecondaryResourceTypes(resourceName));
    return resourceNames;
  }

  private JsonNode prepareIndexSettings(ResourceType resourceName, IndexSettings indexSettings) {
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
    var settings = settingsHelper.getDynamicSettings();

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

  private static ResourceType getReindexRequestResourceType(ReindexRequest req) {
    return ResourceType.byName(req.getResourceName().getValue());
  }

  private void validateResourceName(ResourceType resourceName, String message) {
    var resourceDescription = resourceDescriptionService.find(resourceName)
      .orElseThrow(() -> new RequestValidationException(message, RESOURCE_NAME_PARAMETER, resourceName.getName()));
    log.trace("Resource description was found for {}", resourceDescription.getName());
  }

  private boolean notConsortiumMemberTenant(String tenantId) {
    return tenantId.equals(tenantProvider.getTenant(tenantId));
  }

  private static String normalizeResourceName(ResourceType resourceType) {
    return resourceType.getName().replace("_", "-");
  }

  private boolean isLinkedDataResource(String resource) {
    return LINKED_DATA_INSTANCE.getName().equals(resource)
      || LINKED_DATA_WORK.getName().equals(resource)
      || LINKED_DATA_HUB.getName().equals(resource);
  }
}
