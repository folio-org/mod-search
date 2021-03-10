package org.folio.search.integration.inventory;

import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

import org.folio.search.client.cql.CqlQuery;
import org.folio.search.domain.dto.Instance;
import org.folio.search.model.service.ResultList;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("inventory-view")
public interface InventoryViewClient {

  /**
   * Retrieves resources by ids from inventory service.
   *
   * @param cql - CQL query
   * @return {@link ResultList} with Instance objects inside.
   */
  @GetMapping(path = "/instances?limit=100000", consumes = APPLICATION_OCTET_STREAM_VALUE)
  ResultList<Instance> getInstances(@RequestParam("query") CqlQuery cql);
}
