package org.folio.search.service;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.service.KafkaAdminService.EVENT_LISTENER_ID;
import static org.folio.search.utils.TestConstants.ENV;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.folio.search.service.KafkaAdminService.KafkaTopic;
import org.folio.search.service.KafkaAdminService.KafkaTopics;
import org.folio.search.service.KafkaAdminServiceTest.KafkaAdminServiceTestConfiguration;
import org.folio.search.utils.TestUtils;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.DefaultFolioExecutionContext;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.integration.XOkapiHeaders;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.MessageListenerContainer;

@UnitTest
@Import(KafkaAdminServiceTestConfiguration.class)
@SpringBootTest(classes = KafkaAdminService.class)
class KafkaAdminServiceTest {

  private final List<KafkaTopic> expectedTopics = List.of(
    KafkaTopic.of("topic1", 20, (short) 1),
    KafkaTopic.of("topic2", 50, (short) 3),
    KafkaTopic.of("topic3", 40, (short) 2),
    KafkaTopic.of("test.test_tenant.topic1", 20, (short) 1),
    KafkaTopic.of("test.test_tenant.topic2", 50, (short) 3),
    KafkaTopic.of("test.test_tenant.topic3", 40, (short) 2));

  private final List<KafkaTopic> initialKafkaTopics = List.of(
    expectedTopics.get(0), expectedTopics.get(1), expectedTopics.get(2));

  @Autowired private KafkaAdminService kafkaAdminService;
  @Autowired private ApplicationContext applicationContext;
  @MockBean private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
  @MockBean private LocalFileProvider localFileProvider;
  @MockBean private KafkaAdmin kafkaAdmin;

  @BeforeAll
  static void beforeAll() {
    TestUtils.setEnvProperty(ENV);
  }

  @AfterAll
  static void afterAll() {
    TestUtils.removeEnvProperty();
  }

  @Test
  void createKafkaTopics() {
    when(localFileProvider.readAsObject("kafka/kafka-topics.json", KafkaTopics.class))
      .thenReturn(KafkaTopics.of(initialKafkaTopics));
    kafkaAdminService.createKafkaTopics();

    verify(kafkaAdmin).initialize();

    var beansOfType = applicationContext.getBeansOfType(NewTopic.class);
    var expectedNewTopics = expectedTopics.stream().map(KafkaTopic::toKafkaTopic).collect(toSet());
    assertThat(beansOfType.values()).containsExactlyInAnyOrderElementsOf(expectedNewTopics);
  }

  @Test
  void getTenantKafkaTopics() {
    when(localFileProvider.readAsObject("kafka/kafka-topics.json", KafkaTopics.class))
      .thenReturn(KafkaTopics.of(initialKafkaTopics));
    var tenantKafkaTopics = kafkaAdminService.getDefaultTenantKafkaTopics();
    assertThat(tenantKafkaTopics).isEqualTo(List.of(
      "test.test_tenant.topic1", "test.test_tenant.topic2", "test.test_tenant.topic3"));
  }

  @Test
  void restartEventListeners() {
    var mockListenerContainer = mock(MessageListenerContainer.class);
    when(kafkaListenerEndpointRegistry.getListenerContainer(EVENT_LISTENER_ID)).thenReturn(mockListenerContainer);
    kafkaAdminService.restartEventListeners();
    verify(mockListenerContainer).start();
    verify(mockListenerContainer).stop();
  }

  @TestConfiguration
  static class KafkaAdminServiceTestConfiguration {

    @Bean(name = "topic1.topic")
    NewTopic firstTopic() {
      return new NewTopic("topic1", 20, (short) 1);
    }

    @Bean
    FolioExecutionContext folioExecutionContext() {
      return new DefaultFolioExecutionContext(null, Map.of(XOkapiHeaders.TENANT, List.of(TENANT_ID)));
    }
  }
}
