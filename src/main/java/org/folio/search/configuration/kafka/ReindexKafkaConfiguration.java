package org.folio.search.configuration.kafka;

import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG;
import static org.folio.search.configuration.kafka.KafkaConfiguration.SearchTopic.REINDEX_RANGE_INDEX;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.folio.search.model.event.ReindexFileReadyEvent;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.spring.tools.kafka.FolioKafkaProperties;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

@Log4j2
@Configuration
@RequiredArgsConstructor
public class ReindexKafkaConfiguration extends KafkaConfiguration {

  private static final Map<String, Object> REINDEX_CONSUMER_OVERRIDE_PROPERTIES = Map.of(MAX_POLL_RECORDS_CONFIG, 10);

  private final KafkaProperties kafkaProperties;

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, ReindexRangeIndexEvent> rangeIndexListenerContainerFactory(
    CommonErrorHandler commonErrorHandler) {
    return listenerContainerFactory(ReindexRangeIndexEvent.class, commonErrorHandler);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, ReindexRecordsEvent> reindexRecordsListenerContainerFactory(
    CommonErrorHandler commonErrorHandler) {
    return listenerContainerFactory(ReindexRecordsEvent.class, commonErrorHandler);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, ReindexFileReadyEvent>
    reindexFileReadyListenerContainerFactory(CommonErrorHandler commonErrorHandler) {
    return listenerContainerFactory(ReindexFileReadyEvent.class, commonErrorHandler);
  }

  @Bean
  public CommonErrorHandler errorHandler(FolioKafkaProperties kafkaProperties) {
    var backOff = new FixedBackOff(kafkaProperties.getRetryIntervalMs(), kafkaProperties.getRetryDeliveryAttempts());
    return new DefaultErrorHandler((consumerRecord, exception) -> {
      var message = new ParameterizedMessage("Error occurred while processing event=[{}], topic=[{}]",
        consumerRecord.value(), consumerRecord.topic());
      log.error(message, exception);
    }, backOff);
  }

  @Bean
  public KafkaTemplate<String, ReindexRangeIndexEvent> rangeIndexKafkaTemplate() {
    return new KafkaTemplate<>(getProducerFactory(kafkaProperties));
  }

  @Bean
  public FolioMessageProducer<ReindexRangeIndexEvent> rangeIndexMessageProducer() {
    return new FolioMessageProducer<>(rangeIndexKafkaTemplate(), REINDEX_RANGE_INDEX);
  }

  private <T> ConcurrentKafkaListenerContainerFactory<String, T> listenerContainerFactory(
    Class<T> valueType, CommonErrorHandler errorHandler) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, T>();
    var deserializer = new JacksonJsonDeserializer<>(valueType, false);
    factory.setConsumerFactory(
      getConsumerFactory(deserializer, kafkaProperties, REINDEX_CONSUMER_OVERRIDE_PROPERTIES));
    factory.setCommonErrorHandler(errorHandler);
    return factory;
  }
}
