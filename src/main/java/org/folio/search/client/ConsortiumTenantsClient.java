package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("consortia")
public interface ConsortiumTenantsClient {

  /**
   * Get tenants by consortium id.
   *
   * @return consortium tenants if executed under consortium central 'tenantId' context
   *
   */
  @GetExchange(value = "/{consortiumId}/tenants", accept = APPLICATION_JSON_VALUE)
  ConsortiumTenants getConsortiumTenants(@PathVariable String consortiumId, @RequestParam("limit") int limit);

  record ConsortiumTenants(List<ConsortiumTenant> tenants) { }

  record ConsortiumTenant(String id, boolean isCentral) { }
}
