package org.folio.search.service.scheduled;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.utils.SearchUtils.ID_FIELD;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.EgressExecutionContextService;
import org.folio.search.service.InstanceChildrenResourceService;
import org.folio.search.service.ResourceService;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.service.reindex.ReindexStatusService;
import org.folio.search.service.reindex.jdbc.InstanceChildResourceRepository;
import org.folio.search.service.reindex.jdbc.ItemRepository;
import org.folio.search.service.reindex.jdbc.MergeInstanceRepository;
import org.folio.search.service.reindex.jdbc.MergeRangeRepository;
import org.folio.search.service.reindex.jdbc.ReindexJdbcRepository;
import org.folio.search.service.reindex.jdbc.SubResourceResult;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
import org.folio.search.service.reindex.jdbc.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@ConditionalOnProperty(name = "folio.search-config.indexing.instance-children-index-enabled", havingValue = "true")
public class ScheduledInstanceSubResourcesService {

  private final ResourceService resourceService;
  private final TenantRepository tenantRepository;
  private final Map<ReindexEntityType, ReindexJdbcRepository> repositories;
  private final SubResourcesLockRepository subResourcesLockRepository;
  private final ReindexStatusService reindexStatusService;
  private final EgressExecutionContextService executionService;
  private final int subResourceBatchSize;
  private final long staleLockThresholdMs;
  private InstanceChildrenResourceService instanceChildrenResourceService;

  public ScheduledInstanceSubResourcesService(ResourceService resourceService,
                                              TenantRepository tenantRepository,
                                              List<ReindexJdbcRepository> repositories,
                                              SubResourcesLockRepository subResourcesLockRepository,
                                              ReindexStatusService reindexStatusService,
                                              EgressExecutionContextService executionService,
                                              MergeInstanceRepository instanceRepository,
                                              ItemRepository itemRepository,
                                              SearchConfigurationProperties searchConfigurationProperties) {
    this.resourceService = resourceService;
    this.tenantRepository = tenantRepository;
    this.repositories = buildRepositoriesMap(repositories, instanceRepository, itemRepository);
    this.subResourcesLockRepository = subResourcesLockRepository;
    this.reindexStatusService = reindexStatusService;
    this.executionService = executionService;
    this.subResourceBatchSize = searchConfigurationProperties.getIndexing().getSubResourceBatchSize();
    this.staleLockThresholdMs = searchConfigurationProperties.getIndexing().getStaleLockThresholdMs();
    this.instanceChildrenResourceService = null;
  }

  @Autowired(required = false)
  public void setInstanceChildrenResourceService(InstanceChildrenResourceService instanceChildrenResourceService) {
    this.instanceChildrenResourceService = instanceChildrenResourceService;
  }

  @Scheduled(fixedDelayString = "#{searchConfigurationProperties.indexing.instanceChildrenIndexDelayMs}")
  public void persistChildren() {
    log.info("persistChildren::Starting instance children processing");
    try {
      tenantRepository.fetchDataTenantIds()
        .forEach(tenant -> executionService.execute(tenant, () -> {
          processAllEntityTypes(tenant);
          return null;
        }));

      log.debug("persistChildren::Finished instance children processing");
    } catch (BadSqlGrammarException e) {
      log.warn("persistChildren::Module upgrade may be in progress - database schema is outdated. Skipping cycle.");
    }
  }

  public List<ResourceEvent> mapToResourceEvents(List<Map<String, Object>> recordMaps,
                                                 ReindexEntityType entityType, String tenant) {
    return recordMaps.stream()
      .map(map -> createResourceEventFromMap(map, entityType, tenant))
      .toList();
  }

  private Map<ReindexEntityType, ReindexJdbcRepository> buildRepositoriesMap(
    List<ReindexJdbcRepository> repositories,
    MergeInstanceRepository instanceRepository,
    ItemRepository itemRepository) {
    var repositoriesMap = new LinkedHashMap<ReindexEntityType, ReindexJdbcRepository>();
    repositoriesMap.put(ReindexEntityType.INSTANCE, instanceRepository);
    repositoriesMap.put(ReindexEntityType.ITEM, itemRepository);
    repositoriesMap.putAll(repositories.stream()
      .filter(InstanceChildResourceRepository.class::isInstance)
      .collect(toMap(ReindexJdbcRepository::entityType, identity())));
    return repositoriesMap;
  }

  private void processAllEntityTypes(String tenant) {
    repositories.keySet().forEach(entityType -> processEntityTypeWithLock(entityType, tenant));
  }

  private void processEntityTypeWithLock(ReindexEntityType entityType, String tenant) {
    subResourcesLockRepository.lockSubResource(entityType, tenant)
      .ifPresentOrElse(
        timestamp -> processSubResources(entityType, tenant, timestamp),
        () -> handleLockAcquisitionFailure(entityType, tenant)
      );
  }

