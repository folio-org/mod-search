package org.folio.search.service;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.search.service.KafkaAdminService.KafkaTopic;
import org.folio.search.service.KafkaAdminService.KafkaTopics;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.kafka.core.KafkaAdmin;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaAdminServiceTest {

  @InjectMocks private KafkaAdminService kafkaAdminService;
  @Mock private ConfigurableBeanFactory beanFactory;
  @Mock private LocalFileProvider localFileProvider;
  @Mock private KafkaAdmin kafkaAdmin;

  @Test
  void createKafkaTopics() {
    var topic1 = KafkaTopic.of("topic1", 20, (short) 1);
    var topic2 = KafkaTopic.of("topic2", 50, (short) 1);
    var topic3 = KafkaTopic.of("topic3", 40, (short) 1);
    var kafkaTopics = KafkaTopics.of(List.of(topic1, topic2, topic3));

    when(localFileProvider.readAsObject("kafka/kafka-topics.json", KafkaTopics.class)).thenReturn(kafkaTopics);
    doReturn(true).when(beanFactory).containsBean("topic1.topic");
    doReturn(false).when(beanFactory).containsBean("topic2.topic");
    doReturn(false).when(beanFactory).containsBean("topic3.topic");

    kafkaAdminService.createKafkaTopics();

    verify(beanFactory).registerSingleton("topic2.topic", topic2.toKafkaTopic());
    verify(beanFactory).registerSingleton("topic3.topic", topic3.toKafkaTopic());
    verify(kafkaAdmin).initialize();
  }
}
