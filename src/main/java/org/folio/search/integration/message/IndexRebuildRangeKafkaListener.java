package org.folio.search.integration.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.event.IndexRebuildRangeEvent;
import org.folio.search.service.EgressExecutionContextService;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.indexrebuild.derived.DerivedRangeProcessor;
import org.folio.search.utils.KafkaConstants;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class IndexRebuildRangeKafkaListener {

  private final DerivedRangeProcessor rangeProcessor;
  private final ConsortiumTenantExecutor executionService;
  private final EgressExecutionContextService scopedExecutionService;

  @KafkaListener(
    id = KafkaConstants.INDEX_REBUILD_RANGE_LISTENER_ID,
    containerFactory = "indexRebuildRangeListenerContainerFactory",
    topicPattern = "#{folioKafkaProperties.listener['index-rebuild-range'].topicPattern}",
    groupId = "#{folioKafkaProperties.listener['index-rebuild-range'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['index-rebuild-range'].concurrency}")
  public void handleIndexRebuildRangeEvent(IndexRebuildRangeEvent event) {
    log.debug("handleIndexRebuildRangeEvent:: received event [resource: {}, id: {}]", event.getResource(),
      event.getId());
    scopedExecutionService.execute(event.getTenant(), () -> executionService.execute(() -> {
      rangeProcessor.process(event);
      return null;
    }));
  }
}
