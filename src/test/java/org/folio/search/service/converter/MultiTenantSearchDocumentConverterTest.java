package org.folio.search.service.converter;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.SearchDocumentBody.forDeleteResourceEvent;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.searchDocumentBodyForDelete;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.apache.commons.collections.MapUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.integration.AuthorityEventPreProcessor;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.service.TenantScopedExecutionService;
import org.folio.search.utils.TestUtils;
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
  @Mock private AuthorityEventPreProcessor authorityEventPreProcessor;
  @InjectMocks private MultiTenantSearchDocumentConverter multiTenantConverter;

  @Test
  void shouldConvertEventsPerTenant() {
    when(executionService.executeTenantScoped(anyString(), any()))
      .thenAnswer(invocation -> invocation.<Callable<List<SearchDocumentBody>>>getArgument(1).call());

    var eventsForTenantOne = eventsForTenant("tenant_one");
    var eventsForTenantTwo = eventsForTenant("tenant_two");

    when(searchDocumentConverter.convert(eventsForTenantOne.get(0))).thenReturn(Optional.of(
      searchDocumentBody(eventsForTenantOne.get(0))));
    when(searchDocumentConverter.convert(eventsForTenantTwo.get(0))).thenReturn(Optional.of(
      searchDocumentBody(eventsForTenantTwo.get(0))));

    var allEvents = new ArrayList<>(eventsForTenantOne);
    allEvents.addAll(eventsForTenantTwo);

    var actual = multiTenantConverter.convert(allEvents);

    assertThat(actual).isEqualTo(List.of(
      searchDocumentBody(eventsForTenantOne.get(0)), forDeleteResourceEvent(eventsForTenantOne.get(1)),
      searchDocumentBody(eventsForTenantTwo.get(0)), forDeleteResourceEvent(eventsForTenantTwo.get(1))));

    verify(executionService).executeTenantScoped(eq("tenant_one"), any());
    verify(executionService).executeTenantScoped(eq("tenant_two"), any());
  }

  @Test
  void convertIndexEventsAsMap_positive_indexEvents() {
    var events = List.of(resourceEvent("instance", Map.of("id", RESOURCE_ID)));
    var expectedBody = TestUtils.searchDocumentBody();

    when(searchDocumentConverter.convert(events.get(0))).thenReturn(Optional.of(expectedBody));
    when(executionService.executeTenantScoped(eq(TENANT_ID), any())).thenAnswer(invocation ->
      invocation.<Callable<List<SearchDocumentBody>>>getArgument(1).call());

    var actual = multiTenantConverter.convertAsMap(events);
    assertThat(actual).isEqualTo(mapOf(RESOURCE_ID, expectedBody));
  }

  @Test
  void convertIndexEventsAsMap_positive_null() {
    var actual = multiTenantConverter.convertAsMap(null);
    assertThat(actual).isEqualTo(emptyMap());
  }

  @Test
  void convertIndexEventsAsMap_positive_emptyList() {
    var actual = multiTenantConverter.convertAsMap(emptyList());
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

  @Test
  void convertAuthorityEvent_positive() {
    var event = resourceEvent(AUTHORITY_RESOURCE, mapOf("id", RESOURCE_ID));
    var searchDocument = searchDocumentBody(event);

    when(authorityEventPreProcessor.process(event)).thenReturn(List.of(event));
    when(searchDocumentConverter.convert(event)).thenReturn(Optional.of(searchDocument));
    when(executionService.executeTenantScoped(eq(TENANT_ID), any())).thenAnswer(invocation ->
      invocation.<Callable<List<SearchDocumentBody>>>getArgument(1).call());

    var actual = multiTenantConverter.convert(List.of(event));
    assertThat(actual).isEqualTo(List.of(searchDocumentBody(event)));
  }

  private static List<ResourceEvent> eventsForTenant(String tenant) {
    return List.of(
      resourceEvent(null, RESOURCE_NAME, Map.of("id", randomId())).tenant(tenant).type(ResourceEventType.UPDATE),
      resourceEvent(null, RESOURCE_NAME, Map.of("id", randomId())).tenant(tenant).type(ResourceEventType.DELETE));
  }

  @SuppressWarnings("unchecked")
  private static SearchDocumentBody searchDocumentBody(ResourceEvent event) {
    var id = MapUtils.getString((Map<String, Object>) event.getNew(), "id");
    return SearchDocumentBody.of(id, event.getTenant(), INDEX_NAME, asJsonString(event.getNew()), INDEX);
  }
}
