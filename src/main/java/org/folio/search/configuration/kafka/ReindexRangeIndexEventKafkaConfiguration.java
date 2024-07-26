package org.folio.search.configuration.kafka;

import static org.folio.search.configuration.kafka.KafkaConfiguration.SearchTopic.REINDEX_RANGE_INDEX;

import lombok.RequiredArgsConstructor;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@RequiredArgsConstructor
public class ReindexRangeIndexEventKafkaConfiguration extends KafkaConfiguration {

  private final KafkaProperties kafkaProperties;

  @Bean
  public KafkaTemplate<String, ReindexRangeIndexEvent> rangeIndexKafkaTemplate() {
    return new KafkaTemplate<>(getProducerFactory(kafkaProperties));
  }

  @Bean
  public FolioMessageProducer<ReindexRangeIndexEvent> rangeIndexMessageProducer() {
    return new FolioMessageProducer<>(rangeIndexKafkaTemplate(), REINDEX_RANGE_INDEX);
  }
}
