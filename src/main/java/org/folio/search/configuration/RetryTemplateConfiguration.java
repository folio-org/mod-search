package org.folio.search.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.FolioKafkaProperties;
import org.folio.search.configuration.properties.StreamIdsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

@Log4j2
@Configuration
@RequiredArgsConstructor
public class RetryTemplateConfiguration {

  public static final String KAFKA_RETRY_TEMPLATE_NAME = "kafkaMessageListenerRetryTemplate";
  public static final String STREAM_IDS_RETRY_TEMPLATE_NAME = "streamIdsRetryTemplate";
  private final FolioKafkaProperties folioKafkaProperties;
  private final StreamIdsProperties streamIdsProperties;

  /**
   * Constructs a batch handler that tries to deliver messages 10 times with configured interval, if exception is not
   * resolved than messages will be redelivered by next poll() call. Creates retry template to consume messages from
   * kafka.
   *
   * @return created {@link RetryTemplate} object
   */
  @Bean(name = KAFKA_RETRY_TEMPLATE_NAME)
  public RetryTemplate kafkaMessageListenerRetryTemplate() {
    return RetryTemplate.builder()
      .maxAttempts((int) folioKafkaProperties.getRetryDeliveryAttempts())
      .fixedBackoff(folioKafkaProperties.getRetryIntervalMs())
      .build();
  }

  @Bean(name = STREAM_IDS_RETRY_TEMPLATE_NAME)
  public RetryTemplate streamIdsRetryTemplate() {
    return RetryTemplate.builder()
      .maxAttempts((int) streamIdsProperties.getRetryAttempts())
      .fixedBackoff(streamIdsProperties.getRetryIntervalMs())
      .build();
  }
}
