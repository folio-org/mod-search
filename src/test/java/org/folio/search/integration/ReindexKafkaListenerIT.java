package org.folio.search.integration;

import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.folio.search.integration.ReindexKafkaListenerIT.FOLIO_ENV;
import static org.folio.search.integration.ReindexKafkaListenerIT.KafkaListenerTestConfiguration;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestConstants.reindexRangeIndexTopic;
import static org.folio.search.utils.TestConstants.reindexRecordsTopic;
import static org.folio.search.utils.TestUtils.setEnvProperty;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
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
import org.folio.spring.testing.extension.EnableKafka;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.spring.tools.kafka.FolioKafkaProperties;
import org.folio.spring.tools.kafka.KafkaAdminService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.retry.annotation.EnableRetry;

@EnableKafka
@IntegrationTest
@Import(KafkaListenerTestConfiguration.class)
@SpringBootTest(
  classes = {ReindexKafkaListener.class, FolioKafkaProperties.class},
  properties = {FOLIO_ENV})
class ReindexKafkaListenerIT {

  static final String FOLIO_ENV_VAL = "reindex-listener-it";
  static final String FOLIO_ENV = "folio.environment=" + FOLIO_ENV_VAL;

  @MockBean
  private ConsortiumTenantExecutor executionService;
  @MockBean
  private SystemUserScopedExecutionService systemUserScopedExecutionService;
  @MockBean
  private ReindexOrchestrationService reindexService;
  @Autowired
  private KafkaTemplate<String, ReindexRangeIndexEvent> rangeKafkaTemplate;
  @Autowired
  private KafkaTemplate<String, ReindexRecordsEvent> recordsKafkaTemplate;

  @BeforeAll
  static void beforeAll(@Autowired KafkaAdminService kafkaAdminService) {
    setEnvProperty(FOLIO_ENV_VAL);
    kafkaAdminService.createTopics(TENANT_ID);
    kafkaAdminService.restartEventListeners();
  }

  @BeforeEach
  void setUp() {
    lenient().when(systemUserScopedExecutionService.executeSystemUserScoped(eq(TENANT_ID), any()))
        .thenAnswer(invocation -> ((Callable<?>) invocation.getArgument(1)).call());
    lenient().when(executionService.execute(any()))
      .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
  }

  @Test
  void handleInstanceEvents_positive() {
    var indexEvent = new ReindexRangeIndexEvent();
    indexEvent.setTenant(TENANT_ID);
    indexEvent.setId(UUID.randomUUID());
    rangeKafkaTemplate.send(reindexRangeIndexTopic(TENANT_ID), indexEvent);
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(reindexService).process(indexEvent));
  }

  @Test
  void handleInstanceEvents_negative_shouldRetryProcessing() {
    var indexEvent = new ReindexRangeIndexEvent();
    indexEvent.setTenant(TENANT_ID);
    indexEvent.setId(UUID.randomUUID());

    when(reindexService.process(indexEvent)).thenThrow(new RuntimeException("Failed to process"));

    rangeKafkaTemplate.send(reindexRangeIndexTopic(TENANT_ID), indexEvent);

    await().atMost(FIVE_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(reindexService, times(3)).process(indexEvent));
  }

  @Test
  void handleRecordsEvent_positive() {
    var indexEvent = new ReindexRecordsEvent();
    indexEvent.setTenant(TENANT_ID);
    indexEvent.setRecordType(ReindexRecordsEvent.ReindexRecordType.INSTANCE);
    indexEvent.setRecords(List.of(reindexRecord()));
    var mergeRangeId = UUID.randomUUID().toString();

    recordsKafkaTemplate.send(reindexRecordsTopic(TENANT_ID), mergeRangeId, indexEvent);

    indexEvent.setRangeId(mergeRangeId);
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(reindexService).process(indexEvent));
  }

  private Map<String, Object> reindexRecord() {
    return Map.of("id", UUID.randomUUID().toString());
  }

  @TestConfiguration
  @EnableRetry(proxyTargetClass = true)
  @RequiredArgsConstructor
  @Import({ReindexKafkaConfiguration.class, KafkaAutoConfiguration.class, KafkaAdminService.class})
  static class KafkaListenerTestConfiguration {

    private final KafkaProperties kafkaProperties;

    @Bean
    FolioExecutionContext folioExecutionContext() {
      return new DefaultFolioExecutionContext(null, Map.of(TENANT, List.of(TENANT_ID)));
    }

    @Bean
    public KafkaTemplate<String, ReindexRecordsEvent> reindexRecordsKafkaTemplate() {
      var configProps = new HashMap<>(kafkaProperties.buildProducerProperties(null));
      configProps.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
      configProps.put(VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
      var producerFactory = new DefaultKafkaProducerFactory<String, ReindexRecordsEvent>(configProps);
      return new KafkaTemplate<>(producerFactory);
    }

  }
}
