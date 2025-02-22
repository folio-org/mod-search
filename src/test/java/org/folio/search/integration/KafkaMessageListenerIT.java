package org.folio.search.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.folio.search.model.types.ResourceType.BOUND_WITH;
import static org.folio.search.utils.KafkaConstants.AUTHORITY_LISTENER_ID;
import static org.folio.search.utils.KafkaConstants.EVENT_LISTENER_ID;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestConstants.inventoryAuthorityTopic;
import static org.folio.search.utils.TestConstants.inventoryBoundWithTopic;
import static org.folio.search.utils.TestConstants.inventoryInstanceTopic;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.setEnvProperty;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.RetryTemplateConfiguration;
import org.folio.search.configuration.kafka.InstanceResourceEventKafkaConfiguration;
import org.folio.search.configuration.kafka.ResourceEventKafkaConfiguration;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.configuration.properties.StreamIdsProperties;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.integration.KafkaMessageListenerIT.KafkaListenerTestConfiguration;
import org.folio.search.integration.message.FolioMessageBatchProcessor;
import org.folio.search.integration.message.KafkaMessageListener;
import org.folio.search.integration.message.interceptor.ResourceEventBatchInterceptor;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.ResourceService;
import org.folio.search.service.config.ConfigSynchronizationService;
import org.folio.search.service.metadata.LocalFileProvider;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.DefaultFolioExecutionContext;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.spring.testing.extension.EnableKafka;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.spring.tools.kafka.FolioKafkaProperties;
import org.folio.spring.tools.kafka.KafkaAdminService;
import org.hibernate.exception.SQLGrammarException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Log4j2
@EnableKafka
@IntegrationTest
@Import(KafkaListenerTestConfiguration.class)
@SpringBootTest(
  classes = {KafkaMessageListener.class, FolioKafkaProperties.class, StreamIdsProperties.class,
             ReindexConfigurationProperties.class},
  properties = {
    "ENV=kafka-listener-it",
    "folio.environment=${ENV:folio}",
    "folio.kafka.retry-interval-ms=10",
    "folio.kafka.retry-delivery-attempts=3",
    "folio.kafka.listener.events.concurrency=1",
    "folio.kafka.listener.events.group-id=${folio.environment}-test-group",
    "folio.kafka.listener.authorities.group-id=${folio.environment}-authority-test-group",
    "logging.level.org.apache.kafka.clients.consumer=warn"
  })
class KafkaMessageListenerIT {

  private static final String INSTANCE_ID = randomId();
  private static final String KAFKA_LISTENER_IT_ENV = "kafka-listener-it";

  @Autowired
  private KafkaTemplate<String, ResourceEvent> resourceKafkaTemplate;
  @Autowired
  private FolioKafkaProperties kafkaProperties;
  @MockitoBean
  private ResourceService resourceService;
  @MockitoBean
  private SystemUserScopedExecutionService executionService;
  @MockitoBean
  private ConfigSynchronizationService configSynchronizationService;

  @Autowired
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

  @BeforeAll
  static void beforeAll(@Autowired KafkaAdminService kafkaAdminService) {
    setEnvProperty(KAFKA_LISTENER_IT_ENV);
    kafkaAdminService.createTopics(TENANT_ID);
    kafkaAdminService.restartEventListeners();
  }

