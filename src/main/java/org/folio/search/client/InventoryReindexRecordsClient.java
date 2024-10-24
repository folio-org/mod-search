package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient("inventory-reindex-records")
public interface InventoryReindexRecordsClient {

  @PostMapping(path = "/publish", consumes = APPLICATION_JSON_VALUE)
  void publishReindexRecords(ReindexRecordsRequest reindexRecordsRequest);

  static ReindexRecordsRequest constructRequest(String id, String recordType, String from, String to) {
    return new ReindexRecordsRequest(id, recordType, new ReindexRecordsRange(from, to));
  }

  record ReindexRecordsRequest(String id, String recordType, ReindexRecordsRange recordIdsRange) { }

  record ReindexRecordsRange(String from, String to) { }
}
