package org.folio.search.service;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.folio.search.utils.SearchConverterUtils.getResourceSource;
import static org.folio.search.utils.SearchConverterUtils.isUpdateEventForResourceSharing;
import static org.folio.search.utils.SearchUtils.SOURCE_CONSORTIUM_PREFIX;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.event.SubResourceEvent;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.springframework.stereotype.Component;

/**
 * Class is responsible for handling inner instance resource which are to be indexed into separate indices.
 * For example: subject, contributor, etc.
 * */
@Log4j2
@Component
@RequiredArgsConstructor
public class InstanceChildrenResourceService {

  private final FolioMessageProducer<SubResourceEvent> messageProducer;
  private final List<ChildResourceExtractor> resourceExtractors;
  private final ConsortiumTenantProvider consortiumTenantProvider;

  public void sendChildrenEvent(ResourceEvent event) {
    var needChildrenEvent = false;
    if (isUpdateEventForResourceSharing(event)) {
      needChildrenEvent = resourceExtractors.stream()
        .anyMatch(extractor -> !extractor.hasChildResourceChanges(event));
    } else if (!startsWith(getResourceSource(event), SOURCE_CONSORTIUM_PREFIX)) {
      needChildrenEvent = resourceExtractors.stream()
        .anyMatch(extractor -> extractor.hasChildResourceChanges(event));
    }

    if (needChildrenEvent) {
      var childEvent = SubResourceEvent.fromResourceEvent(event);
      log.debug("sendChildrenEvent::Sending event for instance child entities processing");
      if (log.isDebugEnabled()) {
        log.debug("sendChildrenEvent::Sending event for instance child entities processing [{}]", event);
      }
      messageProducer.sendMessages(singletonList(childEvent));
    } else {
      log.debug("sendChildrenEvent::Not sending event for instance child entities processing");
      if (log.isDebugEnabled()) {
        log.debug("sendChildrenEvent::Not sending event for instance child entities processing [{}]", event);
      }
    }
  }

  public List<ResourceEvent> extractChildren(ResourceEvent event) {
    log.debug("processChildren::Starting instance children event processing");
    if (log.isDebugEnabled()) {
      log.debug("processChildren::Starting instance children event processing [{}]", event);
    }

    var events = new LinkedList<ResourceEvent>();

    if (isUpdateEventForResourceSharing(event)) {
      for (var resourceExtractor : resourceExtractors) {
        events.addAll(resourceExtractor.prepareEventsOnSharing(event));
      }
    } else if (startsWith(getResourceSource(event), SOURCE_CONSORTIUM_PREFIX)) {
      log.debug(
        "processChildren::Finished instance children event processing. No additional action for shadow instance.");
      return events;
    } else {
      for (var resourceExtractor : resourceExtractors) {
        events.addAll(resourceExtractor.prepareEvents(event));
      }
    }

    if (log.isDebugEnabled()) {
      log.debug("processChildren::Finished instance children event processing. Events after: [{}], ", events);
    }
    return events;
  }

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
