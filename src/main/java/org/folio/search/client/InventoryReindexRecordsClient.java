package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("inventory-reindex-records")
public interface InventoryReindexRecordsClient {

  static ReindexRecordsRequest constructRequest(String id, String recordType, String from, String to) {
    return new ReindexRecordsRequest(id, recordType, new ReindexRecordsRange(from, to));
  }

  @HttpExchange(value = "/publish", accept = APPLICATION_JSON_VALUE)
  void publishReindexRecords(@RequestBody ReindexRecordsRequest reindexRecordsRequest);

  record ReindexRecordsRequest(String id, String recordType, ReindexRecordsRange recordIdsRange) { }

  record ReindexRecordsRange(String from, String to) { }
}
