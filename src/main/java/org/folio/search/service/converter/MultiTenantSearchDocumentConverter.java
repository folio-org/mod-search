package org.folio.search.service.converter;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.integration.AuthorityEventPreProcessor;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.service.TenantScopedExecutionService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MultiTenantSearchDocumentConverter {

  private final SearchDocumentConverter searchDocumentConverter;
  private final TenantScopedExecutionService executionService;
  private final AuthorityEventPreProcessor authorityEventPreProcessor;

  /**
   * Converts {@link ResourceEvent} objects to a list with {@link SearchDocumentBody} objects.
   *
   * @param resourceEvents list with {@link ResourceEvent} objects
   * @return list with {@link SearchDocumentBody} objects as value
   */
  public List<SearchDocumentBody> convert(List<ResourceEvent> resourceEvents) {
    return convertIndexRequestToStream(resourceEvents).collect(toList());
  }

  /**
   * Converts {@link ResourceEvent} objects to a map where key is the resource id, value is {@link SearchDocumentBody}
   * object.
   *
   * @param resourceEvents list with {@link ResourceEvent} objects
   * @return map with {@link SearchDocumentBody} objects as value
   */
  public Map<String, SearchDocumentBody> convertAsMap(List<ResourceEvent> resourceEvents) {
    return convertIndexRequestToStream(resourceEvents).collect(toMap(SearchDocumentBody::getId, identity()));
  }

  /**
   * Converts {@link ResourceIdEvent} objects to a map where key is the resource id, value is {@link SearchDocumentBody}
   * object.
   *
   * @param resourceEvents list with {@link ResourceIdEvent} objects
   * @return map with {@link SearchDocumentBody} objects as value
   */
  public Map<String, SearchDocumentBody> convertDeleteEventsAsMap(List<ResourceIdEvent> resourceEvents) {
    if (CollectionUtils.isEmpty(resourceEvents)) {
      return Collections.emptyMap();
    }
    return resourceEvents.stream()
      .map(SearchDocumentBody::forResourceIdEvent)
      .collect(toMap(SearchDocumentBody::getId, identity()));
  }

  private Stream<SearchDocumentBody> convertIndexRequestToStream(List<ResourceEvent> resourceEvents) {
    if (CollectionUtils.isEmpty(resourceEvents)) {
      return Stream.empty();
    }
    var eventsByTenant = resourceEvents.stream().collect(groupingBy(ResourceEvent::getTenant));
    return eventsByTenant.entrySet().stream().map(this::convertForTenant).flatMap(Collection::stream);
  }

  private List<SearchDocumentBody> convertForTenant(Entry<String, List<ResourceEvent>> eventsPerTenant) {
    return executionService.executeTenantScoped(eventsPerTenant.getKey(), () ->
      eventsPerTenant.getValue().stream()
        .flatMap(this::populateResourceEvents)
        .map(this::convertResourceEvent)
        .flatMap(Optional::stream)
        .collect(toList()));
  }

  private Optional<SearchDocumentBody> convertResourceEvent(ResourceEvent resourceEventBody) {
    return resourceEventBody.getType() != DELETE
      ? searchDocumentConverter.convert(resourceEventBody)
      : Optional.of(SearchDocumentBody.forDeleteResourceEvent(resourceEventBody));
  }

  private Stream<ResourceEvent> populateResourceEvents(ResourceEvent event) {
    return AUTHORITY_RESOURCE.equals(event.getResourceName())
      ? authorityEventPreProcessor.process(event).stream()
      : Stream.of(event);
  }
}
