package org.folio.search.integration;

import static java.util.Collections.emptyList;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.eventBody;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.service.IndexService;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.FolioModuleMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaMessageListenerTest {

  @Mock
  private FolioModuleMetadata moduleMetadata;
  @Mock
  private IndexService indexService;
  @InjectMocks
  @Spy
  private KafkaMessageListener messageListener;

  @Test
  void handleEvents() {
    var resourceEvents = List.of(eventBody(INSTANCE_RESOURCE, mapOf("id", randomId())));

    when(indexService.indexResources(resourceEvents)).thenReturn(getSuccessIndexOperationResponse());
    messageListener.handleEvents(resourceEvents);
    verify(indexService).indexResources(resourceEvents);
  }

  @Test
  void shouldGroupEventsByTenant() {
    var event1ForTenantOne = simpleEventForTenant("tenant_one");
    var event2ForTenantOne = simpleEventForTenant("tenant_one");
    var eventForTenantTwo = simpleEventForTenant("tenant_two");
    var eventForTenantThree = simpleEventForTenant("tenant_three");

    when(indexService.indexResources(any())).thenReturn(getSuccessIndexOperationResponse());

    messageListener.handleEvents(List.of(event1ForTenantOne, eventForTenantThree, eventForTenantTwo,
      event2ForTenantOne));

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<ResourceEventBody>> arguments = ArgumentCaptor.forClass(List.class);

    verify(messageListener, times(1)).beginFolioExecutionContext("tenant_one");
    verify(messageListener, times(1)).beginFolioExecutionContext("tenant_two");
    verify(messageListener, times(1)).beginFolioExecutionContext("tenant_three");
    verify(messageListener, times(3)).endFolioExecutionContext();

    verify(indexService, times(3)).indexResources(arguments.capture());

    assertThat(getCapturedEventsForTenant(arguments, "tenant_one"),
      containsInAnyOrder(event1ForTenantOne, event2ForTenantOne));
    assertThat(getCapturedEventsForTenant(arguments, "tenant_two"),
      contains(eventForTenantTwo));
    assertThat(getCapturedEventsForTenant(arguments, "tenant_three"),
      contains(eventForTenantThree));
  }

  @Test
  void shouldFinalizeContextEventIfFailureOccurred() {
    when(indexService.indexResources(any())).thenThrow(new RuntimeException());

    handleEventsIgnoreException(simpleEventForTenant(TENANT_ID));

    verify(messageListener, times(1)).beginFolioExecutionContext(TENANT_ID);
    verify(messageListener, times(1)).endFolioExecutionContext();
  }

  private void handleEventsIgnoreException(ResourceEventBody event) {
    try {
      messageListener.handleEvents(List.of(event));
    } catch (Exception ex) {
      // nothing to do - ignoring
    }
  }

  private List<ResourceEventBody> getCapturedEventsForTenant(
    ArgumentCaptor<List<ResourceEventBody>> arguments, String tenant) {

    return arguments.getAllValues().stream()
      .filter(list -> list.get(0).getTenant().equals(tenant))
      .findFirst()
      .orElse(emptyList());
  }

  private ResourceEventBody simpleEventForTenant(String tenant) {
    return eventBody(INSTANCE_RESOURCE, Map.of("id", randomId()))
      .tenant(tenant);
  }
}
