package org.folio.search.integration.message;

import static org.folio.search.service.reindex.ReindexConstants.REINDEX_JOB_ID_HEADER;

import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.folio.search.model.event.ReindexFileReadyEvent;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.service.EgressExecutionContextService;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.reindex.ReindexJobEventFilter;
import org.folio.search.service.reindex.ReindexJobEventFilter.ReindexEventInfo;
import org.folio.search.service.reindex.ReindexOrchestrationService;
import org.folio.search.utils.KafkaConstants;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class ReindexKafkaListener {

  private static final String RANGE_INDEX_EVENT_TYPE = "reindex-range-index";
  private static final String RECORDS_EVENT_TYPE = "reindex-records";
  private static final String FILE_READY_EVENT_TYPE = "reindex-file-ready";
  private static final String HEADER_SOURCE = "header";

  private final ReindexOrchestrationService reindexService;
  private final ConsortiumTenantExecutor executionService;
  private final EgressExecutionContextService scopedExecutionService;
  private final ReindexJobEventFilter jobEventFilter;

  @KafkaListener(
    id = KafkaConstants.REINDEX_RANGE_INDEX_LISTENER_ID,
    containerFactory = "rangeIndexListenerContainerFactory",
    topicPattern = "#{folioKafkaProperties.listener['reindex-range-index'].topicPattern}",
    groupId = "#{folioKafkaProperties.listener['reindex-range-index'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['reindex-range-index'].concurrency}")
  public void handleReindexRangeEvents(ReindexRangeIndexEvent event) {
    log.debug("handleReindexRangeEvents::received reindex event [id={}]", event.getId());
    var eventInfo = new ReindexEventInfo(RANGE_INDEX_EVENT_TYPE, event.getTenant(), String.valueOf(event.getId()),
      event.getReindexJobId(), "payload.reindexJobId");
    scopedExecutionService.execute(event.getTenant(),
      () -> executionService.execute(() -> jobEventFilter.shouldProcess(eventInfo) && reindexService.process(event)));
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
    var eventInfo = new ReindexEventInfo(RECORDS_EVENT_TYPE, event.getTenant(), event.getRangeId(),
      getHeaderValue(consumerRecord, REINDEX_JOB_ID_HEADER), HEADER_SOURCE);
    scopedExecutionService.execute(event.getTenant(),
      () -> executionService.execute(() -> jobEventFilter.shouldProcess(eventInfo) && reindexService.process(event)));
  }

  @KafkaListener(
    id = KafkaConstants.REINDEX_RECORDS_FILE_READY_ID,
    containerFactory = "reindexFileReadyListenerContainerFactory",
    topicPattern = "#{folioKafkaProperties.listener['reindex-file-ready'].topicPattern}",
    groupId = "#{folioKafkaProperties.listener['reindex-file-ready'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['reindex-file-ready'].concurrency}")
  public void handleReindexFileReadyEvent(ConsumerRecord<String, ReindexFileReadyEvent> consumerRecord) {
    log.debug("handleReindexFileReadyEvent::received reindex event [id={}]", consumerRecord.key());
    var event = consumerRecord.value();
    var eventInfo = fileReadyEventInfo(consumerRecord);
    scopedExecutionService.execute(event.getTenantId(),
      () -> executionService.execute(() -> jobEventFilter.shouldProcess(eventInfo) && reindexService.process(event)));
  }

  /**
   * File-ready events take the job identifier from the header set by mod-inventory-storage. Older
   * mod-inventory-storage versions do not set it, but they echo the request traceId, which mod-search sets to the
   * job identifier for every merge range, so the payload traceId is used as a fallback.
   */
  private static ReindexEventInfo fileReadyEventInfo(ConsumerRecord<String, ReindexFileReadyEvent> consumerRecord) {
    var event = consumerRecord.value();
    var headerJobId = getHeaderValue(consumerRecord, REINDEX_JOB_ID_HEADER);
    return headerJobId != null
           ? new ReindexEventInfo(FILE_READY_EVENT_TYPE, event.getTenantId(), event.getRangeId(), headerJobId,
             HEADER_SOURCE)
           : new ReindexEventInfo(FILE_READY_EVENT_TYPE, event.getTenantId(), event.getRangeId(), event.getTraceId(),
             "payload.traceId");
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
