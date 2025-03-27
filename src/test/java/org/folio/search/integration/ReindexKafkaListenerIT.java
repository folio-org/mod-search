package org.folio.search.integration;

import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.folio.search.integration.ReindexKafkaListenerIT.KafkaListenerTestConfiguration;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.TestConstants.reindexRangeIndexTopic;
import static org.folio.support.TestConstants.reindexRecordsTopic;
import static org.folio.support.utils.KafkaTestUtils.FOLIO_ENV;
import static org.folio.support.utils.KafkaTestUtils.FOLIO_ENV_VAL;
import static org.folio.support.utils.KafkaTestUtils.sendMessage;
import static org.folio.support.utils.TestUtils.removeEnvProperty;
import static org.folio.support.utils.TestUtils.setEnvProperty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.folio.search.configuration.kafka.ReindexKafkaConfiguration;
import org.folio.search.integration.message.ReindexKafkaListener;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.reindex.ReindexOrchestrationService;
import org.folio.spring.DefaultFolioExecutionContext;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.spring.tools.kafka.FolioKafkaProperties;
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
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@EmbeddedKafka(topics = {
  "reindex-listener-it.test_tenant.inventory.reindex-records",
  "reindex-listener-it.test_tenant.search.reindex.range-index"
})
@IntegrationTest
@Import(KafkaListenerTestConfiguration.class)
@SpringBootTest(
  classes = {ReindexKafkaListener.class, FolioKafkaProperties.class},
  properties = {FOLIO_ENV, "folio.kafka.retry-interval-ms=100", "spring.kafka.consumer.auto-offset-reset=earliest"},
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReindexKafkaListenerIT {

  @MockitoBean
  private ConsortiumTenantExecutor executionService;
  @MockitoBean
  private SystemUserScopedExecutionService systemUserScopedExecutionService;
  @MockitoBean
  private ReindexOrchestrationService reindexService;

  @Autowired
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  private EmbeddedKafkaBroker embeddedKafka;

  private KafkaProducer<String, String> kafkaProducer;

  @BeforeAll
  static void beforeAll() {
    setEnvProperty(FOLIO_ENV_VAL);
  }

  @AfterAll
  static void afterAll() {
    removeEnvProperty();
  }

  @BeforeEach
  void setUp() {
    lenient().when(systemUserScopedExecutionService.executeSystemUserScoped(eq(TENANT_ID), any()))
      .thenAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call());
    lenient().when(executionService.execute(any()))
      .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());

    var configs = KafkaTestUtils.producerProps(embeddedKafka);
    configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    kafkaProducer = new KafkaProducer<>(configs);
  }

  @Test
  void handleReindexRangeEvents_positive() {
    var indexEvent = new ReindexRangeIndexEvent();
    indexEvent.setTenant(TENANT_ID);
    indexEvent.setId(UUID.randomUUID());
    sendMessage(indexEvent, reindexRangeIndexTopic(TENANT_ID), kafkaProducer);
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(reindexService).process(indexEvent));
  }

  @Test
  void handleReindexRangeEvents_negative_shouldRetryProcessing() {
    var indexEvent = new ReindexRangeIndexEvent();
    indexEvent.setTenant(TENANT_ID);
    indexEvent.setId(UUID.randomUUID());

    when(reindexService.process(indexEvent)).thenThrow(new RuntimeException("Failed to process"));

    sendMessage(indexEvent, reindexRangeIndexTopic(TENANT_ID), kafkaProducer);

    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(reindexService, times(3)).process(indexEvent));
  }

  @Test
  void handleReindexRecordsEvent_positive() {
    var indexEvent = new ReindexRecordsEvent();
    indexEvent.setTenant(TENANT_ID);
    indexEvent.setRecordType(ReindexRecordsEvent.ReindexRecordType.INSTANCE);
    indexEvent.setRecords(List.of(reindexRecord()));
    var mergeRangeId = UUID.randomUUID().toString();

    sendMessage(mergeRangeId, indexEvent, reindexRecordsTopic(TENANT_ID),
      kafkaProducer);

    indexEvent.setRangeId(mergeRangeId);
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(reindexService).process(indexEvent));
  }

  private Map<String, Object> reindexRecord() {
    return Map.of("id", UUID.randomUUID().toString());
  }

  @TestConfiguration
  @EnableRetry(proxyTargetClass = true)
  @Import({ReindexKafkaConfiguration.class, KafkaAutoConfiguration.class})
  static class KafkaListenerTestConfiguration {

    @Bean
    public FolioExecutionContext folioExecutionContext() {
      return new DefaultFolioExecutionContext(null, Map.of(TENANT, List.of(TENANT_ID)));
    }

  }
}
