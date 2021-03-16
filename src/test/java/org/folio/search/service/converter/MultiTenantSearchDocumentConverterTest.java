package org.folio.search.service.converter;

import static org.folio.search.utils.TestUtils.eventBody;
import static org.folio.search.utils.TestUtils.randomId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.service.TenantScopedExecutionService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@UnitTest
class MultiTenantSearchDocumentConverterTest {
  @Mock private SearchDocumentConverter searchDocumentConverter;
  @Mock private TenantScopedExecutionService executionService;
  @InjectMocks private MultiTenantSearchDocumentConverter multiTenantConverter;

  @Test
  @SuppressWarnings("unchecked")
  void shouldConvertEventsPerTenant() {
    when(executionService.executeTenantScoped(anyString(), any()))
      .thenAnswer(invocation -> ((Callable<List<SearchDocumentBody>>) invocation.getArgument(1)).call());

    var eventsForTenantOne = eventsForTenant("tenant_one");
    var eventsForTenantTwo = eventsForTenant("tenant_two");

    var allEvents = new ArrayList<>(eventsForTenantOne);
    allEvents.addAll(eventsForTenantTwo);

    multiTenantConverter.convert(allEvents);

    verify(searchDocumentConverter, times(1)).convert(eventsForTenantOne);
    verify(searchDocumentConverter, times(1)).convert(eventsForTenantTwo);
  }

  private List<ResourceEventBody> eventsForTenant(String tenant) {
    return List.of(eventBody("instance", Map.of("id", randomId())).tenant(tenant),
      eventBody("instance", Map.of("id", randomId())).tenant(tenant));
  }
}
