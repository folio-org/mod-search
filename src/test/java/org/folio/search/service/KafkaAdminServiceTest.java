package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.kafka.clients.admin.NewTopic;
import org.folio.search.configuration.properties.FolioEnvironment;
import org.folio.search.service.KafkaAdminService.KafkaTopic;
import org.folio.search.service.KafkaAdminService.KafkaTopics;
import org.folio.search.service.KafkaAdminServiceTest.KafkaAdminServiceTestConfiguration;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.DefaultFolioExecutionContext;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.integration.XOkapiHeaders;
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

  @Autowired private KafkaAdminService kafkaAdminService;
  @Autowired private ApplicationContext applicationContext;
  @MockBean private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
  @MockBean private LocalFileProvider localFileProvider;
  @MockBean private KafkaAdmin kafkaAdmin;

  @Test
  void createKafkaTopics_positive() {
    System.setProperty("KAFKA_TOPIC2_PARTITIONS", "50");
    System.setProperty("KAFKA_TOPIC2_REPLICATION_FACTOR", "3");
    when(localFileProvider.readAsObject("kafka/kafka-topics.json", KafkaTopics.class)).thenReturn(KafkaTopics.of(
      List.of(
        KafkaTopic.of("topic1", "${KAFKA_TOPIC1_PARTITIONS:20}", "${KAFKA_EVENT_TOPICS_REPLICATION_FACTOR:}"),
        KafkaTopic.of("topic2", "${KAFKA_TOPIC2_PARTITIONS}", "${KAFKA_TOPIC2_REPLICATION_FACTOR}"),
        KafkaTopic.of("topic3", "40", "2"))));

    kafkaAdminService.createKafkaTopics();
    verify(kafkaAdmin).initialize();

    var beansOfType = applicationContext.getBeansOfType(NewTopic.class);
    assertThat(beansOfType.values()).containsExactlyInAnyOrderElementsOf(List.of(
      new NewTopic("folio.test_tenant.topic1", Optional.of(20), Optional.empty()),
      new NewTopic("folio.test_tenant.topic2", Optional.of(50), Optional.of((short) 3)),
      new NewTopic("folio.test_tenant.topic3", Optional.of(40), Optional.of((short) 2))
    ));
  }

  @Test
  void createKafkaTopics_negative_failedToResolvePlaceholders() {
    when(localFileProvider.readAsObject("kafka/kafka-topics.json", KafkaTopics.class)).thenReturn(KafkaTopics.of(
      List.of(KafkaTopic.of("topic2", "${KAFKA_PARTITIONS}", "${KAFKA_REPLICATION_FACTOR}"))));

    assertThatThrownBy(() -> kafkaAdminService.createKafkaTopics())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Could not resolve placeholder 'KAFKA_PARTITIONS' in value \"${KAFKA_PARTITIONS}\"");
  }

  @Test
  void getTenantKafkaTopics() {
    when(localFileProvider.readAsObject("kafka/kafka-topics.json", KafkaTopics.class))
      .thenReturn(KafkaTopics.of(List.of(
        KafkaTopic.of("topic1", "20", "1"),
        KafkaTopic.of("topic2", "50", "3"),
        KafkaTopic.of("topic3", "40", "2")
      )));
    var tenantKafkaTopics = kafkaAdminService.getDefaultTenantKafkaTopics();
    assertThat(tenantKafkaTopics).isEqualTo(List.of(
      "folio.test_tenant.topic1", "folio.test_tenant.topic2", "folio.test_tenant.topic3"));
  }

  @Test
  void restartEventListeners() {
    var mockListenerContainer = mock(MessageListenerContainer.class);
    when(kafkaListenerEndpointRegistry.getAllListenerContainers()).thenReturn(List.of(mockListenerContainer));
    kafkaAdminService.restartEventListeners();
    verify(mockListenerContainer).start();
    verify(mockListenerContainer).stop();
  }

  @TestConfiguration
  static class KafkaAdminServiceTestConfiguration {

    @Bean(name = "folio.test_tenant.topic3.topic")
    NewTopic firstTopic() {
      return new NewTopic("folio.test_tenant.topic3", 40, (short) 2);
    }

    @Bean
    FolioExecutionContext folioExecutionContext() {
      return new DefaultFolioExecutionContext(null, Map.of(XOkapiHeaders.TENANT, List.of(TENANT_ID)));
    }

    @Bean
    FolioEnvironment appConfigurationProperties() {
      var config = new FolioEnvironment();
      config.setEnvironment("folio");
      return config;
    }
  }
}
