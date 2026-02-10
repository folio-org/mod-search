package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("user-tenants")
public interface UserTenantsClient {

  @GetExchange(accept = APPLICATION_JSON_VALUE)
  UserTenants getUserTenants(@RequestParam("tenantId") String tenantId);

  record UserTenants(List<UserTenant> userTenants) { }

  record UserTenant(String centralTenantId, String consortiumId) { }
}
