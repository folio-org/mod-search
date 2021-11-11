package org.folio.search.integration;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.FIVE_SECONDS;
import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Duration.ONE_MINUTE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.service.KafkaAdminService.AUTHORITY_LISTENER_ID;
import static org.folio.search.service.KafkaAdminService.EVENT_LISTENER_ID;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestConstants.inventoryAuthorityTopic;
import static org.folio.search.utils.TestConstants.inventoryInstanceTopic;
import static org.folio.search.utils.TestUtils.array;
import static org.folio.search.utils.TestUtils.eventBody;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.setEnvProperty;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.folio.search.configuration.KafkaConfiguration;
import org.folio.search.configuration.properties.FolioKafkaProperties;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.exception.TenantNotInitializedException;
import org.folio.search.integration.KafkaMessageListenerIT.KafkaListenerTestConfiguration;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.service.IndexService;
import org.folio.search.service.KafkaAdminService;
import org.folio.search.service.LocalFileProvider;
import org.folio.search.support.extension.EnableKafka;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.types.IntegrationTest;
import org.folio.spring.DefaultFolioExecutionContext;
import org.folio.spring.FolioExecutionContext;
import org.hibernate.exception.SQLGrammarException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.EnableRetry;

@EnableKafka
@IntegrationTest
@Import(KafkaListenerTestConfiguration.class)
@SpringBootTest(classes = {KafkaMessageListener.class, FolioKafkaProperties.class}, properties = {
  "ENV=kafka-listener-it",
  "KAFKA_EVENTS_CONSUMER_PATTERN=(${application.environment}\\.)(.*\\.)inventory\\.(instance|holdings-record|item)",
  "KAFKA_AUTHORITIES_CONSUMER_PATTERN=(${application.environment}\\.)(.*\\.)inventory\\.authority",
  "application.environment=${ENV:folio}",
  "application.kafka.retry-interval-ms=10",
  "application.kafka.retry-delivery-attempts=3",
  "application.kafka.listener.events.concurrency=1",
  "application.kafka.listener.events.group-id=${application.environment}-test-group",
  "application.kafka.listener.authorities.group-id=${application.environment}-authority-test-group",
  "logging.level.org.apache.kafka.clients.consumer=warn"
})
class KafkaMessageListenerIT {

  private static final String INSTANCE_ID = randomId();
  private static final String KAFKA_LISTENER_IT_ENV = "kafka-listener-it";

  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
  @Autowired private FolioKafkaProperties kafkaProperties;
  @MockBean private IndexService indexService;
  @MockBean private AuthorityEventPreProcessor authorityEventPreProcessor;

  @Autowired
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

  @BeforeAll
  static void beforeAll(@Autowired KafkaAdminService kafkaAdminService) {
    setEnvProperty(KAFKA_LISTENER_IT_ENV);
    kafkaAdminService.createKafkaTopics();
    kafkaAdminService.restartEventListeners();
  }

