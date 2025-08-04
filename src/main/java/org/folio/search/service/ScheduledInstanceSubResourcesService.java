package org.folio.search.service.scheduled;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.utils.SearchUtils.ID_FIELD;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.InstanceChildrenResourceService;
import org.folio.search.service.ResourceService;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.service.reindex.jdbc.InstanceChildResourceRepository;
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
  private InstanceChildrenResourceService instanceChildrenResourceService;

  public ScheduledInstanceSubResourcesService(ResourceService resourceService,
                                              TenantRepository tenantRepository,
                                              List<ReindexJdbcRepository> repositories,
                                              SubResourcesLockRepository subResourcesLockRepository,
                                              SystemUserScopedExecutionService executionService) {
    this.resourceService = resourceService;
    this.tenantRepository = tenantRepository;
    this.repositories = repositories.stream()
      .filter(repo -> repo instanceof InstanceChildResourceRepository ||
        (repo.entityType() == ReindexEntityType.INSTANCE && repo instanceof MergeRangeRepository) ||
        repo.entityType() == ReindexEntityType.ITEM)
      .collect(toMap(ReindexJdbcRepository::entityType, identity()));
    this.subResourcesLockRepository = subResourcesLockRepository;
    this.executionService = executionService;
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
          var timestamp = subResourcesLockRepository.lockSubResource(entityType, tenant);
          if (timestamp.isPresent()) {
            SubResourceResult result = null;
            try {
              result = repositories.get(entityType).fetchByTimestamp(tenant, timestamp.get());
              if (result != null && result.hasRecords()) {
                if (entityType == ReindexEntityType.INSTANCE || entityType == ReindexEntityType.ITEM) {
                  processInstanceOrItemEntities(entityType, tenant, result.records());
                } else {
                  var events = map(result.records(), entityType, tenant);
                  resourceService.indexResources(events);
                }
              }
            } catch (Exception e) {
              log.error("persistChildren::Error processing instance children", e);
            } finally {
              var lastUpdatedDate = result == null || result.lastUpdateDate() == null
                                    ? timestamp.get()
                                    : result.lastUpdateDate();
              subResourcesLockRepository.unlockSubResource(entityType, lastUpdatedDate, tenant);
            }
          }
        }
        return null;
      }));

    log.debug("persistChildren::Finished instance children processing");
  }

  private void processInstanceOrItemEntities(ReindexEntityType entityType, String tenant,
                                           List<Map<String, Object>> records) {
    if (instanceChildrenResourceService == null) {
      log.warn("processInstanceOrItemEntities::InstanceChildrenResourceService not available for processing {} entities",
               entityType);
      return;
    }

    var resourceType = entityType == ReindexEntityType.INSTANCE ? ResourceType.INSTANCE : ResourceType.ITEM;

    log.debug("processInstanceOrItemEntities::Processing {} {} entities for tenant {}",
             records.size(), entityType, tenant);

    var events = records.stream()
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


    // Hard delete entities marked as deleted
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
            .collect(Collectors.groupingBy(ResourceEvent::getTenant, Collectors.mapping(ResourceEvent::getId, Collectors.toList())))
            .forEach((groupTenant, ids) ->
              repository.deleteEntitiesForTenant(ids, groupTenant)
            );
        } else {
          // Instances use regular deletion
          var idsForDelete = deletedEntities.stream()
            .map(ResourceEvent::getId)
            .toList();
          repository.deleteEntities(idsForDelete);
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
