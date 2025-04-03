package org.folio.search.service.converter;

import static java.util.Collections.emptyMap;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.folio.search.utils.SearchConverterUtils.getResourceEventId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.ResourceIndexingConfiguration;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.converter.preprocessor.EventPreProcessor;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class MultiTenantSearchDocumentConverter {

  private final SearchDocumentConverter searchDocumentConverter;
  private final ResourceDescriptionService resourceDescriptionService;
  private final Map<String, EventPreProcessor> eventPreProcessorBeans;
  private final ConsortiumTenantExecutor consortiumTenantExecutor;
  private final FolioExecutionContext folioExecutionContext;

  /**
   * Converts {@link ResourceEvent} objects to a list with {@link SearchDocumentBody} objects.
   *
   * @param resourceEvents list with {@link ResourceEvent} objects
   * @return map where key is the resource name and value is the {@link List} with {@link SearchDocumentBody} objects
   */
  public Map<String, List<SearchDocumentBody>> convert(Collection<ResourceEvent> resourceEvents) {
    log.debug("convert:: by [resourceEvents.size: {}]", collectionToLogMsg(resourceEvents, true));

    if (CollectionUtils.isEmpty(resourceEvents)) {
      return emptyMap();
    }

    Map<String, Map<String, List<ResourceEvent>>> resourcesByTenantAndType = new HashMap<>();
    // Pre-allocate with expected capacity
    Map<String, List<SearchDocumentBody>> result = new HashMap<>(resourceEvents.size());

    // Process events in a single pass, grouping by tenant
    for (ResourceEvent event : resourceEvents) {
      resourcesByTenantAndType
        .computeIfAbsent(event.getTenant(), k -> new HashMap<>())
        .computeIfAbsent(event.getResourceName(), k -> new ArrayList<>())
        .add(event);
    }

    // Process each tenant group
    for (Entry<String, Map<String, List<ResourceEvent>>> tenantEntry : resourcesByTenantAndType.entrySet()) {
      String tenantId = tenantEntry.getKey();
      Map<String, List<ResourceEvent>> eventsByType = tenantEntry.getValue();

      // Convert the events for this tenant (either directly or via executor)
      List<SearchDocumentBody> convertedDocs;
      if (tenantId.equals(folioExecutionContext.getTenantId())) {
        convertedDocs = convertEventsForTenant(eventsByType);
      } else {
        convertedDocs = consortiumTenantExecutor.execute(tenantId,
          () -> convertEventsForTenant(eventsByType));
      }

      // Group by resource and merge into result
      for (SearchDocumentBody doc : convertedDocs) {
        result.computeIfAbsent(doc.getResource(), k -> new ArrayList<>()).add(doc);
      }
    }

    return result;
  }

  // Helper method to convert events for a specific tenant
  private List<SearchDocumentBody> convertEventsForTenant(Map<String, List<ResourceEvent>> eventsByType) {
    List<SearchDocumentBody> results = new ArrayList<>();

    for (Entry<String, List<ResourceEvent>> entry : eventsByType.entrySet()) {
      String resourceName = entry.getKey();
      List<ResourceEvent> events = entry.getValue();
      ResourceType resourceType = ResourceType.byName(resourceName);

      // Get the event pre-processor once for all events of this type
      Optional<EventPreProcessor> preProcessor = resourceDescriptionService.find(resourceType)
        .map(ResourceDescription::getIndexingConfiguration)
        .map(ResourceIndexingConfiguration::getEventPreProcessor)
        .map(eventPreProcessorBeans::get);

      for (ResourceEvent event : events) {
        // Pre-process the event if needed
        Collection<ResourceEvent> processedEvents;
        if (preProcessor.isPresent()) {
          processedEvents = preProcessor.get().preProcess(event);
        } else {
          processedEvents = Collections.singletonList(event);
        }

        // Convert each processed event
        for (ResourceEvent processedEvent : processedEvents) {
          // Ensure ID is present
          if (processedEvent.getId() == null) {
            processedEvent.id(getResourceEventId(processedEvent));
          }

          // Convert to document body
          searchDocumentConverter.convert(processedEvent)
            .ifPresent(results::add);
        }
      }
    }

    return results;
  }

  private Stream<ResourceEvent> populateResourceEvents(ResourceEvent event) {
    var resourceName = ResourceType.byName(event.getResourceName());
    return resourceDescriptionService.find(resourceName)
      .map(ResourceDescription::getIndexingConfiguration)
      .map(ResourceIndexingConfiguration::getEventPreProcessor)
      .map(eventPreProcessorBeans::get)
      .map(eventPreProcessor -> eventPreProcessor.preProcess(event))
      .map(Collection::stream)
      .orElseGet(() -> Stream.of(event));
  }
}
