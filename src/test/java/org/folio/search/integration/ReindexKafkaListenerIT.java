package org.folio.search.integration;

import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.folio.search.integration.ReindexKafkaListenerIT.FOLIO_ENV;
import static org.folio.search.integration.ReindexKafkaListenerIT.KafkaListenerTestConfiguration;
import static org.folio.search.service.reindex.ReindexConstants.REINDEX_JOB_ID_HEADER;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestConstants.reindexRangeIndexTopic;
import static org.folio.search.utils.TestConstants.reindexRecordsTopic;
import static org.folio.search.utils.TestUtils.setEnvProperty;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.folio.search.configuration.kafka.ReindexKafkaConfiguration;
import org.folio.search.integration.message.ReindexKafkaListener;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.reindex.ReindexJobEventFilter;
import org.folio.search.service.reindex.ReindexJobEventFilter.ReindexEventInfo;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@EnableKafka
@IntegrationTest
@Import(KafkaListenerTestConfiguration.class)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@SpringBootTest(
  classes = {ReindexKafkaListener.class, FolioKafkaProperties.class},
  properties = {FOLIO_ENV})
class ReindexKafkaListenerIT {

  static final String FOLIO_ENV_VAL = "reindex-listener-it";
  static final String FOLIO_ENV = "folio.environment=" + FOLIO_ENV_VAL;

  @MockitoBean
  private ConsortiumTenantExecutor executionService;
  @MockitoBean
  private SystemUserScopedExecutionService systemUserScopedExecutionService;
  @MockitoBean
  private ReindexOrchestrationService reindexService;
  @MockitoBean
  private ReindexJobEventFilter jobEventFilter;
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
        .thenAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call());
    lenient().when(executionService.execute(any()))
      .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
    lenient().when(jobEventFilter.shouldProcess(any())).thenReturn(true);
  }

  @Test
  @Disabled("todo: fix in scope of MSEARCH-991")
  void handleInstanceEvents_positive() {
    var indexEvent = new ReindexRangeIndexEvent();
    indexEvent.setTenant(TENANT_ID);
    indexEvent.setId(UUID.randomUUID());
    rangeKafkaTemplate.send(reindexRangeIndexTopic(TENANT_ID), indexEvent);
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(reindexService).process(indexEvent));
  }

  @Test
  @Disabled("Will be fixed in MSEARCH-991")
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

  @Test
  void handleRecordsEvent_positive_passesJobIdFromHeaderToFilter() {
    var indexEvent = recordsEvent();
    var mergeRangeId = UUID.randomUUID().toString();
    var reindexJobId = UUID.randomUUID().toString();

    recordsKafkaTemplate.send(recordWithJobId(mergeRangeId, indexEvent, reindexJobId));

    indexEvent.setRangeId(mergeRangeId);
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(reindexService).process(indexEvent));
    assertThat(capturedEventInfo(mergeRangeId)).isEqualTo(
      new ReindexEventInfo("reindex-records", TENANT_ID, mergeRangeId, reindexJobId, "header"));
  }

  @Test
  void handleRecordsEvent_negative_skipsEventRejectedByFilter() {
    var indexEvent = recordsEvent();
    var mergeRangeId = UUID.randomUUID().toString();
    when(jobEventFilter.shouldProcess(any())).thenReturn(false);

    recordsKafkaTemplate.send(recordWithJobId(mergeRangeId, indexEvent, UUID.randomUUID().toString()));

    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      assertThat(capturedEventInfo(mergeRangeId)).isNotNull());
    verify(reindexService, after(500).never()).process(any(ReindexRecordsEvent.class));
  }

  private ReindexRecordsEvent recordsEvent() {
    var indexEvent = new ReindexRecordsEvent();
    indexEvent.setTenant(TENANT_ID);
    indexEvent.setRecordType(ReindexRecordsEvent.ReindexRecordType.INSTANCE);
    indexEvent.setRecords(List.of(reindexRecord()));
    return indexEvent;
  }

  private ProducerRecord<String, ReindexRecordsEvent> recordWithJobId(String key, ReindexRecordsEvent event,
                                                                      String reindexJobId) {
    var producerRecord = new ProducerRecord<>(reindexRecordsTopic(TENANT_ID), key, event);
    producerRecord.headers().add(REINDEX_JOB_ID_HEADER, reindexJobId.getBytes(StandardCharsets.UTF_8));
    return producerRecord;
  }

  private ReindexEventInfo capturedEventInfo(String rangeId) {
    var captor = ArgumentCaptor.forClass(ReindexEventInfo.class);
    verify(jobEventFilter, atLeastOnce()).shouldProcess(captor.capture());
    return captor.getAllValues().stream()
      .filter(info -> rangeId.equals(info.rangeId()))
      .findFirst()
      .orElse(null);
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
