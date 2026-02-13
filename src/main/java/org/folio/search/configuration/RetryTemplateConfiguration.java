package org.folio.search.configuration;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.configuration.properties.StreamIdsProperties;
import org.folio.search.exception.FolioIntegrationException;
import org.folio.spring.tools.kafka.FolioKafkaProperties;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;

@Log4j2
@Configuration
@RequiredArgsConstructor
public class RetryTemplateConfiguration {

  public static final String KAFKA_RETRY_TEMPLATE_NAME = "kafkaMessageListenerRetryTemplate";
  public static final String STREAM_IDS_RETRY_TEMPLATE_NAME = "streamIdsRetryTemplate";
  public static final String REINDEX_PUBLISH_RANGE_RETRY_TEMPLATE_NAME = "reindexPublishRangeRetryTemplate";

  /**
   * Constructs a batch handler that tries to deliver messages 10 times with configured interval, if exception is not
   * resolved than messages will be redelivered by next poll() call. Creates retry template to consume messages from
   * kafka.
   *
   * @return created {@link RetryTemplate} object
   */
  @ConditionalOnBean(FolioKafkaProperties.class)
  @Bean(name = KAFKA_RETRY_TEMPLATE_NAME)
  public RetryTemplate kafkaMessageListenerRetryTemplate(FolioKafkaProperties properties) {
    return new RetryTemplate(RetryPolicy.builder()
      .maxRetries(properties.getRetryDeliveryAttempts())
      .delay(Duration.ofMillis(properties.getRetryIntervalMs()))
      .build());
  }

  @ConditionalOnBean(StreamIdsProperties.class)
  @Bean(name = STREAM_IDS_RETRY_TEMPLATE_NAME)
  public RetryTemplate streamIdsRetryTemplate(StreamIdsProperties properties) {
    return new RetryTemplate(RetryPolicy.builder()
      .maxRetries(properties.getRetryAttempts())
      .delay(Duration.ofMillis(properties.getRetryIntervalMs()))
      .build());
  }

  @ConditionalOnBean(ReindexConfigurationProperties.class)
  @Bean(name = REINDEX_PUBLISH_RANGE_RETRY_TEMPLATE_NAME)
  public RetryTemplate reindexPublishRangeRetryTemplate(ReindexConfigurationProperties properties) {
    var retryTemplate = new RetryTemplate(RetryPolicy.builder()
      .maxRetries(properties.getMergeRangePublisherRetryAttempts())
      .delay(Duration.ofMillis(properties.getMergeRangePublisherRetryIntervalMs()))
      .build());
    retryTemplate.setRetryListener(new RetryListener() {
      @Override
      public void onRetryPolicyExhaustion(@NonNull RetryPolicy retryPolicy,
                                          @NonNull Retryable<?> retryable,
                                          @NonNull RetryException exception) {
        var lastThrowable = exception.getLastException();
        log.error(new FormattedMessage("Failed to publish reindex records range"), lastThrowable);
        throw new FolioIntegrationException("Failed to publish reindex records range after all retries", lastThrowable);
      }
    });
    return retryTemplate;
  }
}
