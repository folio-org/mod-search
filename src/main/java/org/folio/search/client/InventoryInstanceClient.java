package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.net.URI;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface InventoryInstanceClient {

  /**
   * Retrieves Inventory Records count with given URI.
   *
   * @param uri URI to retrieve count of inventory record
   * @return count represented as {@link InventoryRecordsCountDto}
   */
  @GetExchange(accept = APPLICATION_JSON_VALUE)
  InventoryRecordsCountDto getInventoryRecordsCount(URI uri);

  record InventoryRecordsCountDto(int totalRecords) { }
}
