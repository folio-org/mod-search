package org.folio.search.service.converter;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.eventBody;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.searchDocumentBody;
import static org.folio.search.utils.TestUtils.searchDocumentBodyForDelete;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.service.TenantScopedExecutionService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
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

  @Test
  void convertIndexEventsAsMap_positive_indexEvents() {
    var events = List.of(eventBody("instance", Map.of("id", RESOURCE_ID)));
    var expectedBody = searchDocumentBody();

    when(searchDocumentConverter.convert(events)).thenReturn(List.of(expectedBody));
    when(executionService.executeTenantScoped(eq(TENANT_ID), any())).thenAnswer(invocation ->
      invocation.<Callable<List<SearchDocumentBody>>>getArgument(1).call());

    var actual = multiTenantConverter.convertIndexEventsAsMap(events);
    assertThat(actual).isEqualTo(mapOf(RESOURCE_ID, expectedBody));
  }

  @Test
  void convertIndexEventsAsMap_positive_null() {
    var actual = multiTenantConverter.convertIndexEventsAsMap(null);
    assertThat(actual).isEqualTo(emptyMap());
  }

  @Test
  void convertIndexEventsAsMap_positive_emptyList() {
    var actual = multiTenantConverter.convertIndexEventsAsMap(emptyList());
    assertThat(actual).isEqualTo(emptyMap());
  }

  @Test
  void convertDeleteEventsAsMap_positive_listOfResourceIdEvents() {
    var event = ResourceIdEvent.of(RESOURCE_ID, RESOURCE_NAME, TENANT_ID, IndexActionType.DELETE);
    var actual = multiTenantConverter.convertDeleteEventsAsMap(List.of(event));
    assertThat(actual).isEqualTo(mapOf(RESOURCE_ID, searchDocumentBodyForDelete()));
  }

  @Test
  void convertDeleteEventsAsMap_positive_null() {
    var actual = multiTenantConverter.convertDeleteEventsAsMap(null);
    assertThat(actual).isEqualTo(emptyMap());
  }

  @Test
  void convertDeleteEventsAsMap_positive_emptyList() {
    var actual = multiTenantConverter.convertDeleteEventsAsMap(emptyList());
    assertThat(actual).isEqualTo(emptyMap());
  }

  private static List<ResourceEventBody> eventsForTenant(String tenant) {
    return List.of(
      eventBody("instance", Map.of("id", randomId())).tenant(tenant),
      eventBody("instance", Map.of("id", randomId())).tenant(tenant));
  }
}
