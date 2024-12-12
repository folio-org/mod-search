package org.folio.search.service;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.utils.SearchUtils.ID_FIELD;

import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.service.reindex.jdbc.InstanceChildResourceRepository;
import org.folio.search.service.reindex.jdbc.SubResourceResult;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
import org.folio.search.service.reindex.jdbc.TenantRepository;
import org.folio.search.service.reindex.jdbc.UploadRangeRepository;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Log4j2
@Service
public class ScheduledInstanceSubResourcesService {

  private final TransactionTemplate transactionTemplate;
  private final ResourceService resourceService;
  private final TenantRepository tenantRepository;
  private final Map<ReindexEntityType, UploadRangeRepository> repositories;
  private final SubResourcesLockRepository subResourcesLockRepository;
  private final SystemUserScopedExecutionService executionService;

  public ScheduledInstanceSubResourcesService(TransactionTemplate transactionTemplate, ResourceService resourceService,
                                              TenantRepository tenantRepository,
                                              List<UploadRangeRepository> repositories,
                                              SubResourcesLockRepository subResourcesLockRepository,
                                              SystemUserScopedExecutionService executionService) {
    this.transactionTemplate = transactionTemplate;
    this.resourceService = resourceService;
    this.tenantRepository = tenantRepository;
    this.repositories = repositories.stream()
      .filter(uploadRangeRepository -> uploadRangeRepository instanceof InstanceChildResourceRepository)
      .collect(toMap(UploadRangeRepository::entityType, identity()));
    this.subResourcesLockRepository = subResourcesLockRepository;
    this.executionService = executionService;
  }

  @Scheduled(fixedDelayString = "#{searchConfigurationProperties.indexing.instanceChildrenIndexDelayMs}")
  public void persistChildren() {
    log.info("persistChildren::Starting instance children processing");
    tenantRepository.fetchDataTenantIds().forEach(tenant -> {
      executionService.executeSystemUserScoped(tenant, () -> {
        var entityTypes = repositories.keySet();
        for (var entityType : entityTypes) {
          var timestamp = subResourcesLockRepository.lockSubResource(entityType, tenant);
          if (timestamp.isPresent()) {
            SubResourceResult result = null;
            try {
              result = repositories.get(entityType).fetchByTimestamp(tenant, timestamp.get());
              if (result.hasRecords()) {
                var events = map(result.records(), entityType, tenant);
                resourceService.indexResources(events);
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
      });

    });

    log.debug("persistChildren::Finished instance children processing");
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
