package org.folio.search.service;

import static org.folio.search.utils.SearchConverterUtils.getMapValueByPath;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.search.service.reindex.jdbc.CallNumberRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Class is responsible for handling inner instance resource which are to be indexed into separate indices.
 * For example: subject, contributor, etc.
 */
@Log4j2
@Component
@ConditionalOnProperty(name = "folio.search-config.indexing.instance-children-index-enabled", havingValue = "true")
public class InstanceChildrenResourceService {

  private final Map<ResourceType, List<ChildResourceExtractor>> resourceExtractors;
  private final ConsortiumTenantProvider consortiumTenantProvider;
  private final CallNumberRepository callNumberRepository;

  public InstanceChildrenResourceService(List<ChildResourceExtractor> resourceExtractors,
                                         ConsortiumTenantProvider consortiumTenantProvider,
                                         CallNumberRepository callNumberRepository) {
    this.resourceExtractors = resourceExtractors.stream()
      .collect(Collectors.groupingBy(ChildResourceExtractor::resourceType));
    this.consortiumTenantProvider = consortiumTenantProvider;
    this.callNumberRepository = callNumberRepository;
  }

  public void persistChildren(String tenantId, ResourceType resourceType, List<ResourceEvent> events) {
    var extractors = resourceExtractors.get(resourceType);
    if (extractors == null) {
      return;
    }

    var shared = consortiumTenantProvider.isCentralTenant(tenantId);

    // Process child resources normally
    extractors.forEach(resourceExtractor ->
      resourceExtractor.persistChildren(tenantId, shared, events));

    // When background job processes new instances in central tenant, update call numbers
    // that may still be pointing to member tenant. Covers sharing instance case.
    if (shared && resourceType == ResourceType.INSTANCE && !events.isEmpty()) {
      var instanceIds = events.stream()
        .filter(this::isNewInstance)
        .map(ResourceEvent::getId)
        .toList();
      log.info("persistChildren: Updating call number tenant_id for {} instances in central tenant {}",
        instanceIds.size(), tenantId);
      callNumberRepository.updateTenantIdForCentralInstances(instanceIds, tenantId);
    }
  }

  /**
   * Checks if the instance is newly created by comparing metadata dates.
   * An instance is considered new if its createdDate equals its updatedDate.
   *
   * @param event the resource event to check
   * @return true if the instance is newly created, false otherwise
   */
  private Boolean isNewInstance(ResourceEvent event) {
    var instanceData = getNewAsMap(event);
    if (instanceData.isEmpty()) {
      return false;
    }

    var createdDate = getMapValueByPath("metadata.createdDate", instanceData);
    var updatedDate = getMapValueByPath("metadata.updatedDate", instanceData);

    return Objects.equals(createdDate, updatedDate);
  }

  public void persistChildrenOnReindex(String tenantId, ResourceType resourceType,
                                       List<Map<String, Object>> instances) {
    var events = instances.stream()
      .map(instance -> new ResourceEvent()
        .id(instance.get("id").toString())
        .type(ResourceEventType.REINDEX)
        .resourceName(resourceType.getName())
        .tenant(tenantId)
        ._new(instance))
      .toList();
    persistChildren(tenantId, resourceType, events);
  }
}
