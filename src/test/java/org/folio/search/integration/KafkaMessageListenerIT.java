package org.folio.search.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.folio.search.model.types.ResourceType.BOUND_WITH;
import static org.folio.search.utils.KafkaConstants.BROWSE_CONFIG_DATA_LISTENER_ID;
import static org.folio.search.utils.KafkaConstants.EVENT_LISTENER_ID;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.TestConstants.inventoryAuthorityTopic;
import static org.folio.support.TestConstants.inventoryBoundWithTopic;
import static org.folio.support.TestConstants.inventoryInstanceTopic;
import static org.folio.support.utils.KafkaTestUtils.sendMessage;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.randomId;
import static org.folio.support.utils.TestUtils.removeEnvProperty;
import static org.folio.support.utils.TestUtils.resourceEvent;
import static org.folio.support.utils.TestUtils.setEnvProperty;
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
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.folio.search.configuration.RetryTemplateConfiguration;
import org.folio.search.configuration.kafka.InstanceResourceEventKafkaConfiguration;
import org.folio.search.configuration.kafka.ResourceEventKafkaConfiguration;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.integration.KafkaMessageListenerIT.KafkaListenerTestConfiguration;
import org.folio.search.integration.message.FolioMessageBatchProcessor;
import org.folio.search.integration.message.KafkaMessageListener;
import org.folio.search.integration.message.interceptor.ResourceEventBatchInterceptor;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.ResourceService;
import org.folio.search.service.config.ConfigSynchronizationService;
import org.folio.spring.DefaultFolioExecutionContext;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.spring.tools.kafka.FolioKafkaProperties;
import org.hibernate.exception.SQLGrammarException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Log4j2
@EmbeddedKafka(topics = {
  "kafka-listener-it.test_tenant.inventory.instance",
  "kafka-listener-it.test_tenant.inventory.bound-with",
  "kafka-listener-it.test_tenant.authorities.authority"
})
@IntegrationTest
@Import(KafkaListenerTestConfiguration.class)
@SpringBootTest(
  classes = {KafkaMessageListener.class, FolioKafkaProperties.class},
  properties = {
    "ENV=kafka-listener-it",
    "folio.environment=${ENV:kafka-listener-it}",
    "folio.kafka.retry-interval-ms=100",
    "folio.kafka.retry-delivery-attempts=3",
    "spring.kafka.consumer.auto-offset-reset=earliest"
  }, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class KafkaMessageListenerIT {

  private static final String INSTANCE_ID = randomId();
  private static final String KAFKA_LISTENER_IT_ENV = "kafka-listener-it";

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
  private EmbeddedKafkaBroker embeddedKafka;

  @Autowired
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

  private KafkaProducer<String, String> kafkaProducer;

  @BeforeAll
  static void beforeAll() {
    setEnvProperty(KAFKA_LISTENER_IT_ENV);
  }

  @AfterAll
  static void afterAll() {
    removeEnvProperty();
  }

  @BeforeEach
  void setUp() {
    lenient().doAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call())
      .when(executionService).executeSystemUserScoped(any(), any());

    var configs = KafkaTestUtils.producerProps(embeddedKafka);
    configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    kafkaProducer = new KafkaProducer<>(configs);
  }

  @Test
  void handleInstanceEvents_positive() {
    var expectedEvent = instanceEvent();

    sendMessage(INSTANCE_ID, expectedEvent, inventoryInstanceTopic(), kafkaProducer);

    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(resourceService).indexInstanceEvents(anyList()));
  }

  @Test
  void handleInstanceEvents_positive_boundWithEvent() {
    var boundWithEvent = resourceEvent(null, BOUND_WITH, mapOf("id", randomId(), "instanceId", INSTANCE_ID));
    var expectedEvent = instanceEvent().resourceName(BOUND_WITH.getName())._new(boundWithEvent.getNew());

    sendMessage(INSTANCE_ID, boundWithEvent, inventoryBoundWithTopic(), kafkaProducer);

    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(resourceService).indexInstanceEvents(anyList()));
  }

  @Test
  void handleInstanceEvents_negative_tenantIndexNotInitialized() {
    var idEvent = instanceEvent();

    when(resourceService.indexInstanceEvents(anyList())).thenThrow(
      new SearchOperationException("Failed to upload events"));

    sendMessage(INSTANCE_ID, idEvent, inventoryInstanceTopic(), kafkaProducer);

    await().atMost(FIVE_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(resourceService, times(3)).indexInstanceEvents(anyList()));
  }

  @Test
  void handleInstanceEvents_negative_tenantSchemaIsNotInitialized() {
    var idEvent = instanceEvent();

    when(resourceService.indexInstanceEvents(anyList())).thenThrow(
      new SQLGrammarException("could not extract ResultSet", new SQLException()));

    sendMessage(INSTANCE_ID, idEvent, inventoryInstanceTopic(), kafkaProducer);

    await().atMost(FIVE_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(resourceService, times(3)).indexInstanceEvents(anyList()));
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

    authorityIds.forEach(id -> sendMessage(INSTANCE_ID, authorityEvent(id), inventoryAuthorityTopic(), kafkaProducer));

    var expectedEvents = authorityIds.stream().map(KafkaMessageListenerIT::authorityEvent).toList();
    await().atMost(FIVE_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() -> {
      verify(resourceService).indexResources(List.of(expectedEvents.getFirst()));
      verify(resourceService, times(3)).indexResources(List.of(expectedEvents.get(1)));
    });
  }

  @Test
  void shouldAddEnvPrefixForConsumerGroup() {
    var listenerContainer = kafkaListenerEndpointRegistry.getListenerContainer(EVENT_LISTENER_ID);
    assertThat(listenerContainer).isNotNull();
    assertThat(listenerContainer.getGroupId()).startsWith(KAFKA_LISTENER_IT_ENV).endsWith("group");
    kafkaProperties.getListener().values()
      .forEach(listenerProperties -> assertThat(listenerProperties.getGroupId()).startsWith(KAFKA_LISTENER_IT_ENV));
  }

  @Test
  void shouldNotShareGroupId() {
    var listenerContainer = kafkaListenerEndpointRegistry.getListenerContainer(BROWSE_CONFIG_DATA_LISTENER_ID);
    assertThat(listenerContainer).isNotNull();
    assertThat(listenerContainer.getGroupId())
      .startsWith(KAFKA_LISTENER_IT_ENV + "-mod-search-browse-config-data-group-");
  }

  @Test
  void shouldUseCustomConsumerPattern() {
    kafkaProperties.getListener().values().forEach(
      listenerProperties -> assertThat(listenerProperties.getTopicPattern())
        .startsWith(String.format("(%s\\.)(.*\\.)", KAFKA_LISTENER_IT_ENV)));
  }

  private static ResourceEvent instanceEvent() {
    return resourceEvent(INSTANCE_ID, ResourceType.INSTANCE, mapOf("id", INSTANCE_ID));
  }

  private static ResourceEvent authorityEvent(String id) {
    return resourceEvent(id, ResourceType.AUTHORITY, mapOf("id", id)).id(id);
  }

  @TestConfiguration
  @EnableRetry(proxyTargetClass = true)
  @Import({
    InstanceResourceEventKafkaConfiguration.class, ResourceEventKafkaConfiguration.class,
    KafkaAutoConfiguration.class, FolioMessageBatchProcessor.class,
    RetryTemplateConfiguration.class, ResourceEventBatchInterceptor.class
  })
  static class KafkaListenerTestConfiguration {

    @Bean
    FolioExecutionContext folioExecutionContext() {
      return new DefaultFolioExecutionContext(null, Map.of(TENANT, List.of(TENANT_ID)));
    }
  }
}
