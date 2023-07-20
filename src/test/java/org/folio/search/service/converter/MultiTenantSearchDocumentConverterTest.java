package org.folio.search.service.converter;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.SMILE_MAPPER;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceDescription;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.ResourceIndexingConfiguration;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.model.types.IndexingDataFormat;
import org.folio.search.service.consortium.ConsortiaTenantExecutor;
import org.folio.search.service.converter.preprocessor.EventPreProcessor;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.common.bytes.BytesArray;

@UnitTest
@ExtendWith(MockitoExtension.class)
class MultiTenantSearchDocumentConverterTest {

  private static final String CUSTOM_PRE_PROCESSOR = "testPreProcessor";

  @InjectMocks
  private MultiTenantSearchDocumentConverter multiTenantConverter;
  @Mock
  private SearchDocumentConverter searchDocumentConverter;
  @Mock
  private ConsortiaTenantExecutor executionService;
  @Mock
  private EventPreProcessor customEventPreProcessor;
  @Mock
  private Map<String, EventPreProcessor> eventPreProcessorBeans;
  @Mock
  private ResourceDescriptionService resourceDescriptionService;
  @Mock
  private FolioExecutionContext folioExecutionContext;

  @Test
  void convert_positive() {
    when(executionService.execute(anyString(), any()))
      .thenAnswer(invocation -> invocation.<Supplier<List<SearchDocumentBody>>>getArgument(1).get());
    var tenant1 = "tenant_one";
    var tenant2 = "tenant_two";
    var events = List.of(
      resourceEvent(null, RESOURCE_NAME, mapOf("id", randomId())).tenant(tenant1).type(ResourceEventType.UPDATE),
      resourceEvent(null, RESOURCE_NAME, mapOf("id", randomId())).tenant(tenant1).type(ResourceEventType.DELETE),
      resourceEvent(null, RESOURCE_NAME, mapOf("id", randomId())).tenant(tenant2).type(ResourceEventType.UPDATE),
      resourceEvent(null, RESOURCE_NAME, mapOf("id", randomId())).tenant(tenant2).type(ResourceEventType.DELETE));

    when(resourceDescriptionService.find(RESOURCE_NAME)).thenReturn(of(resourceDescription(RESOURCE_NAME)));
    when(searchDocumentConverter.convert(events.get(0))).thenReturn(of(searchDocument(events.get(0), INDEX)));
    when(searchDocumentConverter.convert(events.get(1))).thenReturn(of(searchDocument(events.get(1), DELETE)));
    when(searchDocumentConverter.convert(events.get(2))).thenReturn(of(searchDocument(events.get(2), INDEX)));
    when(searchDocumentConverter.convert(events.get(3))).thenReturn(of(searchDocument(events.get(3), DELETE)));

    var actual = multiTenantConverter.convert(events);

    assertThat(actual).isEqualTo(Map.of(RESOURCE_NAME, List.of(
      searchDocument(events.get(0), INDEX), searchDocument(events.get(1), DELETE),
      searchDocument(events.get(2), INDEX), searchDocument(events.get(3), DELETE))));

    verify(executionService).execute(eq("tenant_one"), any());
    verify(executionService).execute(eq("tenant_two"), any());
  }

  @Test
  void convert_positive_noScoped() {
    var tenant1 = "tenant_one";
    var events = List.of(
      resourceEvent(null, RESOURCE_NAME, mapOf("id", randomId())).tenant(tenant1).type(ResourceEventType.UPDATE),
      resourceEvent(null, RESOURCE_NAME, mapOf("id", randomId())).tenant(tenant1).type(ResourceEventType.DELETE));

    when(resourceDescriptionService.find(RESOURCE_NAME)).thenReturn(of(resourceDescription(RESOURCE_NAME)));
    when(searchDocumentConverter.convert(events.get(0))).thenReturn(of(searchDocument(events.get(0), INDEX)));
    when(searchDocumentConverter.convert(events.get(1))).thenReturn(of(searchDocument(events.get(1), DELETE)));
    when(folioExecutionContext.getTenantId()).thenReturn(tenant1);

    var actual = multiTenantConverter.convert(events);

    assertThat(actual).isEqualTo(Map.of(RESOURCE_NAME, List.of(
      searchDocument(events.get(0), INDEX), searchDocument(events.get(1), DELETE))));

    verifyNoInteractions(executionService);
  }

  @Test
  void convert_positive_singleEventThatIsNotConverted() {
    var event = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID));
    when(resourceDescriptionService.find(RESOURCE_NAME)).thenReturn(of(resourceDescription(RESOURCE_NAME)));
    when(searchDocumentConverter.convert(event)).thenReturn(Optional.empty());
    when(executionService.execute(eq(TENANT_ID), any())).thenAnswer(invocation ->
      invocation.<Supplier<List<SearchDocumentBody>>>getArgument(1).get());

    var actual = multiTenantConverter.convert(List.of(event));
    assertThat(actual).isEqualTo(emptyMap());
  }

  @Test
  void convert_positive_eventWithCustomEventPreProcessor() {
    var event = resourceEvent(RESOURCE_NAME, mapOf("id", RESOURCE_ID));
    var searchDocument = searchDocument(event, INDEX);

    when(resourceDescriptionService.find(RESOURCE_NAME)).thenReturn(of(resourceDescriptionWithPreProcessor()));
    when(searchDocumentConverter.convert(event)).thenReturn(of(searchDocument));
    when(eventPreProcessorBeans.get(CUSTOM_PRE_PROCESSOR)).thenReturn(customEventPreProcessor);
    when(customEventPreProcessor.process(event)).thenReturn(List.of(event));
    when(executionService.execute(eq(TENANT_ID), any())).thenAnswer(invocation ->
      invocation.<Supplier<List<SearchDocumentBody>>>getArgument(1).get());

    var actual = multiTenantConverter.convert(List.of(event));
    assertThat(actual).isEqualTo(mapOf(RESOURCE_NAME, List.of(searchDocument(event, INDEX))));
  }

  @Test
  void convert_positive_null() {
    var actual = multiTenantConverter.convert(null);
    assertThat(actual).isEqualTo(emptyMap());
  }

  @Test
  void convert_positive_emptyList() {
    var actual = multiTenantConverter.convert(emptyList());
    assertThat(actual).isEqualTo(emptyMap());
  }

  @SneakyThrows
  private static SearchDocumentBody searchDocument(ResourceEvent event, IndexActionType type) {
    return SearchDocumentBody.of(type == INDEX ? new BytesArray(SMILE_MAPPER.writeValueAsBytes(event.getNew())) : null,
      IndexingDataFormat.SMILE, event, type);
  }

  private static ResourceDescription resourceDescriptionWithPreProcessor() {
    var configuration = new ResourceIndexingConfiguration();
    configuration.setEventPreProcessor(CUSTOM_PRE_PROCESSOR);

    var resourceDescription = resourceDescription(RESOURCE_NAME);
    resourceDescription.setIndexingConfiguration(configuration);
    return resourceDescription;
  }
}
