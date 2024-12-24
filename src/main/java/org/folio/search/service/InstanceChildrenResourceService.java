package org.folio.search.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.springframework.stereotype.Component;

/**
 * Class is responsible for handling inner instance resource which are to be indexed into separate indices.
 * For example: subject, contributor, etc.
 * */
@Log4j2
@Component
@RequiredArgsConstructor
public class InstanceChildrenResourceService {

  private final List<ChildResourceExtractor> resourceExtractors;
  private final ConsortiumTenantProvider consortiumTenantProvider;

  public void persistChildren(String tenantId, List<ResourceEvent> events) {
    var shared = consortiumTenantProvider.isCentralTenant(tenantId);
    resourceExtractors.forEach(resourceExtractor -> resourceExtractor.persistChildren(shared, events));
  }

  public void persistChildrenOnReindex(String tenantId, List<Map<String, Object>> instances) {
    var events = instances.stream()
      .map(instance -> new ResourceEvent()
        .id(instance.get("id").toString())
        .type(ResourceEventType.REINDEX)
        .tenant(tenantId)
        ._new(instance))
      .toList();
    persistChildren(tenantId, events);
  }

}
