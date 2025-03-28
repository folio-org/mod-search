package org.folio.search.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.configuration.properties.StreamIdsProperties;
import org.folio.spring.tools.kafka.FolioKafkaProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

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
    return RetryTemplate.builder()
      .maxAttempts((int) properties.getRetryDeliveryAttempts())
      .fixedBackoff(properties.getRetryIntervalMs())
      .build();
  }

  @ConditionalOnBean(StreamIdsProperties.class)
  @Bean(name = STREAM_IDS_RETRY_TEMPLATE_NAME)
  public RetryTemplate streamIdsRetryTemplate(StreamIdsProperties properties) {
    return RetryTemplate.builder()
      .maxAttempts(properties.getRetryAttempts())
      .fixedBackoff(properties.getRetryIntervalMs())
      .build();
  }

  @ConditionalOnBean(ReindexConfigurationProperties.class)
  @Bean(name = REINDEX_PUBLISH_RANGE_RETRY_TEMPLATE_NAME)
  public RetryTemplate reindexPublishRangeRetryTemplate(ReindexConfigurationProperties properties) {
    return RetryTemplate.builder()
      .maxAttempts(properties.getMergeRangePublisherRetryAttempts())
      .fixedBackoff(properties.getMergeRangePublisherRetryIntervalMs())
      .build();
  }
}
