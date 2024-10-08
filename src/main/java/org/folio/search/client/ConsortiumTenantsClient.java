package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("consortia")
public interface ConsortiumTenantsClient {

  /**
   * Get tenants by consortium id.
   *
   * @return consortium tenants if executed under consortium central 'tenantId' context
   * */
  @GetMapping(value = "/{consortiumId}/tenants", produces = APPLICATION_JSON_VALUE)
  ConsortiumTenants getConsortiumTenants(@PathVariable("consortiumId") String consortiumId,
                                         @RequestParam("limit") int limit);

  record ConsortiumTenants(List<ConsortiumTenant> tenants) { }

  record ConsortiumTenant(String id, boolean isCentral) { }
}