  @Test
  void handleEvents_positive() {
    var expectedEvent = idEvent(INSTANCE_ID);
    kafkaTemplate.send(inventoryInstanceTopic(TENANT_ID), INSTANCE_ID, instanceEventBody());
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(indexService).indexResourcesById(List.of(expectedEvent)));
  }

  @Test
  void handleEvents_negative_tenantIndexNotInitialized() throws Exception {
    var idEvent = idEvent(INSTANCE_ID);

    when(indexService.indexResourcesById(List.of(idEvent))).thenThrow(
      new TenantNotInitializedException(array(TENANT_ID), null));

    kafkaTemplate.send(inventoryInstanceTopic(TENANT_ID), INSTANCE_ID, instanceEventBody()).get();

    await().atMost(FIVE_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(indexService, times(3)).indexResourcesById(List.of(idEvent)));
  }

  @Test
  void handleEvents_negative_tenantSchemaIsNotInitialized() throws Exception {
    var idEvent = idEvent(INSTANCE_ID);

    when(indexService.indexResourcesById(List.of(idEvent))).thenThrow(
      new SQLGrammarException("could not extract ResultSet", new SQLException()));

    kafkaTemplate.send(inventoryInstanceTopic(TENANT_ID), INSTANCE_ID, instanceEventBody()).get();

    await().atMost(FIVE_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      verify(indexService, times(3)).indexResourcesById(List.of(idEvent)));
  }

  @Test
  void handleEvents_positive_splittingBatchToTheParts() {
    var ids = List.of(randomId(), randomId(), randomId());

    when(indexService.indexResourcesById(anyList())).thenAnswer(inv -> {
      List<ResourceIdEvent> resourceIdEvents = inv.getArgument(0);
      if (resourceIdEvents.size() == 3) {
        throw new SearchOperationException("Failed to save bulk");
      }
      if (resourceIdEvents.get(0).getId().equals(ids.get(2))) {
        throw new SearchOperationException("Failed to save single resource");
      }
      return getSuccessIndexOperationResponse();
    });

    sendMessagesWithStoppedListenerContainer(ids, EVENT_LISTENER_ID,
      inventoryInstanceTopic(TENANT_ID), KafkaMessageListenerIT::instanceEventBody);

    var expectedEvents = ids.stream().map(KafkaMessageListenerIT::idEvent).collect(toList());
    await().atMost(FIVE_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() -> {
      verify(indexService).indexResourcesById(List.of(expectedEvents.get(0)));
      verify(indexService).indexResourcesById(List.of(expectedEvents.get(1)));
      verify(indexService, times(3)).indexResourcesById(List.of(expectedEvents.get(2)));
    });
  }

  @Test
  void handleEvents_positive_logFailedAuthorityEvent() {
    var authorityIds = List.of(randomId(), randomId());

    when(authorityEventPreProcessor.process(any())).thenAnswer(inv -> List.of(inv.<ResourceEventBody>getArgument(0)));
    when(indexService.indexResources(anyList())).thenAnswer(inv -> {
      var eventBodies = inv.<List<ResourceEventBody>>getArgument(0);
      if (eventBodies.size() == 2) {
        throw new SearchOperationException("Failed to save bulk");
      }
      if (eventBodies.get(0).getId().equals(authorityIds.get(1))) {
        throw new SearchOperationException("Failed to save single resource");
      }
      return getSuccessIndexOperationResponse();
    });

    sendMessagesWithStoppedListenerContainer(authorityIds, AUTHORITY_LISTENER_ID, inventoryAuthorityTopic(TENANT_ID),
      KafkaMessageListenerIT::authorityEventBody);

    var expectedEvents = authorityIds.stream().map(KafkaMessageListenerIT::authorityEventBody).collect(toList());
    await().atMost(FIVE_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() -> {
      verify(indexService).indexResources(List.of(expectedEvents.get(0)));
      verify(indexService, times(3)).indexResources(List.of(expectedEvents.get(1)));
    });
  }

  @Test
  void shouldAddEnvPrefixForConsumerGroup() {
    var container = kafkaListenerEndpointRegistry.getListenerContainer(EVENT_LISTENER_ID);
    assertThat(container.getGroupId()).startsWith(KAFKA_LISTENER_IT_ENV);
    kafkaProperties.getListener().values().forEach(
      listenerProperties -> assertThat(listenerProperties.getGroupId()).startsWith(KAFKA_LISTENER_IT_ENV));
  }

  @Test
  void shouldUseCustomConsumerPattern() {
    var container = kafkaListenerEndpointRegistry.getListenerContainer(EVENT_LISTENER_ID);
    assertThat(container.getGroupId()).startsWith(KAFKA_LISTENER_IT_ENV);
    kafkaProperties.getListener().values().forEach(
      listenerProperties -> assertThat(listenerProperties.getTopicPattern())
        .startsWith(String.format("(%s.)(.*.)", KAFKA_LISTENER_IT_ENV)));
  }

  private void sendMessagesWithStoppedListenerContainer(List<String> ids, String containerId, String topicName,
    Function<String, ResourceEventBody> resourceEventFunction) {
    var container = kafkaListenerEndpointRegistry.getListenerContainer(containerId);
    container.stop();
    ids.forEach(id -> kafkaTemplate.send(topicName, id, resourceEventFunction.apply(id)));
    container.start();
  }

  private static ResourceIdEvent idEvent(String id) {
    return ResourceIdEvent.of(id, INSTANCE_RESOURCE, TENANT_ID, INDEX);
  }

  private static ResourceEventBody instanceEventBody() {
    return instanceEventBody(INSTANCE_ID);
  }

  private static ResourceEventBody instanceEventBody(String instanceId) {
    return eventBody(INSTANCE_RESOURCE, mapOf("id", instanceId));
  }

  private static ResourceEventBody authorityEventBody(String id) {
    return eventBody(AUTHORITY_RESOURCE, mapOf("id", id)).id(id);
  }

  @TestConfiguration
  @EnableRetry(proxyTargetClass = true)
  @Import({
    KafkaConfiguration.class, KafkaAutoConfiguration.class, FolioMessageBatchProcessor.class,
    KafkaAdminService.class, LocalFileProvider.class, JsonConverter.class, JacksonAutoConfiguration.class
  })
  static class KafkaListenerTestConfiguration {

    @Bean
    FolioExecutionContext folioExecutionContext() {
      return new DefaultFolioExecutionContext(null, Map.of(TENANT, List.of(TENANT_ID)));
    }
  }
}
