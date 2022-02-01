package org.folio.search.controller;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.service.KafkaAdminService;
import org.folio.search.service.SearchTenantService;
import org.folio.spring.controller.TenantController;
import org.folio.spring.service.TenantService;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController("folioTenantController")
public class FolioTenantController extends TenantController {

  private final KafkaAdminService kafkaAdminService;
  private final SearchTenantService tenantService;

  public FolioTenantController(TenantService baseTenantService, KafkaAdminService kafkaAdminService,
    SearchTenantService tenantService) {
    super(baseTenantService);
    this.kafkaAdminService = kafkaAdminService;
    this.tenantService = tenantService;
  }

  @Override
  public ResponseEntity<Void> postTenant(TenantAttributes tenantAttributes) {
    var tenantInit = super.postTenant(tenantAttributes);

    if (!isDeleteJob(tenantAttributes)) {
      kafkaAdminService.createKafkaTopics();
      kafkaAdminService.restartEventListeners();
      tenantService.initializeTenant(tenantAttributes);
    }

    log.info("Tenant init has been completed [response={}]", tenantInit);
    return tenantInit;
  }

  @Override
  public void disableTenant() {
    super.disableTenant();
    tenantService.removeElasticsearchIndexes();
  }

  private static boolean isDeleteJob(TenantAttributes tenantAttributes) {
    return StringUtils.isBlank(tenantAttributes.getModuleTo()) && tenantAttributes.getPurge();
  }
}
