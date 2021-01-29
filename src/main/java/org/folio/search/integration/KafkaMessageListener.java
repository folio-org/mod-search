package org.folio.search.integration;

import static java.util.stream.Collectors.toList;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.service.IndexService;
import org.folio.search.utils.SearchUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * A Spring component for consuming events from messaging system.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class KafkaMessageListener {

  private final IndexService indexService;

  /**
   * Listens the events with instance data inside from messaging system and sends request to index this values in search
   * engine.
   *
   * @param events list with the body as json from messaging system.
   */
  @KafkaListener(
    id = "mod-search-listener",
    containerFactory = "kafkaListenerContainerFactory",
    topics = "${application.kafka.listener.events.topics}",
    groupId = "${application.kafka.listener.events.group-id}",
    concurrency = "${application.kafka.listener.events.concurrency}")
  public void handleEvents(List<ResourceEventBody> events) {
    log.info("Processing resource events from kafka [number of events: {}]", events.size());
    var resources = events.stream()
      .map(KafkaMessageListener::asInstanceResource)
      .collect(toList());

    indexService.indexResources(resources);
  }

  private static ResourceEventBody asInstanceResource(ResourceEventBody eventBody) {
    eventBody.setResourceName(SearchUtils.INSTANCE_RESOURCE);
    return eventBody;
  }
}
