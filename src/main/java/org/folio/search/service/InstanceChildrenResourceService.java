package org.folio.search.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.search.utils.SearchConverterUtils;
import org.springframework.stereotype.Component;

/**
 * Class is responsible for handling inner instance resource which are to be indexed into separate indices.
 * For example: subject, contributor, etc.
 */
@Log4j2
@Component
public class InstanceChildrenResourceService {

  private final Map<ResourceType, List<ChildResourceExtractor>> resourceExtractors;
  private final ConsortiumTenantProvider consortiumTenantProvider;

  public InstanceChildrenResourceService(List<ChildResourceExtractor> resourceExtractors,
                                         ConsortiumTenantProvider consortiumTenantProvider) {
    this.resourceExtractors = resourceExtractors.stream()
      .collect(Collectors.groupingBy(ChildResourceExtractor::resourceType));
    this.consortiumTenantProvider = consortiumTenantProvider;
  }

  public void persistChildren(String tenantId, ResourceType resourceType, List<ResourceEvent> events) {
    var extractors = resourceExtractors.get(resourceType);
    if (extractors == null) {
      return;
    }
    var eventsByInstanceSharing = events.stream()
      .collect(Collectors.groupingBy(SearchConverterUtils::isUpdateEventForResourceSharing));
    var shared = consortiumTenantProvider.isCentralTenant(tenantId);

    extractors.forEach(resourceExtractor ->
      resourceExtractor.persistChildren(shared, eventsByInstanceSharing.get(false)));
    extractors.forEach(resourceExtractor ->
      resourceExtractor.persistChildrenForResourceSharing(shared, eventsByInstanceSharing.get(true)));
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
