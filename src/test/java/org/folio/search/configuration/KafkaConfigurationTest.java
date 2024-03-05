package org.folio.search.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.folio.spring.testing.type.UnitTest;
import org.folio.spring.tools.kafka.FolioKafkaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaConfigurationTest {

  @InjectMocks
  private KafkaConfiguration kafkaConfiguration;
  @InjectMocks
  private RetryTemplateConfiguration retryTemplateConfiguration;
  @Mock
  private KafkaProperties kafkaProperties;
  @Mock
  private FolioKafkaProperties folioKafkaProperties;

  @Test
  void standardListenerContainerFactory() {
    when(kafkaProperties.buildConsumerProperties(any())).thenReturn(Collections.emptyMap());
    var containerFactory = kafkaConfiguration.standardListenerContainerFactory();
    assertThat(containerFactory).isNotNull();
  }

  @Test
  void consortiumListenerContainerFactory() {
    when(kafkaProperties.buildConsumerProperties(any())).thenReturn(Collections.emptyMap());
    var containerFactory = kafkaConfiguration.consortiumListenerContainerFactory();
    assertThat(containerFactory).isNotNull();
  }

  @Test
  void kafkaMessageListenerRetryTemplate() {
    when(folioKafkaProperties.getRetryIntervalMs()).thenReturn(100L);
    when(folioKafkaProperties.getRetryDeliveryAttempts()).thenReturn(5L);
    var retryTemplate = retryTemplateConfiguration.kafkaMessageListenerRetryTemplate();
    assertThat(retryTemplate).isNotNull();
  }
}
