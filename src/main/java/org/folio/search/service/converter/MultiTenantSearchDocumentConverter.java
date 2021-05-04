package org.folio.search.service.converter;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.service.TenantScopedExecutionService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MultiTenantSearchDocumentConverter {

  private final SearchDocumentConverter searchDocumentConverter;
  private final TenantScopedExecutionService executionService;

  /**
   * Converts {@link ResourceEventBody} objects to a list with {@link SearchDocumentBody} objects.
   *
   * @param resourceEvents list with {@link ResourceEventBody} objects
   * @return list with {@link SearchDocumentBody} objects as value
   */
  public List<SearchDocumentBody> convert(List<ResourceEventBody> resourceEvents) {
    return convertIndexRequestToStream(resourceEvents).collect(toList());
  }

  /**
   * Converts {@link ResourceEventBody} objects to a map where key is the resource id, value is {@link
   * SearchDocumentBody} object.
   *
   * @param resourceEvents list with {@link ResourceEventBody} objects
   * @return map with {@link SearchDocumentBody} objects as value
   */
  public Map<String, SearchDocumentBody> convertIndexEventsAsMap(List<ResourceEventBody> resourceEvents) {
    if (CollectionUtils.isEmpty(resourceEvents)) {
      return Collections.emptyMap();
    }
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

  private Stream<SearchDocumentBody> convertIndexRequestToStream(List<ResourceEventBody> resourceEvents) {
    return resourceEvents.stream()
      .collect(groupingBy(ResourceEventBody::getTenant)).entrySet().stream()
      .flatMap(this::convertForTenant);
  }

  private Stream<SearchDocumentBody> convertForTenant(Map.Entry<String, List<ResourceEventBody>> entry) {
    return executionService.executeTenantScoped(entry.getKey(),
      () -> searchDocumentConverter.convert(entry.getValue())).stream();
  }
}
