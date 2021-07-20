package org.folio.search.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.folio.search.configuration.properties.FolioKafkaProperties;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaConfigurationTest {

  @InjectMocks private KafkaConfiguration kafkaConfiguration;
  @Mock private KafkaProperties kafkaProperties;
  @Mock private FolioKafkaProperties folioKafkaProperties;

  @Test
  void kafkaListenerContainerFactory() {
    when(kafkaProperties.buildConsumerProperties()).thenReturn(Collections.emptyMap());
    var containerFactory = kafkaConfiguration.kafkaListenerContainerFactory();
    assertThat(containerFactory).isNotNull();
  }

  @Test
  void kafkaMessageListenerRetryTemplate() {
    when(folioKafkaProperties.getRetryIntervalMs()).thenReturn(100L);
    when(folioKafkaProperties.getRetryDeliveryAttempts()).thenReturn(5L);
    var retryTemplate = kafkaConfiguration.kafkaMessageListenerRetryTemplate();
    assertThat(retryTemplate).isNotNull();
  }
}
