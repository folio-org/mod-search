package org.folio.search.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.folio.search.configuration.properties.OpensearchProperties;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.configuration.properties.StreamIdsProperties;
import org.folio.spring.testing.type.UnitTest;
import org.folio.spring.tools.kafka.FolioKafkaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.retry.RetryException;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RetryTemplateConfigurationTest {

  @InjectMocks
  private RetryTemplateConfiguration configuration;
  @Mock
  private FolioKafkaProperties folioKafkaProperties;
  @Mock
  private StreamIdsProperties streamIdsProperties;
  @Mock
  private ReindexConfigurationProperties reindexConfigurationProperties;

  @Test
  void createKafkaMessageListenerRetryTemplate_positive() {
    when(folioKafkaProperties.getRetryDeliveryAttempts()).thenReturn(5L);
    when(folioKafkaProperties.getRetryIntervalMs()).thenReturn(200L);
    var actual = configuration.kafkaMessageListenerRetryTemplate(folioKafkaProperties);
    assertThat(actual).isNotNull();
  }

  @Test
  void createStreamIdsRetryTemplate_positive() {
    when(streamIdsProperties.getRetryAttempts()).thenReturn(5);
    when(streamIdsProperties.getRetryIntervalMs()).thenReturn(200L);
    var actual = configuration.streamIdsRetryTemplate(streamIdsProperties);
    assertThat(actual).isNotNull();
  }

  @Test
  void createReindexPublishRangeRetryTemplate_positive() {
    when(reindexConfigurationProperties.getMergeRangePublisherRetryAttempts()).thenReturn(5);
    when(reindexConfigurationProperties.getMergeRangePublisherRetryIntervalMs()).thenReturn(200L);
    var actual = configuration.reindexPublishRangeRetryTemplate(reindexConfigurationProperties);
    assertThat(actual).isNotNull();
  }

  @Test
  void searchRetryTemplate_connectionClosedException_retries() {
    var properties = new OpensearchProperties();
    properties.setSearchRetryAttempts(3);
    properties.setSearchRetryIntervalMs(1);

    var retryTemplate = configuration.searchRetryTemplate(properties);
    var attempts = new AtomicInteger();

    assertThatThrownBy(() -> retryTemplate.execute(() -> {
      attempts.incrementAndGet();
      throw new ConnectionClosedException("closed");
    })).isInstanceOf(RetryException.class);

    assertThat(attempts.get()).isGreaterThan(1);
  }

  @Test
  void searchRetryTemplate_wrappedConnectionClosedException_retries() {
    var properties = new OpensearchProperties();
    properties.setSearchRetryAttempts(3);
    properties.setSearchRetryIntervalMs(1);

    var retryTemplate = configuration.searchRetryTemplate(properties);
    var attempts = new AtomicInteger();

    assertThatThrownBy(() -> retryTemplate.execute(() -> {
      attempts.incrementAndGet();
      throw new RuntimeException(new ConnectionClosedException("closed"));
    })).isInstanceOf(RetryException.class)
      .hasRootCauseInstanceOf(ConnectionClosedException.class);

    assertThat(attempts.get()).isGreaterThan(1);
  }

  @Test
  void searchRetryTemplate_otherException_doesNotRetry() {
    var properties = new OpensearchProperties();
    properties.setSearchRetryAttempts(3);
    properties.setSearchRetryIntervalMs(1);

    var retryTemplate = configuration.searchRetryTemplate(properties);
    var attempts = new AtomicInteger();

    assertThatThrownBy(() -> retryTemplate.execute(() -> {
      attempts.incrementAndGet();
      throw new IllegalStateException("boom");
    })).isInstanceOf(RetryException.class);

    assertThat(attempts.get()).isEqualTo(1);
  }
}