  @BeforeEach
  void setUp() {
    lenient().doAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call())
      .when(executionService).executeSystemUserScoped(any(), any());
  }

  @Test
  void handleInstanceEvents_positive() {
    var expectedEvent = instanceEvent();
    resourceKafkaTemplate.send(inventoryInstanceTopic(), INSTANCE_ID, instanceEvent());
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(resourceService).indexInstancesById(List.of(expectedEvent)));
  }

  @Test
  void handleInstanceEvents_positive_boundWithEvent() {
    var boundWithEvent = resourceEvent(null, BOUND_WITH, mapOf("id", randomId(), "instanceId", INSTANCE_ID));
    var expectedEvent = instanceEvent().resourceName(BOUND_WITH.getName())._new(boundWithEvent.getNew());
    resourceKafkaTemplate.send(inventoryBoundWithTopic(), INSTANCE_ID, boundWithEvent);
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(resourceService).indexInstancesById(List.of(expectedEvent)));
  }

  @Test
  void handleInstanceEvents_negative_tenantIndexNotInitialized() throws Exception {
    var idEvent = instanceEvent();

    when(resourceService.indexInstancesById(List.of(idEvent))).thenThrow(
      new SearchOperationException("Failed to upload events"));

    resourceKafkaTemplate.send(inventoryInstanceTopic(), INSTANCE_ID, instanceEvent()).get();

    await().atMost(FIVE_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(resourceService, times(3)).indexInstancesById(List.of(idEvent)));
  }

  @Test
  void handleInstanceEvents_negative_tenantSchemaIsNotInitialized() throws Exception {
    var idEvent = instanceEvent();

    when(resourceService.indexInstancesById(List.of(idEvent))).thenThrow(
      new SQLGrammarException("could not extract ResultSet", new SQLException()));

    resourceKafkaTemplate.send(inventoryInstanceTopic(), INSTANCE_ID, instanceEvent()).get();

    await().atMost(FIVE_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(resourceService, times(3)).indexInstancesById(List.of(idEvent)));
  }

  @Test
  void handleInstanceEvents_positive_logFailedAuthorityEvent() {
    var authorityIds = List.of(randomId(), randomId());
    when(resourceService.indexResources(anyList())).thenAnswer(inv -> {
      var eventBodies = inv.<List<ResourceEvent>>getArgument(0);
      if (eventBodies.size() == 2) {
        throw new SearchOperationException("Failed to save bulk");
      }
      if (Objects.equals(eventBodies.getFirst().getId(), authorityIds.get(1))) {
        throw new SearchOperationException("Failed to save single resource");
      }
      return getSuccessIndexOperationResponse();
    });

    sendMessagesWithStoppedListenerContainer(authorityIds, AUTHORITY_LISTENER_ID, inventoryAuthorityTopic(),
      KafkaMessageListenerIT::authorityEvent);

    var expectedEvents = authorityIds.stream().map(KafkaMessageListenerIT::authorityEvent).toList();
    await().atMost(FIVE_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() -> {
      verify(resourceService).indexResources(List.of(expectedEvents.get(0)));
      verify(resourceService, times(3)).indexResources(List.of(expectedEvents.get(1)));
    });
  }

  @Test
  void shouldAddEnvPrefixForConsumerGroup() {
    var container = getKafkaListenerContainer(EVENT_LISTENER_ID);
    assertThat(container.getGroupId()).startsWith(KAFKA_LISTENER_IT_ENV);
    kafkaProperties.getListener().values().forEach(
      listenerProperties -> assertThat(listenerProperties.getGroupId()).startsWith(KAFKA_LISTENER_IT_ENV));
  }

  @Test
  void shouldUseCustomConsumerPattern() {
    kafkaProperties.getListener().values().forEach(
      listenerProperties -> assertThat(listenerProperties.getTopicPattern())
        .startsWith(String.format("(%s\\.)(.*\\.)", KAFKA_LISTENER_IT_ENV)));
  }

  private void sendMessagesWithStoppedListenerContainer(List<String> ids, String containerId, String topicName,
                                                        Function<String, ResourceEvent> resourceEventFunction) {
    var container = getKafkaListenerContainer(containerId);
    log.info("Stopping listener container");
    container.stop();
    ids.forEach(id -> resourceKafkaTemplate.send(topicName, id, resourceEventFunction.apply(id)));
    log.info("Starting listener container");
    container.start();
    await().atMost(FIVE_SECONDS).until(container::isRunning, Boolean.TRUE::equals);
  }

  private MessageListenerContainer getKafkaListenerContainer(String containerId) {
    var container = kafkaListenerEndpointRegistry.getListenerContainer(containerId);
    assertThat(container).isNotNull();
    return container;
  }

  private static ResourceEvent instanceEvent() {
    return instanceEvent(INSTANCE_ID);
  }

  private static ResourceEvent instanceEvent(String instanceId) {
    return resourceEvent(instanceId, ResourceType.INSTANCE, mapOf("id", instanceId));
  }

  private static ResourceEvent authorityEvent(String id) {
    return resourceEvent(id, ResourceType.AUTHORITY, mapOf("id", id)).id(id);
  }

  @TestConfiguration
  @EnableRetry(proxyTargetClass = true)
  @Import({
    InstanceResourceEventKafkaConfiguration.class, ResourceEventKafkaConfiguration.class,
    KafkaAutoConfiguration.class, FolioMessageBatchProcessor.class,
    KafkaAdminService.class, LocalFileProvider.class, JsonConverter.class, JacksonAutoConfiguration.class,
    RetryTemplateConfiguration.class, ResourceEventBatchInterceptor.class
  })
  static class KafkaListenerTestConfiguration {

    @Bean
    FolioExecutionContext folioExecutionContext() {
      return new DefaultFolioExecutionContext(null, Map.of(TENANT, List.of(TENANT_ID)));
    }
  }
}