  private void handleLockAcquisitionFailure(ReindexEntityType entityType, String tenant) {
    if (isReindexInProgressOrFailedNotForConsortiumMember()) {
      log.info(
        "persistChildren::Skipping stale lock check for entity type {} in tenant {} - reindex is in progress or failed",
        entityType, tenant);
      return;
    }

    if (subResourcesLockRepository.checkAndReleaseStaleLock(entityType, tenant, staleLockThresholdMs)) {
      log.warn("persistChildren::Released stale lock for entity type {} in tenant {}. "
               + "Lock was older than threshold of {} ms",
        entityType, tenant, staleLockThresholdMs);
    }
  }

  private boolean isReindexInProgressOrFailedNotForConsortiumMember() {
    try {
      return reindexStatusService.isReindexInProgressOrFailedNotForConsortiumMember();
    } catch (BadSqlGrammarException e) {
      throw e;
    } catch (Exception e) {
      log.warn("persistChildren::Failed to check reindex status, assuming no reindex in progress or failed", e);
      return false;
    }
  }

  private void processSubResources(ReindexEntityType entityType, String tenant, Timestamp timestamp) {
    var loopResult = new ProcessingLoopResult();
    try {
      runProcessingLoop(entityType, tenant, timestamp, loopResult);
    } catch (Exception e) {
      log.error("processSubResources::Error processing {} entities", entityType, e);
    } finally {
      if (!loopResult.preempted) {
        releaseProcessingLock(entityType, tenant, timestamp, loopResult.lastResult, loopResult.fencingToken);
      }
    }
  }

  private void runProcessingLoop(ReindexEntityType entityType, String tenant, Timestamp timestamp,
                                 ProcessingLoopResult loopResult) {
    String lastId = null;
    Timestamp lastTimestamp = timestamp;
    loopResult.fencingToken = timestamp;

    do {
      loopResult.lastResult = fetchSubResourceBatch(entityType, tenant, timestamp, lastId, lastTimestamp);
      if (isEmptyResult(loopResult.lastResult)) {
        return;
      }
      processBatch(entityType, tenant, loopResult.lastResult);
      if (!hasMoreBatches(loopResult.lastResult)
          || !refreshFencingToken(entityType, tenant, lastTimestamp, loopResult)) {
        return;
      }
      lastId = extractLastRecordId(loopResult.lastResult);
      lastTimestamp = loopResult.lastResult.lastUpdateDate();
    } while (loopResult.lastResult.hasRecords());
  }

  private boolean refreshFencingToken(ReindexEntityType entityType, String tenant, Timestamp lastTimestamp,
                                      ProcessingLoopResult loopResult) {
    var newToken = updateLockForNextBatch(entityType, tenant, loopResult.lastResult, lastTimestamp,
      loopResult.fencingToken);
    if (newToken == null) {
      log.warn("processSubResources::Lock for entity type {} in tenant {} was preempted "
               + "(reindex started). Aborting current cycle.", entityType, tenant);
      loopResult.preempted = true;
      return false;
    }
    loopResult.fencingToken = newToken;
    return true;
  }

  private boolean isEmptyResult(SubResourceResult result) {
    return result == null || !result.hasRecords();
  }

  private void processBatch(ReindexEntityType entityType, String tenant, SubResourceResult result) {
    if (isInstanceOrItemEntity(entityType)) {
      processInstanceOrItemEntities(entityType, tenant, result);
    } else {
      processChildResourceEntities(entityType, tenant, result);
    }
  }

  private boolean isInstanceOrItemEntity(ReindexEntityType entityType) {
    return entityType == ReindexEntityType.INSTANCE || entityType == ReindexEntityType.ITEM;
  }

  private void processChildResourceEntities(ReindexEntityType entityType, String tenant, SubResourceResult result) {
    var events = mapToResourceEvents(result.records(), entityType, tenant);
    resourceService.indexResources(events);
  }

  private boolean hasMoreBatches(SubResourceResult result) {
    return result.records().size() == subResourceBatchSize;
  }

  private Timestamp updateLockForNextBatch(ReindexEntityType entityType, String tenant,
                                           SubResourceResult result, Timestamp lastTimestamp,
                                           Timestamp fencingToken) {
    var currentTimestamp = result.lastUpdateDate() != null ? result.lastUpdateDate() : lastTimestamp;
    var refreshed = subResourcesLockRepository.updateLockTimestampFenced(entityType, currentTimestamp, tenant,
      fencingToken);
    return refreshed ? currentTimestamp : null;
  }

  private String extractLastRecordId(SubResourceResult result) {
    var lastRecord = result.records().getLast();
    return getString(lastRecord, ID_FIELD);
  }

  private void releaseProcessingLock(ReindexEntityType entityType, String tenant,
                                     Timestamp timestamp, SubResourceResult result, Timestamp fencingToken) {
    var lastUpdatedDate = determineLastUpdatedDate(timestamp, result);
    var released = subResourcesLockRepository.unlockSubResourceFenced(entityType, lastUpdatedDate, tenant,
      fencingToken);
    if (!released) {
      log.warn("releaseProcessingLock::Lock for entity type {} in tenant {} was preempted "
               + "(reindex started). Skipping unlock.", entityType, tenant);
    }
  }

  private Timestamp determineLastUpdatedDate(Timestamp timestamp, SubResourceResult result) {
    return result == null || result.lastUpdateDate() == null
           ? timestamp
           : result.lastUpdateDate();
  }

