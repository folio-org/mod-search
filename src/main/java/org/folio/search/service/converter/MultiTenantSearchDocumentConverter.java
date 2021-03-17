package org.folio.search.service.converter;

import static java.util.stream.Collectors.groupingBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.service.TenantScopedExecutionService;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class MultiTenantSearchDocumentConverter {
  private final SearchDocumentConverter searchDocumentConverter;
  private final TenantScopedExecutionService executionService;

  public List<SearchDocumentBody> convert(List<ResourceEventBody> resourceEvents) {
    return resourceEvents.stream()
      .collect(groupingBy(ResourceEventBody::getTenant)).entrySet().stream()
      .flatMap(this::convertForTenant)
      .collect(Collectors.toList());
  }

  private Stream<SearchDocumentBody> convertForTenant(Map.Entry<String, List<ResourceEventBody>> entry) {
    return executionService.executeTenantScoped(entry.getKey(),
      () -> searchDocumentConverter.convert(entry.getValue())).stream();
  }
}
