package org.folio.search.controller;

import org.folio.search.service.TenantService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.controller.TenantController;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping(value = "/_/")
@RestController("folioTenantController")
public class FolioTenantController extends TenantController {
  private final TenantService tenantService;

  public FolioTenantController(FolioSpringLiquibase folioSpringLiquibase,
    FolioExecutionContext context, TenantService tenantService) {

    super(folioSpringLiquibase, context);
    this.tenantService = tenantService;
  }

  @Override
  public ResponseEntity<String> postTenant(TenantAttributes tenantAttributes) {
    var tenantInit = super.postTenant(tenantAttributes);

    if (tenantInit.getStatusCode() == HttpStatus.OK) {
      tenantService.initializeTenant();
    }

    return tenantInit;
  }
}
