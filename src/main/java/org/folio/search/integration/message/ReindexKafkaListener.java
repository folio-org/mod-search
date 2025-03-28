package org.folio.search.integration.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.reindex.ReindexOrchestrationService;
import org.folio.search.utils.KafkaConstants;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class ReindexKafkaListener {

  private final ReindexOrchestrationService reindexService;
  private final ConsortiumTenantExecutor executionService;
  private final SystemUserScopedExecutionService systemUserScopedExecutionService;

  @KafkaListener(
    id = KafkaConstants.REINDEX_RANGE_INDEX_LISTENER_ID,
    containerFactory = "rangeIndexListenerContainerFactory",
    topicPattern = "#{folioKafkaProperties.listener['reindex-range-index'].topicPattern}",
    groupId = "#{folioKafkaProperties.listener['reindex-range-index'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['reindex-range-index'].concurrency}")
  public void handleReindexRangeEvents(ReindexRangeIndexEvent event) {
    log.debug("handleReindexRangeEvents::received reindex event [id={}]", event.getId());
    systemUserScopedExecutionService.executeSystemUserScoped(event.getTenant(),
      () -> executionService.execute(() -> reindexService.process(event)));
  }

  @KafkaListener(
    id = KafkaConstants.REINDEX_RECORDS_LISTENER_ID,
    containerFactory = "reindexRecordsListenerContainerFactory",
    topicPattern = "#{folioKafkaProperties.listener['reindex-records'].topicPattern}",
    groupId = "#{folioKafkaProperties.listener['reindex-records'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['reindex-records'].concurrency}")
  public void handleReindexRecordsEvent(ConsumerRecord<String, ReindexRecordsEvent> consumerRecord) {
    log.debug("handleReindexRecordsEvent::received reindex event [id={}]", consumerRecord.key());
    var event = consumerRecord.value();
    event.setRangeId(consumerRecord.key());
    systemUserScopedExecutionService.executeSystemUserScoped(event.getTenant(),
      () -> executionService.execute(() -> reindexService.process(event)));
  }
}
