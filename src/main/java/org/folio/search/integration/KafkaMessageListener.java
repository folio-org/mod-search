package org.folio.search.integration;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.service.IndexService;
import org.folio.search.utils.SearchUtils;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.scope.FolioExecutionScopeExecutionContextManager;
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
  private final FolioModuleMetadata moduleMetadata;

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

    resources.stream()
      .collect(groupingBy(ResourceEventBody::getTenant))
      .forEach((tenant, eventsForTenant) -> {
        try {
          log.info("Processing [{}] events for tenant [{}]", eventsForTenant.size(), tenant);
          // This needed to inject FOLIO specific values to properly work with DB
          beginFolioExecutionContext(tenant);

          indexService.indexResources(eventsForTenant);
        } finally {
          endFolioExecutionContext();
        }
      });
  }

  // Package access required for tests
  void beginFolioExecutionContext(String tenant) {
    FolioExecutionScopeExecutionContextManager
      .beginFolioExecutionContext(new AsyncFolioExecutionContext(tenant, moduleMetadata));
  }

  // Package access required for tests
  void endFolioExecutionContext() {
    FolioExecutionScopeExecutionContextManager.endFolioExecutionContext();
  }

  private static ResourceEventBody asInstanceResource(ResourceEventBody eventBody) {
    eventBody.setResourceName(SearchUtils.INSTANCE_RESOURCE);
    return eventBody;
  }
}
