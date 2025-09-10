package org.folio.search.service;

import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.folio.search.utils.SearchConverterUtils.getResourceSource;
import static org.folio.search.utils.SearchUtils.SOURCE_CONSORTIUM_PREFIX;

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

    var shared = consortiumTenantProvider.isCentralTenant(tenantId);
    var noShadowCopiesInstanceEvents = events.stream()
      .filter(resourceEvent -> {
        if (ResourceType.INSTANCE.getName().equals(resourceEvent.getResourceName())) {
          return !startsWith(getResourceSource(resourceEvent), SOURCE_CONSORTIUM_PREFIX);
        }
        return true;
      })
      .toList();
    var eventsForResourceSharing = events.stream()
      .filter(SearchConverterUtils::isUpdateEventForResourceSharing)
      .toList();
    extractors.forEach(resourceExtractor -> resourceExtractor.persistChildren(shared, noShadowCopiesInstanceEvents));
    extractors.forEach(resourceExtractor ->
      resourceExtractor.persistChildrenForResourceSharing(shared, eventsForResourceSharing));
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
