package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.net.URI;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient("instance-storage")
public interface InventoryInstanceClient {

  /**
   * Retrieves Inventory Records count with given URI.
   *
   * @param uri URI to retrieve count of inventory record
   * @return count represented as {@link InventoryRecordsCountDto}
   */
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  InventoryRecordsCountDto getInventoryRecordsCount(URI uri);

  record InventoryRecordsCountDto(int totalRecords) { }
}
