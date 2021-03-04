package org.folio.search.controller;

import lombok.extern.log4j.Log4j2;
import org.folio.search.service.KafkaAdminService;
import org.folio.search.service.SearchTenantService;
import org.folio.spring.controller.TenantController;
import org.folio.spring.service.TenantService;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RequestMapping(value = "/_/")
@RestController("folioTenantController")
public class FolioTenantController extends TenantController {

  private final KafkaAdminService kafkaAdminService;
  private final SearchTenantService tenantService;

  public FolioTenantController(TenantService baseTenantService,
    KafkaAdminService kafkaAdminService, SearchTenantService tenantService) {

    super(baseTenantService);
    this.kafkaAdminService = kafkaAdminService;
    this.tenantService = tenantService;
  }

  @Override
  public ResponseEntity<String> postTenant(TenantAttributes tenantAttributes) {
    kafkaAdminService.createKafkaTopics();
    var tenantInit = super.postTenant(tenantAttributes);

    if (tenantInit.getStatusCode() == HttpStatus.OK) {
      tenantService.initializeTenant();
    }

    return tenantInit;
  }
}
