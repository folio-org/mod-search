package org.folio.search.client;

import org.folio.search.client.cql.CqlQuery;
import org.folio.search.model.service.ReferenceRecord;
import org.folio.search.model.service.ResultList;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("identifier-types")
public interface IdentifierTypeClient {
  @GetMapping
  ResultList<ReferenceRecord> getIdentifierTypes(@RequestParam("query") CqlQuery query);
}
