package org.folio.search.integration.message;

import static org.folio.search.service.reindex.ReindexConstants.REINDEX_JOB_ID_HEADER;

import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.reindex.ReindexJobEventFilter;
import org.folio.search.service.reindex.ReindexJobEventFilter.ReindexEventInfo;
import org.folio.search.service.reindex.ReindexOrchestrationService;
import org.folio.search.utils.KafkaConstants;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class ReindexKafkaListener {

  private static final String RANGE_INDEX_EVENT_TYPE = "reindex-range-index";
  private static final String RECORDS_EVENT_TYPE = "reindex-records";

  private final ReindexOrchestrationService reindexService;
  private final ConsortiumTenantExecutor executionService;
  private final SystemUserScopedExecutionService systemUserScopedExecutionService;
  private final ReindexJobEventFilter jobEventFilter;

  @KafkaListener(
    id = KafkaConstants.REINDEX_RANGE_INDEX_LISTENER_ID,
    containerFactory = "rangeIndexListenerContainerFactory",
    topicPattern = "#{folioKafkaProperties.listener['reindex-range-index'].topicPattern}",
    groupId = "#{folioKafkaProperties.listener['reindex-range-index'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['reindex-range-index'].concurrency}")
  public void handleInstanceEvents(ReindexRangeIndexEvent event) {
    var eventInfo = new ReindexEventInfo(RANGE_INDEX_EVENT_TYPE, event.getTenant(), String.valueOf(event.getId()),
      event.getReindexJobId(), "payload.reindexJobId");
    systemUserScopedExecutionService.executeSystemUserScoped(event.getTenant(),
      () -> executionService.execute(() -> jobEventFilter.shouldProcess(eventInfo) && reindexService.process(event)));
  }

  @KafkaListener(
    id = KafkaConstants.REINDEX_RECORDS_LISTENER_ID,
    containerFactory = "reindexRecordsListenerContainerFactory",
    topicPattern = "#{folioKafkaProperties.listener['reindex-records'].topicPattern}",
    groupId = "#{folioKafkaProperties.listener['reindex-records'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['reindex-records'].concurrency}")
  public void handleRecordsEvent(ConsumerRecord<String, ReindexRecordsEvent> consumerRecord) {
    var event = consumerRecord.value();
    event.setRangeId(consumerRecord.key());
    var eventInfo = new ReindexEventInfo(RECORDS_EVENT_TYPE, event.getTenant(), event.getRangeId(),
      getHeaderValue(consumerRecord, REINDEX_JOB_ID_HEADER), "header");
    systemUserScopedExecutionService.executeSystemUserScoped(event.getTenant(),
      () -> executionService.execute(() -> jobEventFilter.shouldProcess(eventInfo) && reindexService.process(event)));
  }

  private static String getHeaderValue(ConsumerRecord<?, ?> consumerRecord, String headerName) {
    return StreamSupport.stream(consumerRecord.headers().spliterator(), false)
      .filter(header -> headerName.equalsIgnoreCase(header.key()))
      .reduce((first, second) -> second)
      .map(Header::value)
      .map(value -> new String(value, StandardCharsets.UTF_8))
      .orElse(null);
  }
}
