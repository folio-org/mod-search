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
import org.folio.search.service.InstanceChildrenResourceService;
import org.folio.search.service.ResourceService;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.service.reindex.jdbc.InstanceChildResourceRepository;
import org.folio.search.service.reindex.jdbc.ItemRepository;
import org.folio.search.service.reindex.jdbc.MergeInstanceRepository;
import org.folio.search.service.reindex.jdbc.MergeRangeRepository;
import org.folio.search.service.reindex.jdbc.ReindexJdbcRepository;
import org.folio.search.service.reindex.jdbc.SubResourceResult;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
import org.folio.search.service.reindex.jdbc.TenantRepository;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
  private final SystemUserScopedExecutionService executionService;
  private final int subResourceBatchSize;
  private final long staleLockThresholdMs;
  private InstanceChildrenResourceService instanceChildrenResourceService;

  public ScheduledInstanceSubResourcesService(ResourceService resourceService,
                                              TenantRepository tenantRepository,
                                              List<ReindexJdbcRepository> repositories,
                                              SubResourcesLockRepository subResourcesLockRepository,
                                              SystemUserScopedExecutionService executionService,
                                              MergeInstanceRepository instanceRepository,
                                              ItemRepository itemRepository,
                                              SearchConfigurationProperties searchConfigurationProperties) {
    this.resourceService = resourceService;
    this.tenantRepository = tenantRepository;
    this.repositories = new LinkedHashMap<>();
    this.repositories.put(ReindexEntityType.INSTANCE, instanceRepository);
    this.repositories.put(ReindexEntityType.ITEM, itemRepository);
    this.repositories.putAll(repositories.stream()
      .filter(InstanceChildResourceRepository.class::isInstance)
      .collect(toMap(ReindexJdbcRepository::entityType, identity())));
    this.subResourcesLockRepository = subResourcesLockRepository;
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
    tenantRepository.fetchDataTenantIds()
      .forEach(tenant -> executionService.executeSystemUserScoped(tenant, () -> {
        var entityTypes = repositories.keySet();
        for (var entityType : entityTypes) {
          subResourcesLockRepository.lockSubResource(entityType, tenant)
            .ifPresentOrElse(
              // Lock acquired successfully - process sub-resources
              timestamp -> processSubResources(entityType, tenant, timestamp),
              // Lock acquisition failed - check if it's because of a stale lock and release it
              () -> {
                if (subResourcesLockRepository.checkAndReleaseStaleLock(entityType, tenant, staleLockThresholdMs)) {
                  log.warn("persistChildren::Released stale lock for entity type {} in tenant {}. "
                      + "Lock was older than threshold of {} ms",
                    entityType, tenant, staleLockThresholdMs);
                }
                // Don't process sub-resources on the same call as stale lock release
              }
            );
        }
        return null;
      }));

    log.debug("persistChildren::Finished instance children processing");
  }

  private void processSubResources(ReindexEntityType entityType, String tenant, Timestamp timestamp) {
    SubResourceResult result = null;
    String lastId = null;
    Timestamp lastTimestamp = timestamp;

    try {
      do {
        result = fetchSubResourceResult(entityType, tenant, timestamp, lastId, lastTimestamp);

        if (result == null || !result.hasRecords()) {
          break;
        }

        if (entityType == ReindexEntityType.INSTANCE || entityType == ReindexEntityType.ITEM) {
          processInstanceOrItemEntities(entityType, tenant, result);
        } else {
          var events = map(result.records(), entityType, tenant);
          resourceService.indexResources(events);
        }

        if (result.records().size() == subResourceBatchSize) {
          // Update lock timestamp after successful batch processing to keep lock fresh
          var currentTimestamp = result.lastUpdateDate() != null ? result.lastUpdateDate() : lastTimestamp;
          subResourcesLockRepository.updateLockTimestamp(entityType, currentTimestamp, tenant);

          var lastRecord = result.records().getLast();
          lastId = getString(lastRecord, ID_FIELD);
          lastTimestamp = result.lastUpdateDate();
        } else {
          break;
        }
      } while (result.hasRecords());
    } catch (Exception e) {
      log.error("processSubResources::Error processing {} entities", entityType, e);
    } finally {
      var lastUpdatedDate = getLastUpdatedDate(timestamp, result);
      subResourcesLockRepository.unlockSubResource(entityType, lastUpdatedDate, tenant);
    }
  }

  private Timestamp getLastUpdatedDate(Timestamp timestamp, SubResourceResult result) {
    return result == null || result.lastUpdateDate() == null
      ? timestamp
      : result.lastUpdateDate();
  }

  private SubResourceResult fetchSubResourceResult(ReindexEntityType entityType, String tenant, Timestamp timestamp,
                                                   String lastId, Timestamp lastTimestamp) {
    return lastId == null
      ? repositories.get(entityType).fetchByTimestamp(tenant, timestamp, subResourceBatchSize)
      : repositories.get(entityType).fetchByTimestamp(tenant, lastTimestamp, lastId, subResourceBatchSize);
  }

  private void processInstanceOrItemEntities(ReindexEntityType entityType, String tenant,
                                           SubResourceResult result) {
    if (result == null || !result.hasRecords()) {
      return;
    }

    if (instanceChildrenResourceService == null) {
      log.warn("processInstanceOrItemEntities::InstanceChildrenResourceService not available for processing {}.",
               entityType);
      return;
    }

    var resourceType = entityType == ReindexEntityType.INSTANCE ? ResourceType.INSTANCE : ResourceType.ITEM;

    log.debug("processInstanceOrItemEntities::Processing {} {} entities for tenant {}",
             result.records().size(), entityType, tenant);

    var events = result.records().stream()
      .map(recordMap -> {
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
      })
      .toList();
    events.stream()
      .collect(Collectors.groupingBy(ResourceEvent::getTenant)).forEach((eventsTenant, tenantEvents) ->
        instanceChildrenResourceService.persistChildren(eventsTenant, resourceType, tenantEvents));

    var deletedEntities = events.stream()
      .filter(event -> ResourceEventType.DELETE == event.getType())
      .toList();

    if (!deletedEntities.isEmpty()) {
      var repository = (MergeRangeRepository) repositories.get(entityType);
      if (repository != null) {
        log.debug("processInstanceOrItemEntities::Hard deleting {} {} entities with IDs: {}",
          deletedEntities.size(), entityType, deletedEntities);

        if (entityType == ReindexEntityType.ITEM) {
          // Items need tenant-specific deletion
          deletedEntities.stream()
            .collect(Collectors.groupingBy(ResourceEvent::getTenant,
              Collectors.mapping(ResourceEvent::getId, Collectors.toList())))
            .forEach((groupTenant, ids) ->
              repository.deleteEntitiesForTenant(ids, groupTenant, true)
            );
        } else {
          // Instances use regular deletion
          var idsForDelete = deletedEntities.stream()
            .map(ResourceEvent::getId)
            .toList();
          repository.deleteEntities(idsForDelete, true);
        }
      }
    }
  }

  public List<ResourceEvent> map(List<Map<String, Object>> recordMaps, ReindexEntityType entityType, String tenant) {
    return recordMaps.stream()
      .map(map -> {
        var instancesEmpty = map.get("instances") == null;
        return new ResourceEvent().id(getString(map, ID_FIELD))
          .type(instancesEmpty ? ResourceEventType.DELETE : ResourceEventType.CREATE)
          .resourceName(ReindexConstants.RESOURCE_NAME_MAP.get(entityType).getName())
          ._new(instancesEmpty ? null : map)
          .tenant(tenant);
      })
      .toList();
  }
}