  private SubResourceResult fetchSubResourceBatch(ReindexEntityType entityType, String tenant,
                                                  Timestamp timestamp, String lastId, Timestamp lastTimestamp) {
    return lastId == null
           ? repositories.get(entityType).fetchByTimestamp(tenant, timestamp, subResourceBatchSize)
           : repositories.get(entityType).fetchByTimestamp(tenant, lastTimestamp, lastId, subResourceBatchSize);
  }

  private void processInstanceOrItemEntities(ReindexEntityType entityType, String tenant,
                                             SubResourceResult result) {
    if (isEmptyResult(result)) {
      return;
    }

    if (instanceChildrenResourceService == null) {
      log.warn("processInstanceOrItemEntities::InstanceChildrenResourceService not available for processing {}.",
        entityType);
      return;
    }

    var resourceType = determineResourceType(entityType);
    log.debug("processInstanceOrItemEntities::Processing {} {} entities for tenant {}",
      result.records().size(), entityType, tenant);

    var events = convertToResourceEvents(result.records(), resourceType);
    persistChildrenByTenant(events, resourceType);
    deleteMarkedEntities(entityType, events);
  }

  private ResourceType determineResourceType(ReindexEntityType entityType) {
    return entityType == ReindexEntityType.INSTANCE ? ResourceType.INSTANCE : ResourceType.ITEM;
  }

  private List<ResourceEvent> convertToResourceEvents(List<Map<String, Object>> records, ResourceType resourceType) {
    return records.stream()
      .map(recordMap -> buildResourceEvent(recordMap, resourceType))
      .toList();
  }

  private ResourceEvent buildResourceEvent(Map<String, Object> recordMap, ResourceType resourceType) {
    var isDeleted = Boolean.TRUE.equals(recordMap.get("isDeleted"));
    var eventType = isDeleted ? ResourceEventType.DELETE : ResourceEventType.UPDATE;

    var resourceEvent = new ResourceEvent()
      .id(recordMap.get("id").toString())
      .type(eventType)
      .resourceName(resourceType.getName())
      .tenant(recordMap.get("tenantId").toString());

    if (!isDeleted) {
      resourceEvent._new(recordMap);
    }

    return resourceEvent;
  }

  private void persistChildrenByTenant(List<ResourceEvent> events, ResourceType resourceType) {
    events.stream()
      .collect(Collectors.groupingBy(ResourceEvent::getTenant))
      .forEach((tenant, tenantEvents) ->
        instanceChildrenResourceService.persistChildren(tenant, resourceType, tenantEvents));
  }

  private void deleteMarkedEntities(ReindexEntityType entityType, List<ResourceEvent> events) {
    var deletedEntities = extractDeletedEvents(events);

    if (deletedEntities.isEmpty()) {
      return;
    }

    var repository = (MergeRangeRepository) repositories.get(entityType);
    if (repository != null) {
      log.debug("processInstanceOrItemEntities::Hard deleting {} {} entities with IDs: {}",
        deletedEntities.size(), entityType, deletedEntities);

      if (entityType == ReindexEntityType.ITEM) {
        deleteItemsByTenant(repository, deletedEntities);
      } else {
        deleteInstances(repository, deletedEntities);
      }
    }
  }

  private List<ResourceEvent> extractDeletedEvents(List<ResourceEvent> events) {
    return events.stream()
      .filter(event -> ResourceEventType.DELETE == event.getType())
      .toList();
  }

  private void deleteItemsByTenant(MergeRangeRepository repository, List<ResourceEvent> deletedEntities) {
    deletedEntities.stream()
      .collect(Collectors.groupingBy(ResourceEvent::getTenant,
        Collectors.mapping(ResourceEvent::getId, Collectors.toList())))
      .forEach((tenant, ids) -> repository.deleteEntitiesForTenant(ids, tenant, true));
  }

  private void deleteInstances(MergeRangeRepository repository, List<ResourceEvent> deletedEntities) {
    var idsForDelete = deletedEntities.stream()
      .map(ResourceEvent::getId)
      .toList();
    repository.deleteEntities(idsForDelete, true);
  }

  private ResourceEvent createResourceEventFromMap(Map<String, Object> map,
                                                   ReindexEntityType entityType, String tenant) {
    var instancesEmpty = map.get("instances") == null;
    return new ResourceEvent()
      .id(getString(map, ID_FIELD))
      .type(instancesEmpty ? ResourceEventType.DELETE : ResourceEventType.CREATE)
      .resourceName(ReindexConstants.RESOURCE_NAME_MAP.get(entityType).getName())
      ._new(instancesEmpty ? null : map)
      .tenant(tenant);
  }

  private static final class ProcessingLoopResult {
    private SubResourceResult lastResult;
    /**
     * The last {@code last_updated_date} value the scheduler believes is stored in the row.
     * Used by scheduler-side lock writes to detect preemption by a reindex
     * ({@code forceLockAllForReindex} bumps the timestamp so this token goes stale).
     */
    private Timestamp fencingToken;
    private boolean preempted;
  }
}
