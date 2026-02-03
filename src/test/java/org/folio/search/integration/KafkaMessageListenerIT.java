package org.folio.search.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.folio.search.model.types.ResourceType.BOUND_WITH;
import static org.folio.search.utils.KafkaConstants.EVENT_LISTENER_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestConstants.inventoryBoundWithTopic;
import static org.folio.search.utils.TestConstants.inventoryInstanceTopic;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.removeEnvProperty;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.setEnvProperty;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.folio.search.configuration.RetryTemplateConfiguration;
import org.folio.search.configuration.kafka.InstanceResourceEventKafkaConfiguration;
import org.folio.search.configuration.kafka.ResourceEventKafkaConfiguration;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.configuration.properties.StreamIdsProperties;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.integration.KafkaMessageListenerIT.KafkaListenerTestConfiguration;
import org.folio.search.integration.message.FolioMessageBatchProcessor;
import org.folio.search.integration.message.InstanceEventMapper;
import org.folio.search.integration.message.KafkaMessageListener;
import org.folio.search.integration.message.interceptor.ResourceEventBatchInterceptor;
import org.folio.search.model.event.IndexInstanceEvent;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.ResourceService;
import org.folio.search.service.config.ConfigSynchronizationService;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.spring.DefaultFolioExecutionContext;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.spring.testing.extension.EnableKafka;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.spring.tools.kafka.FolioKafkaProperties;
import org.folio.spring.tools.kafka.KafkaAdminService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Log4j2
@EnableKafka
@IntegrationTest
@Import(KafkaListenerTestConfiguration.class)
@SpringBootTest(
  classes = {KafkaMessageListener.class, FolioKafkaProperties.class, StreamIdsProperties.class,
             ReindexConfigurationProperties.class, InstanceEventMapper.class},
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
  private FolioKafkaProperties kafkaProperties;
  @Autowired
  private KafkaTemplate<String, ResourceEvent> resourceKafkaTemplate;
  @MockitoBean
  private ResourceService resourceService;
  @MockitoBean
  private SystemUserScopedExecutionService executionService;
  @MockitoBean
  private ConfigSynchronizationService configSynchronizationService;
  @MockitoBean
  private KafkaTemplate<String, IndexInstanceEvent> instanceEventProducer;
  @MockitoBean
  private ConsortiumTenantService consortiumTenantService;
  @Captor
  private ArgumentCaptor<ProducerRecord<String, IndexInstanceEvent>> producerRecordCaptor;

  @Autowired
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

  @BeforeAll
  static void beforeAll(@Autowired KafkaAdminService kafkaAdminService) {
    setEnvProperty(KAFKA_LISTENER_IT_ENV);
    kafkaAdminService.createTopics(TENANT_ID);
    kafkaAdminService.restartEventListeners();
  }

  @AfterAll
  static void afterAll() {
    removeEnvProperty();
  }

  @BeforeEach
  void setUp() {
    lenient().doAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call())
      .when(executionService).executeSystemUserScoped(any(), any());
  }

  @Test
  void handleInstanceEvents_positive() {
    resourceKafkaTemplate.send(inventoryInstanceTopic(), INSTANCE_ID, instanceEvent());

    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(instanceEventProducer).send(producerRecordCaptor.capture()));

    var capturedEvent = producerRecordCaptor.getValue();
    var expectedEvent = new IndexInstanceEvent(TENANT_ID, INSTANCE_ID);
    assertThat(capturedEvent.key()).isEqualTo(INSTANCE_ID);
    assertThat(capturedEvent.value()).isEqualTo(expectedEvent);
  }

  @Test
  void handleInstanceEvents_positive_boundWithEvent() {
    var boundWithEvent = resourceEvent(null, BOUND_WITH, mapOf("id", randomId(), "instanceId", INSTANCE_ID));

    resourceKafkaTemplate.send(inventoryBoundWithTopic(), INSTANCE_ID, boundWithEvent);

    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(instanceEventProducer).send(producerRecordCaptor.capture()));

    var capturedEvent = producerRecordCaptor.getValue();
    var expectedEvent = new IndexInstanceEvent(TENANT_ID, INSTANCE_ID);
    assertThat(capturedEvent.key()).isEqualTo(INSTANCE_ID);
    assertThat(capturedEvent.value()).isEqualTo(expectedEvent);
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
  void shouldUseCustomConsumerPattern() {
    kafkaProperties.getListener().values().forEach(
      listenerProperties -> assertThat(listenerProperties.getTopicPattern())
        .startsWith(String.format("(%s\\.)(.*\\.)", KAFKA_LISTENER_IT_ENV)));
  }

  private static ResourceEvent instanceEvent() {
    return resourceEvent(INSTANCE_ID, ResourceType.INSTANCE, mapOf("id", INSTANCE_ID));
  }

  @TestConfiguration
  @EnableRetry(proxyTargetClass = true)
  @Import({
    InstanceResourceEventKafkaConfiguration.class, ResourceEventKafkaConfiguration.class,
    KafkaAutoConfiguration.class, FolioMessageBatchProcessor.class,
    RetryTemplateConfiguration.class, ResourceEventBatchInterceptor.class,
    KafkaAdminService.class
  })
  static class KafkaListenerTestConfiguration {

    @Bean
    FolioExecutionContext folioExecutionContext() {
      return new DefaultFolioExecutionContext(null, Map.of(TENANT, List.of(TENANT_ID)));
    }
  }
}
