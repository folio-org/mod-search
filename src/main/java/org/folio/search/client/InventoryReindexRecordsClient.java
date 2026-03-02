package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("inventory-reindex-records")
public interface InventoryReindexRecordsClient {

  static PublishReindexRecordsRequest constructRequest(String id, String recordType, String from, String to) {
    return new PublishReindexRecordsRequest(id, recordType, new ReindexRecordsRange(from, to));
  }

  static ExportReindexRecordsRequest constructRequest(String id, String traceId, String recordType,
                                                      String from, String to) {
    return new ExportReindexRecordsRequest(id, traceId, recordType, new ReindexRecordsRange(from, to));
  }

  @PostExchange(value = "/publish", accept = APPLICATION_JSON_VALUE)
  void publishReindexRecords(@RequestBody PublishReindexRecordsRequest publishReindexRecordsRequest);

  @PostExchange(value = "/export", accept = APPLICATION_JSON_VALUE)
  void exportReindexRecords(@RequestBody ExportReindexRecordsRequest publishReindexRecordsRequest);

  record PublishReindexRecordsRequest(
    String id,
    String recordType,
    ReindexRecordsRange recordIdsRange
  ) { }

  record ExportReindexRecordsRequest(
    String id,
    String traceId,
    String recordType,
    ReindexRecordsRange recordIdsRange
  ) { }

  record ReindexRecordsRange(String from, String to) { }
}
