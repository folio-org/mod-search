package org.folio.search.service.converter;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.groupingBy;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.folio.search.utils.SearchConverterUtils.getResourceEventId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.ResourceIndexingConfiguration;
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

    var eventsByTenant = resourceEvents.stream().collect(groupingBy(ResourceEvent::getTenant));
    return eventsByTenant.entrySet().stream()
      .map(this::convertForTenant)
      .flatMap(Collection::stream)
      .collect(groupingBy(SearchDocumentBody::getResource));
  }

  private List<SearchDocumentBody> convertForTenant(Entry<String, List<ResourceEvent>> entry) {
    var convert = (Supplier<List<SearchDocumentBody>>) () ->
      entry.getValue().stream()
        .flatMap(this::populateResourceEvents)
        .map(event -> event.getId() != null ? event : event.id(getResourceEventId(event)))
        .map(searchDocumentConverter::convert)
        .flatMap(Optional::stream)
        .toList();

    if (entry.getKey().equals(folioExecutionContext.getTenantId())) {
      return convert.get();
    } else {
      return consortiumTenantExecutor.execute(entry.getKey(), convert);
    }
  }

  private Stream<ResourceEvent> populateResourceEvents(ResourceEvent event) {
    var resourceName = event.getResourceName();
    return resourceDescriptionService.find(resourceName)
      .map(ResourceDescription::getIndexingConfiguration)
      .map(ResourceIndexingConfiguration::getEventPreProcessor)
      .map(eventPreProcessorBeans::get)
      .map(eventPreProcessor -> eventPreProcessor.process(event))
      .map(Collection::stream)
      .orElseGet(() -> Stream.of(event));
  }
}
