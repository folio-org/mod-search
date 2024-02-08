package org.folio.search.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.folio.search.configuration.properties.StreamIdsProperties;
import org.folio.spring.testing.type.UnitTest;
import org.folio.spring.tools.kafka.FolioKafkaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RetryTemplateConfigurationTest {

  @InjectMocks
  private RetryTemplateConfiguration configuration;
  @Mock
  private FolioKafkaProperties folioKafkaProperties;
  @Mock
  private StreamIdsProperties streamIdsProperties;

  @Test
  void createKafkaMessageListenerRetryTemplate_positive() {
    when(folioKafkaProperties.getRetryDeliveryAttempts()).thenReturn(5L);
    when(folioKafkaProperties.getRetryIntervalMs()).thenReturn(200L);
    var actual = configuration.kafkaMessageListenerRetryTemplate();
    assertThat(actual).isNotNull();
  }

  @Test
  void createStreamIdsRetryTemplate_positive() {
    when(streamIdsProperties.getRetryAttempts()).thenReturn(5L);
    when(streamIdsProperties.getRetryIntervalMs()).thenReturn(200L);
    var actual = configuration.streamIdsRetryTemplate();
    assertThat(actual).isNotNull();
  }
}
