package org.folio.search.service;

import static java.util.Locale.ROOT;
import static org.folio.search.configuration.SearchCacheNames.ACTIVE_INDEX_FAMILY_CACHE;
import static org.folio.search.configuration.SearchCacheNames.CUTTING_OVER_INDEX_FAMILY_CACHE;
import static org.folio.search.configuration.SearchCacheNames.PHYSICAL_INDEX_EXISTS_CACHE;
import static org.folio.search.model.types.IndexFamilyStatus.ACTIVE;
import static org.folio.search.model.types.IndexFamilyStatus.BUILDING;
import static org.folio.search.model.types.IndexFamilyStatus.CUTTING_OVER;
import static org.folio.search.model.types.IndexFamilyStatus.RETIRED;
import static org.folio.search.model.types.IndexFamilyStatus.RETIRING;
import static org.folio.search.model.types.IndexFamilyStatus.STAGED;
import static org.folio.spring.config.properties.FolioEnvironment.getFolioEnvName;
import static org.opensearch.common.xcontent.XContentType.JSON;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.reindex.IndexFamilyEntity;
import org.folio.search.model.reindex.runtime.V2ReindexPhaseType;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.IndexNameProvider;
import org.folio.search.service.es.SearchMappingsHelper;
import org.folio.search.service.es.SearchSettingsHelper;
import org.folio.search.service.reindex.ReindexKafkaConsumerManager;
import org.folio.search.service.reindex.V2ReindexRuntimeStatusTracker;
import org.folio.search.service.reindex.jdbc.IndexFamilyRepository;
import org.folio.search.service.reindex.jdbc.StreamingReindexStatusRepository;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.opensearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.opensearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class IndexFamilyService {

  public static final List<ResourceType> V2_BROWSE_RESOURCE_TYPES = List.of(
    ResourceType.V2_CONTRIBUTOR,
    ResourceType.V2_SUBJECT,
    ResourceType.V2_CLASSIFICATION,
    ResourceType.V2_CALL_NUMBER
  );

  private final IndexFamilyRepository indexFamilyRepository;
  private final RestHighLevelClient elasticsearchClient;
  private final SearchSettingsHelper searchSettingsHelper;
  private final SearchMappingsHelper searchMappingsHelper;
  private final JsonConverter jsonConverter;
  private final IndexNameProvider indexNameProvider;
  private final ReindexKafkaConsumerManager reindexKafkaConsumerManager;
  private final StreamingReindexStatusRepository streamingReindexStatusRepository;
  private final FolioExecutionContext context;
  @Nullable
  private final V2ReindexRuntimeStatusTracker runtimeStatusTracker;

  public IndexFamilyEntity allocateNewFamily(String tenantId, QueryVersion version, IndexSettings indexSettings) {
    var generation = indexFamilyRepository.getNextGeneration(version);
    var physicalIndexName = buildPhysicalIndexName(tenantId, version, generation);

    log.info("allocateNewFamily:: allocating new family [tenant: {}, version: {}, generation: {}, index: {},"
        + " indexSettings: {}]",
      tenantId, version, generation, physicalIndexName, indexSettings);

    createPhysicalIndex(physicalIndexName, version, indexSettings);

    if (version == QueryVersion.V2) {
      for (var browseType : V2_BROWSE_RESOURCE_TYPES) {
        var browseIndexName = getV2BrowsePhysicalIndexName(tenantId, browseType, generation);
        createBrowsePhysicalIndex(browseIndexName, browseType, indexSettings);
      }
    }

    var entity = new IndexFamilyEntity(
      UUID.randomUUID(),
      generation,
      physicalIndexName,
      BUILDING,
      Timestamp.from(Instant.now()),
      null,
      null,
      version
    );

    indexFamilyRepository.create(entity);
    return entity;
  }

  public String getAliasName(String tenantId, QueryVersion version) {
    return getFolioEnvName().toLowerCase(ROOT) + "_" + version.getIndexPrefix() + "_" + tenantId;
  }

  public String getV2BrowseAliasName(String tenantId, ResourceType browseType) {
    return getFolioEnvName().toLowerCase(ROOT) + "_" + browseType.getName() + "_" + tenantId;
  }

  public String getV2BrowsePhysicalIndexName(String tenantId, ResourceType browseType, int generation) {
    return getFolioEnvName().toLowerCase(ROOT) + "_" + browseType.getName() + "_" + tenantId + "_" + generation;
  }

  public Map<ResourceType, String> getV2BrowseAliasMap(String tenantId) {
    var result = new java.util.EnumMap<ResourceType, String>(ResourceType.class);
    for (var browseType : V2_BROWSE_RESOURCE_TYPES) {
      result.put(browseType, getV2BrowseAliasName(tenantId, browseType));
    }
    return result;
  }

  public Map<ResourceType, String> getV2BrowsePhysicalIndexMap(String tenantId, int generation) {
    var result = new java.util.EnumMap<ResourceType, String>(ResourceType.class);
    for (var browseType : V2_BROWSE_RESOURCE_TYPES) {
      result.put(browseType, getV2BrowsePhysicalIndexName(tenantId, browseType, generation));
    }
    return result;
  }

  @Transactional
  @CacheEvict(cacheNames = {ACTIVE_INDEX_FAMILY_CACHE, CUTTING_OVER_INDEX_FAMILY_CACHE, PHYSICAL_INDEX_EXISTS_CACHE},
    allEntries = true, beforeInvocation = true)
  public void switchOver(UUID familyId) {
    var requestedFamily = indexFamilyRepository.findById(familyId)
      .orElseThrow(() -> new RequestValidationException("Index family not found", "familyId", familyId.toString()));

    var version = requestedFamily.getQueryVersion();
    indexFamilyRepository.lockByVersion(version);

    var newFamily = indexFamilyRepository.findById(familyId)
      .orElseThrow(() -> new RequestValidationException("Index family not found", "familyId", familyId.toString()));

    if (newFamily.getStatus() != STAGED && newFamily.getStatus() != CUTTING_OVER) {
      throw new RequestValidationException(
        "Only STAGED or CUTTING_OVER families can be switched over", "status", newFamily.getStatus().getValue());
    }

    reindexKafkaConsumerManager.captureTargetOffsets(familyId);
    var lagToTarget = reindexKafkaConsumerManager.getConsumerLagToTarget(familyId);
    recordCatchUpLagIfTracked(familyId, version, lagToTarget);
    if (lagToTarget > 0) {
      throw new RequestValidationException(
        "Temporary reindex consumer has not reached cutover offset snapshot",
        "consumerLag", String.valueOf(lagToTarget));
    }

    var tenantId = context.getTenantId();
    var aliasName = getAliasName(tenantId, version);
    var oldFamily = indexFamilyRepository.findActiveByVersion(version);
    var cutoverStarted = false;
    try {
      if (version == QueryVersion.V2) {
        completeCatchUpPhaseIfTracked(familyId);
        startCutoverPhaseIfTracked(familyId, aliasName, newFamily.getIndexName(),
          oldFamily.map(IndexFamilyEntity::getIndexName).orElse("none"));
        cutoverStarted = true;
      }

      if (newFamily.getStatus() == STAGED) {
        indexFamilyRepository.updateStatus(familyId, CUTTING_OVER);
        log.info("switchOver:: cutover started [familyId: {}, tenant: {}, version: {}, index: {}]",
          familyId, tenantId, version, newFamily.getIndexName());
      }

      log.info("switchOver:: switching alias [alias: {}, newIndex: {}, oldIndex: {}, version: {}]",
        aliasName, newFamily.getIndexName(), oldFamily.map(IndexFamilyEntity::getIndexName).orElse("none"), version);

      if (version == QueryVersion.V1 && physicalIndexExists(aliasName)) {
        atomicRemoveIndexAndAddAlias(aliasName, newFamily.getIndexName());
      } else {
        atomicAliasSwap(aliasName, newFamily.getIndexName(), oldFamily.map(IndexFamilyEntity::getIndexName));
      }

      if (version == QueryVersion.V2) {
        switchOverV2BrowseAliases(tenantId, newFamily.getGeneration(),
          oldFamily.map(IndexFamilyEntity::getGeneration));
      }

      oldFamily.ifPresent(old -> indexFamilyRepository.updateStatus(old.getId(),
        isLegacyV1Representative(old, tenantId) ? RETIRED : RETIRING));
      indexFamilyRepository.updateStatus(familyId, ACTIVE);
      reindexKafkaConsumerManager.stopReindexConsumer(familyId);

      if (version == QueryVersion.V2) {
        completeCutoverPhaseIfTracked(familyId);
        updateFamilyStatusIfTracked(familyId, ACTIVE);
      }

      log.info("switchOver:: completed [alias: {}, activeIndex: {}]", aliasName, newFamily.getIndexName());
    } catch (RuntimeException e) {
      if (cutoverStarted) {
        failCutoverPhaseIfTracked(familyId, e.getMessage());
      }
      throw e;
    }
  }

  @CacheEvict(cacheNames = {ACTIVE_INDEX_FAMILY_CACHE, CUTTING_OVER_INDEX_FAMILY_CACHE, PHYSICAL_INDEX_EXISTS_CACHE},
    allEntries = true, beforeInvocation = true)
  public void retireFamily(UUID familyId) {
    var family = indexFamilyRepository.findById(familyId)
      .orElseThrow(() -> new RequestValidationException("Index family not found", "familyId", familyId.toString()));

    if (family.getStatus() != RETIRING) {
      throw new RequestValidationException(
        "Only RETIRING families can be retired", "status", family.getStatus().getValue());
    }

    var tenantId = context.getTenantId();
    var aliasName = getAliasName(tenantId, family.getQueryVersion());
    if (!isLegacyV1Representative(family, tenantId)) {
      verifyAliasDoesNotPointTo(aliasName, family.getIndexName());
      dropPhysicalIndex(family.getIndexName());
    }

    if (family.getQueryVersion() == QueryVersion.V2) {
      dropV2BrowseIndices(tenantId, family.getGeneration());
    }

    indexFamilyRepository.updateStatus(familyId, RETIRED);

    log.info("retireFamily:: retired [index: {}]", family.getIndexName());
  }

  @Cacheable(cacheNames = ACTIVE_INDEX_FAMILY_CACHE, key = "#tenantId + ':' + #version.name()")
  public Optional<IndexFamilyEntity> findActiveFamily(String tenantId, QueryVersion version) {
    return indexFamilyRepository.findActiveByVersion(version);
  }

  public List<IndexFamilyEntity> findAllFamilies(String tenantId, boolean onlineOnly) {
    var families = indexFamilyRepository.findAll();
    if (!onlineOnly) {
      return families;
    }

    return families.stream()
      .filter(family -> family.getStatus() != RETIRING && family.getStatus() != RETIRED)
      .toList();
  }

  public Optional<IndexFamilyEntity> findById(UUID id) {
    return indexFamilyRepository.findById(id);
  }

  public List<IndexFamilyEntity> findByStatusAndVersion(IndexFamilyStatus status, QueryVersion version) {
    return indexFamilyRepository.findByStatusAndVersion(status, version);
  }

  @Cacheable(cacheNames = CUTTING_OVER_INDEX_FAMILY_CACHE, key = "#tenantId + ':' + #version.name()")
  public Optional<IndexFamilyEntity> findCuttingOverFamily(String tenantId, QueryVersion version) {
    return indexFamilyRepository.findByStatusAndVersion(CUTTING_OVER, version)
      .stream()
      .findFirst();
  }

  public void cleanupFailedFamily(UUID familyId) {
    var family = indexFamilyRepository.findById(familyId)
      .orElseThrow(() -> new RequestValidationException("Index family not found", "familyId", familyId.toString()));

    if (family.getStatus() != IndexFamilyStatus.FAILED) {
      throw new RequestValidationException(
        "Only FAILED families can be deleted", "status", family.getStatus().getValue());
    }

    deleteFamilyCompletely(family);
  }

  // Legacy V1 full reindex still writes the unsuffixed index directly.
  // Keep exactly one representative row in index_family so the newer families view can
  // represent that legacy path without forcing V1 through the family lifecycle.
  @Transactional
  @CacheEvict(cacheNames = {ACTIVE_INDEX_FAMILY_CACHE, CUTTING_OVER_INDEX_FAMILY_CACHE, PHYSICAL_INDEX_EXISTS_CACHE},
    allEntries = true, beforeInvocation = true)
  public IndexFamilyEntity prepareLegacyV1RepresentativeFamily(String tenantId) {
    var representative = upsertLegacyV1RepresentativeFamily(tenantId, BUILDING);
    log.info("prepareLegacyV1RepresentativeFamily:: ready [tenant: {}, familyId: {}]",
      tenantId, representative.getId());
    return representative;
  }

  @Transactional
  @CacheEvict(cacheNames = {ACTIVE_INDEX_FAMILY_CACHE, CUTTING_OVER_INDEX_FAMILY_CACHE, PHYSICAL_INDEX_EXISTS_CACHE},
    allEntries = true, beforeInvocation = true)
  public Optional<IndexFamilyEntity> activateLegacyV1RepresentativeFamily(String tenantId) {
    var legacyIndexName = legacyV1PhysicalIndexName(tenantId);
    if (!physicalIndexExists(legacyIndexName)) {
      log.warn("activateLegacyV1RepresentativeFamily:: skipped, legacy index is missing [tenant: {}, index: {}]",
        tenantId, legacyIndexName);
      return Optional.empty();
    }

    var representative = upsertLegacyV1RepresentativeFamily(tenantId, ACTIVE);
    log.info("activateLegacyV1RepresentativeFamily:: active [tenant: {}, familyId: {}]",
      tenantId, representative.getId());
    return Optional.of(representative);
  }

  private void deleteFamilyCompletely(IndexFamilyEntity family) {
    cleanupFamilyArtifacts(family, true);
    indexFamilyRepository.deleteById(family.getId());
    removeTrackedFamilyIfPresent(family.getId());

    log.info("deleteFamilyCompletely:: deleted [familyId: {}, index: {}, status: {}]",
      family.getId(), family.getIndexName(), family.getStatus().getValue());
  }

  private void cleanupFamilyArtifacts(IndexFamilyEntity family, boolean dropIndex) {
    var familyId = family.getId();
    reindexKafkaConsumerManager.stopReindexConsumer(familyId);
    if (dropIndex) {
      dropPhysicalIndex(family.getIndexName());
    }

    if (family.getQueryVersion() == QueryVersion.V2) {
      dropV2BrowseIndices(context.getTenantId(), family.getGeneration());
    }

    streamingReindexStatusRepository.deleteByFamilyId(familyId);
  }

  public void markFailed(UUID familyId) {
    indexFamilyRepository.updateStatus(familyId, IndexFamilyStatus.FAILED);
    log.warn("markFailed:: family marked as failed [familyId: {}]", familyId);
  }

  public void updateStatus(UUID familyId, IndexFamilyStatus status) {
    indexFamilyRepository.updateStatus(familyId, status);
  }

  @CacheEvict(cacheNames = {ACTIVE_INDEX_FAMILY_CACHE, CUTTING_OVER_INDEX_FAMILY_CACHE, PHYSICAL_INDEX_EXISTS_CACHE},
    allEntries = true)
  public void createAliasDirectly(String aliasName, String indexName, UUID familyId) {
    try {
      var aliasRequest = new IndicesAliasesRequest();
      aliasRequest.addAliasAction(IndicesAliasesRequest.AliasActions.add()
        .index(indexName).alias(aliasName));
      elasticsearchClient.indices().updateAliases(aliasRequest, RequestOptions.DEFAULT);
      indexFamilyRepository.updateStatus(familyId, ACTIVE);
      log.info("createAliasDirectly:: created alias [alias: {}, index: {}]", aliasName, indexName);
    } catch (Exception e) {
      throw new SearchServiceException("Failed to create alias: " + aliasName, e);
    }
  }

  private static JsonNode applyIndexSettings(JsonNode baseSettings, IndexSettings overrides) {
    if (overrides == null) {
      return baseSettings;
    }
    var indexNode = (ObjectNode) baseSettings.get("index");
    if (overrides.getNumberOfShards() != null) {
      indexNode.put("number_of_shards", overrides.getNumberOfShards());
    }
    if (overrides.getNumberOfReplicas() != null) {
      indexNode.put("number_of_replicas", overrides.getNumberOfReplicas());
    }
    if (overrides.getRefreshInterval() != null && overrides.getRefreshInterval() != 0) {
      indexNode.put("refresh_interval",
        overrides.getRefreshInterval() == -1 ? "-1" : overrides.getRefreshInterval() + "s");
    }
    return baseSettings;
  }

  private String buildPhysicalIndexName(String tenantId, QueryVersion version, int generation) {
    return getFolioEnvName().toLowerCase(ROOT) + "_" + version.getIndexPrefix() + "_" + tenantId + "_" + generation;
  }

  private void createPhysicalIndex(String indexName, QueryVersion version, IndexSettings indexSettings) {
    try {
      var exists = elasticsearchClient.indices()
        .exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
      if (exists) {
        log.warn("createPhysicalIndex:: index already exists [index: {}]", indexName);
        return;
      }

      var settings = applyIndexSettings(
        searchSettingsHelper.getSettingsJson(version.getResourceType()), indexSettings).toString();
      var mappings = version == QueryVersion.V2 ? buildFlatMappings() : searchMappingsHelper.getMappings(
        ResourceType.INSTANCE);

      var createRequest = new CreateIndexRequest(indexName)
        .settings(settings, JSON)
        .mapping(mappings, JSON);

      elasticsearchClient.indices().create(createRequest, RequestOptions.DEFAULT);
      log.info("createPhysicalIndex:: created [index: {}, version: {}]", indexName, version);
    } catch (Exception e) {
      log.error("createPhysicalIndex:: failed [index: {}, version: {}]", indexName, version, e);
      throw new SearchServiceException(
        "Failed to create physical index: %s — %s".formatted(indexName, e.getMessage()), e);
    }
  }

  @Cacheable(cacheNames = PHYSICAL_INDEX_EXISTS_CACHE, key = "#indexName")
  public boolean physicalIndexExists(String indexName) {
    try {
      var exists = elasticsearchClient.indices()
        .exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
      if (!exists) {
        return false;
      }
      var aliasExists = elasticsearchClient.indices()
        .existsAlias(new GetAliasesRequest(indexName), RequestOptions.DEFAULT);
      return !aliasExists;
    } catch (Exception e) {
      log.warn("physicalIndexExists:: error checking index [index: {}]", indexName, e);
      return false;
    }
  }

  private void atomicRemoveIndexAndAddAlias(String aliasName, String newIndexName) {
    try {
      var aliasRequest = new IndicesAliasesRequest();
      aliasRequest.addAliasAction(IndicesAliasesRequest.AliasActions.removeIndex()
        .index(aliasName));
      aliasRequest.addAliasAction(IndicesAliasesRequest.AliasActions.add()
        .index(newIndexName).alias(aliasName));
      elasticsearchClient.indices().updateAliases(aliasRequest, RequestOptions.DEFAULT);
      log.info("atomicRemoveIndexAndAddAlias:: removed physical index and created alias [alias: {}, newIndex: {}]",
        aliasName, newIndexName);
    } catch (Exception e) {
      throw new SearchServiceException("Failed to atomically remove index and add alias: " + aliasName, e);
    }
  }

  private void atomicAliasSwap(String aliasName, String newIndexName, Optional<String> oldIndexName) {
    try {
      var aliasRequest = new IndicesAliasesRequest();
      oldIndexName.ifPresent(oldName ->
        aliasRequest.addAliasAction(IndicesAliasesRequest.AliasActions.remove()
          .index(oldName).alias(aliasName)));
      aliasRequest.addAliasAction(IndicesAliasesRequest.AliasActions.add()
        .index(newIndexName).alias(aliasName));

      elasticsearchClient.indices().updateAliases(aliasRequest, RequestOptions.DEFAULT);
    } catch (Exception e) {
      throw new SearchServiceException("Failed to swap alias: " + aliasName, e);
    }
  }

  private String buildFlatMappings() {
    // Start with a fresh root mapping — shared fields at the top level
    var rootNode = (ObjectNode) jsonConverter.asJsonTree("{}");
    var rootProperties = rootNode.putObject("properties");

    addKeyword(rootProperties, "resourceType");
    addKeywordLowercase(rootProperties, "instanceId");
    addKeyword(rootProperties, "tenantId");
    addBoolean(rootProperties, "shared");
    rootProperties.set("join_field", jsonConverter.asJsonTree("""
      {"type":"join","relations":{"instance":["holding","item"]}}
      """));

    // Instance namespace: V1 instance mapping + browse IDs
    // getMappings() returns top-level index mapping with date_detection/numeric_detection —
    // strip those since they're invalid inside an object property, keep only "properties"
    var instanceMapping = (ObjectNode) jsonConverter.asJsonTree(
      searchMappingsHelper.getMappings(ResourceType.INSTANCE));
    var instanceProperties = instanceMapping.with("properties");
    addBrowseIdToObjectMapping(instanceProperties, "contributors");
    addBrowseIdToObjectMapping(instanceProperties, "subjects");
    addBrowseIdToObjectMapping(instanceProperties, "classifications");
    var instanceNode = jsonConverter.asJsonTree("{}").deepCopy();
    ((ObjectNode) instanceNode).set("properties", instanceProperties);
    rootProperties.set("instance", instanceNode);

    // Holding namespace
    var holdingNode = (ObjectNode) jsonConverter.asJsonTree("{}");
    var holdingProperties = holdingNode.putObject("properties");
    buildHoldingProperties(holdingProperties);
    rootProperties.set("holding", holdingNode);

    // Item namespace
    var itemNode = (ObjectNode) jsonConverter.asJsonTree("{}");
    var itemProperties = itemNode.putObject("properties");
    buildItemProperties(itemProperties);
    rootProperties.set("item", itemNode);

    return rootNode.toString();
  }

  private void buildHoldingProperties(ObjectNode properties) {
    addKeywordLowercase(properties, "id");
    addKeywordLowercase(properties, "holdingsRecordId");
    addKeywordLowercase(properties, "sourceId");
    addKeywordLowercase(properties, "holdingsSourceId");
    addKeywordLowercase(properties, "permanentLocationId");
    addKeywordLowercase(properties, "holdingsTypeId");
    addKeywordLowercase(properties, "formerIds");
    addKeywordLowercase(properties, "statisticalCodeIds");
    addKeywordLowercase(properties, "holdingsIdentifiers");
    addKeywordLowercase(properties, "holdingsFullCallNumbers");
    addKeywordLowercase(properties, "holdingsNormalizedCallNumbers");
    addKeywordLowercase(properties, "holdingsTags");
    addKeywordLowercase(properties, "holdingsHrid");
    addText(properties, "callNumberPrefix");
    addText(properties, "callNumber");
    addText(properties, "callNumberSuffix");
    addText(properties, "copyNumber");
    addText(properties, "holdingsPublicNotes");
    addBoolean(properties, "holdingsDiscoverySuppress");
    addSourceAnalyzedText(properties, "holdingsAdministrativeNotes");
    addSourceAnalyzedText(properties, "holdingsNotes");
    properties.set("holdingsElectronicAccess", jsonConverter.asJsonTree("""
      {"properties":{
        "uri":{"type":"keyword","normalizer":"keyword_lowercase"},
        "linkText":{"type":"text","analyzer":"source_analyzer"},
        "publicNote":{"type":"text","analyzer":"source_analyzer"},
        "materialsSpecification":{"type":"text","analyzer":"source_analyzer"},
        "relationshipId":{"type":"keyword","normalizer":"keyword_lowercase"}
      }}
      """));
    addTagsMapping(properties);
    addNotesMapping(properties);
    addElectronicAccessMapping(properties);
    addMetadataMapping(properties);
  }

  private void buildItemProperties(ObjectNode properties) {
    addKeywordLowercase(properties, "id");
    addKeywordLowercase(properties, "holdingsRecordId");
    addKeywordLowercase(properties, "effectiveLocationId");
    addKeywordLowercase(properties, "materialTypeId");
    addKeywordLowercase(properties, "itemLevelCallNumberTypeId");
    addKeywordLowercase(properties, "formerIds");
    addKeywordLowercase(properties, "statisticalCodeIds");
    addKeywordLowercase(properties, "itemIdentifiers");
    addKeywordLowercase(properties, "itemFullCallNumbers");
    addKeywordLowercase(properties, "itemNormalizedCallNumbers");
    addKeywordLowercase(properties, "itemTags");
    addKeywordLowercase(properties, "itemHrid");
    addText(properties, "volume");
    addText(properties, "enumeration");
    addText(properties, "chronology");
    addText(properties, "accessionNumber");
    addText(properties, "itemPublicNotes");
    addBoolean(properties, "itemDiscoverySuppress");
    addSourceAnalyzedText(properties, "itemAdministrativeNotes");
    addSourceAnalyzedText(properties, "itemNotes");
    addSourceAnalyzedText(properties, "itemCirculationNotes");
    properties.set("status", jsonConverter.asJsonTree("""
      {"properties":{"name":{"type":"keyword"}}}
      """));
    addTagsMapping(properties);
    addNotesMapping(properties);
    properties.set("circulationNotes", jsonConverter.asJsonTree("""
      {"properties":{"note":{"type":"text","analyzer":"source_analyzer"},"staffOnly":{"type":"boolean"}}}
      """));
    addElectronicAccessMapping(properties);
    properties.set("effectiveCallNumberComponents", jsonConverter.asJsonTree("""
      {"properties":{
        "prefix":{"type":"keyword","normalizer":"keyword_lowercase"},
        "callNumber":{"type":"keyword","normalizer":"keyword_lowercase"},
        "suffix":{"type":"keyword","normalizer":"keyword_lowercase"},
        "typeId":{"type":"keyword","normalizer":"keyword_lowercase"}
      }}
      """));
    addKeyword(properties, "itemCallNumberBrowseId");
    addMetadataMapping(properties);
  }

  private void addSourceAnalyzedText(ObjectNode properties, String name) {
    properties.set(name, jsonConverter.asJsonTree("{\"type\":\"text\",\"analyzer\":\"source_analyzer\"}"));
  }

  private void addElectronicAccessMapping(ObjectNode properties) {
    properties.set("electronicAccess", jsonConverter.asJsonTree("""
      {"properties":{
        "uri":{"type":"keyword","normalizer":"keyword_lowercase"},
        "linkText":{"type":"text","analyzer":"source_analyzer"},
        "publicNote":{"type":"text","analyzer":"source_analyzer"},
        "materialsSpecification":{"type":"text","analyzer":"source_analyzer"},
        "relationshipId":{"type":"keyword","normalizer":"keyword_lowercase"}
      }}
      """));
  }

  private void addTagsMapping(ObjectNode properties) {
    properties.set("tags", jsonConverter.asJsonTree("""
      {"properties":{"tagList":{"type":"keyword","normalizer":"keyword_lowercase"}}}
      """));
  }

  private void addNotesMapping(ObjectNode properties) {
    properties.set("notes", jsonConverter.asJsonTree("""
      {"properties":{"note":{"type":"text","analyzer":"source_analyzer"},"staffOnly":{"type":"boolean"}}}
      """));
  }

  private void addMetadataMapping(ObjectNode properties) {
    properties.set("metadata", jsonConverter.asJsonTree("""
      {"properties":{
        "createdDate":{"type":"date"},
        "updatedDate":{"type":"date"},
        "createdByUserId":{"type":"keyword","normalizer":"keyword_lowercase"},
        "updatedByUserId":{"type":"keyword","normalizer":"keyword_lowercase"}
      }}
      """));
  }

  private void addKeyword(ObjectNode properties, String name) {
    if (!properties.has(name)) {
      properties.set(name, jsonConverter.asJsonTree("{\"type\":\"keyword\"}"));
    }
  }

  private void addKeywordLowercase(ObjectNode properties, String name) {
    if (!properties.has(name)) {
      properties.set(name, jsonConverter.asJsonTree("""
        {"type":"keyword","normalizer":"keyword_lowercase"}
        """));
    }
  }

  private void addBoolean(ObjectNode properties, String name) {
    if (!properties.has(name)) {
      properties.set(name, jsonConverter.asJsonTree("{\"type\":\"boolean\"}"));
    }
  }

  private void addBrowseIdToObjectMapping(ObjectNode properties, String objectFieldName) {
    var objectNode = properties.get(objectFieldName);
    if (objectNode != null && objectNode.has("properties")) {
      var objectProperties = (ObjectNode) objectNode.get("properties");
      if (!objectProperties.has("browseId")) {
        objectProperties.set("browseId", jsonConverter.asJsonTree("{\"type\":\"keyword\"}"));
      }
    }
  }

  private void addText(ObjectNode properties, String name) {
    if (!properties.has(name)) {
      properties.set(name, jsonConverter.asJsonTree("""
        {"type":"text","analyzer":"source_analyzer"}
        """));
    }
  }

  private void removeAlias(String aliasName, String indexName) {
    try {
      var aliasExists = elasticsearchClient.indices()
        .existsAlias(new GetAliasesRequest(aliasName).indices(indexName), RequestOptions.DEFAULT);
      if (aliasExists) {
        var aliasRequest = new IndicesAliasesRequest();
        aliasRequest.addAliasAction(IndicesAliasesRequest.AliasActions.remove()
          .index(indexName).alias(aliasName));
        elasticsearchClient.indices().updateAliases(aliasRequest, RequestOptions.DEFAULT);
        log.info("removeAlias:: removed [alias: {}, index: {}]", aliasName, indexName);
      }
    } catch (Exception e) {
      throw new SearchServiceException("Failed to remove alias: " + aliasName, e);
    }
  }

  private void verifyAliasDoesNotPointTo(String aliasName, String indexName) {
    try {
      var aliasExists = elasticsearchClient.indices()
        .existsAlias(new GetAliasesRequest(aliasName).indices(indexName), RequestOptions.DEFAULT);
      if (aliasExists) {
        throw new RequestValidationException(
          "Cannot retire: alias still points to this index", "index", indexName);
      }
    } catch (RequestValidationException e) {
      throw e;
    } catch (Exception e) {
      throw new SearchServiceException("Failed to verify alias before retiring index: " + aliasName, e);
    }
  }

  private void createBrowsePhysicalIndex(String indexName, ResourceType browseType, IndexSettings indexSettings) {
    try {
      var exists = elasticsearchClient.indices()
        .exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
      if (exists) {
        log.warn("createBrowsePhysicalIndex:: index already exists [index: {}]", indexName);
        return;
      }

      var settings = applyIndexSettings(
        searchSettingsHelper.getSettingsJson(browseType), indexSettings).toString();
      var mappings = searchMappingsHelper.getMappings(browseType);

      var createRequest = new CreateIndexRequest(indexName)
        .settings(settings, JSON)
        .mapping(mappings, JSON);

      elasticsearchClient.indices().create(createRequest, RequestOptions.DEFAULT);
      log.info("createBrowsePhysicalIndex:: created [index: {}, browseType: {}]", indexName, browseType);
    } catch (Exception e) {
      log.error("createBrowsePhysicalIndex:: failed [index: {}, browseType: {}]", indexName, browseType, e);
      throw new SearchServiceException(
        "Failed to create V2 browse index: %s — %s".formatted(indexName, e.getMessage()), e);
    }
  }

  private void switchOverV2BrowseAliases(String tenantId, int newGeneration, Optional<Integer> oldGeneration) {
    try {
      var aliasRequest = new IndicesAliasesRequest();
      for (var browseType : V2_BROWSE_RESOURCE_TYPES) {
        var alias = getV2BrowseAliasName(tenantId, browseType);
        var newIndex = getV2BrowsePhysicalIndexName(tenantId, browseType, newGeneration);
        if (physicalIndexExists(alias)) {
          aliasRequest.addAliasAction(IndicesAliasesRequest.AliasActions.removeIndex()
            .index(alias));
        } else {
          oldGeneration.ifPresent(oldGen -> {
            var oldIndex = getV2BrowsePhysicalIndexName(tenantId, browseType, oldGen);
            if (physicalIndexExists(oldIndex)) {
              aliasRequest.addAliasAction(IndicesAliasesRequest.AliasActions.remove()
                .index(oldIndex).alias(alias));
            }
          });
        }
        aliasRequest.addAliasAction(IndicesAliasesRequest.AliasActions.add()
          .index(newIndex).alias(alias));
      }
      elasticsearchClient.indices().updateAliases(aliasRequest, RequestOptions.DEFAULT);
      log.info("switchOverV2BrowseAliases:: swapped V2 browse aliases [tenant: {}, generation: {}]",
        tenantId, newGeneration);
    } catch (Exception e) {
      throw new SearchServiceException("Failed to switch over V2 browse aliases for tenant: " + tenantId, e);
    }
  }

  private void dropV2BrowseIndices(String tenantId, int generation) {
    for (var browseType : V2_BROWSE_RESOURCE_TYPES) {
      var indexName = getV2BrowsePhysicalIndexName(tenantId, browseType, generation);
      dropPhysicalIndex(indexName);
    }
  }

  private void dropPhysicalIndex(String indexName) {
    try {
      var exists = elasticsearchClient.indices()
        .exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
      if (exists) {
        elasticsearchClient.indices()
          .delete(new org.opensearch.action.admin.indices.delete.DeleteIndexRequest(indexName),
            RequestOptions.DEFAULT);
        log.info("dropPhysicalIndex:: dropped [index: {}]", indexName);
      }
    } catch (Exception e) {
      throw new SearchServiceException("Failed to drop physical index: " + indexName, e);
    }
  }

  private IndexFamilyEntity upsertLegacyV1RepresentativeFamily(String tenantId, IndexFamilyStatus status) {
    var legacyIndexName = legacyV1PhysicalIndexName(tenantId);
    var v1Families = indexFamilyRepository.findByVersion(QueryVersion.V1);
    if (v1Families.isEmpty()) {
      var now = Timestamp.from(Instant.now());
      var activatedAt = status == ACTIVE ? now : null;
      var entity = new IndexFamilyEntity(
        UUID.randomUUID(),
        indexFamilyRepository.getNextGeneration(QueryVersion.V1),
        legacyIndexName,
        status,
        now,
        activatedAt,
        null,
        QueryVersion.V1
      );
      indexFamilyRepository.create(entity);
      return entity;
    }

    var representative = v1Families.stream()
      .filter(family -> legacyIndexName.equals(family.getIndexName()))
      .findFirst()
      .or(() -> v1Families.stream().filter(family -> family.getStatus() == ACTIVE).findFirst())
      .orElse(v1Families.getFirst());

    for (var family : v1Families) {
      if (!family.getId().equals(representative.getId())) {
        deleteFamilyCompletely(family);
      }
    }

    cleanupFamilyArtifacts(representative, !legacyIndexName.equals(representative.getIndexName()));
    indexFamilyRepository.updateRepresentation(representative.getId(), legacyIndexName, status);
    return indexFamilyRepository.findById(representative.getId()).orElse(representative);
  }

  private String legacyV1PhysicalIndexName(String tenantId) {
    return indexNameProvider.getIndexName(ResourceType.INSTANCE, tenantId);
  }

  private boolean isLegacyV1Representative(IndexFamilyEntity family, String tenantId) {
    return family.getQueryVersion() == QueryVersion.V1
      && legacyV1PhysicalIndexName(tenantId).equals(family.getIndexName());
  }

  private void recordCatchUpLagIfTracked(UUID familyId, QueryVersion version, long lag) {
    if (runtimeStatusTracker != null && version == QueryVersion.V2) {
      runtimeStatusTracker.recordCatchUpLag(familyId, lag);
      if (lag == 0L) {
        runtimeStatusTracker.markCatchUpReady(familyId);
      }
      runtimeStatusTracker.updatePhaseDetails(familyId, V2ReindexPhaseType.CATCH_UP, Map.of(
        "consumerLag", lag,
        "consumerLagToTarget", lag,
        "partitions", reindexKafkaConsumerManager.getTrackedPartitionCount(familyId)));
    }
  }

  private void completeCatchUpPhaseIfTracked(UUID familyId) {
    if (runtimeStatusTracker != null) {
      runtimeStatusTracker.completePhase(familyId, V2ReindexPhaseType.CATCH_UP);
    }
  }

  private void startCutoverPhaseIfTracked(UUID familyId, String aliasName, String newIndex, String oldIndex) {
    if (runtimeStatusTracker != null) {
      runtimeStatusTracker.startPhase(
        familyId,
        V2ReindexPhaseType.CUTOVER,
        1L,
        Map.of("alias", aliasName, "newIndex", newIndex, "oldIndex", oldIndex));
    }
  }

  private void completeCutoverPhaseIfTracked(UUID familyId) {
    if (runtimeStatusTracker != null) {
      runtimeStatusTracker.completePhase(familyId, V2ReindexPhaseType.CUTOVER);
      runtimeStatusTracker.markFamilyCompleted(familyId);
    }
  }

  private void failCutoverPhaseIfTracked(UUID familyId, String errorMessage) {
    if (runtimeStatusTracker != null) {
      runtimeStatusTracker.failPhase(familyId, V2ReindexPhaseType.CUTOVER, errorMessage);
    }
  }

  private void updateFamilyStatusIfTracked(UUID familyId, IndexFamilyStatus status) {
    if (runtimeStatusTracker != null) {
      runtimeStatusTracker.updateFamilyStatus(familyId, status);
    }
  }

  private void removeTrackedFamilyIfPresent(UUID familyId) {
    if (runtimeStatusTracker != null) {
      runtimeStatusTracker.removeFamily(familyId);
    }
  }
}
