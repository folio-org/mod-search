package org.folio.search.integration;

import static java.util.stream.Collectors.toList;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.folio.search.model.ResourceEventBody;
import org.folio.search.service.IndexService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * A Spring component for consuming events from messaging system.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMessageListener {

  private static final String INSTANCE_RESOURCE = "instance";
  private final IndexService indexService;

  /**
   * Listens the events with instance data inside from messaging system and sends request to index
   * this values in search engine.
   *
   * @param events list with the body as json from messaging system.
   */
  @KafkaListener(
      id = "search-events-listener",
      containerFactory = "kafkaListenerContainerFactory",
      topics = "${application.kafka.listener.events.topics}",
      groupId = "${application.kafka.listener.events.group-id}",
      concurrency = "${application.kafka.listener.events.concurrency}")
  public void handleEvents(List<ResourceEventBody> events) {
    log.info("Received data from kafka [eventsCount: {}]", events.size());
    var resources = events.stream()
        .map(event -> event.withResourceName(INSTANCE_RESOURCE))
        .collect(toList());

    indexService.indexResources(resources);
  }
}
