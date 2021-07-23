package org.folio.search.client;

import org.folio.search.client.cql.CqlQuery;
import org.folio.search.model.service.ReferenceRecord;
import org.folio.search.model.service.ResultList;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("alternative-title-types")
public interface AlternativeTitleTypesClient {

  /**
   * Fetches alternative title types by CQL query.
   *
   * @param query - CQL query
   * @return result list with fetched reference records
   */
  @GetMapping
  ResultList<ReferenceRecord> getAlternativeTitleTypes(@RequestParam("query") CqlQuery query);
}
