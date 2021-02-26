package org.folio.search.controller;

import lombok.extern.log4j.Log4j2;
import org.folio.search.service.KafkaAdminService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.controller.TenantController;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RequestMapping(value = "/_/")
@RestController("folioTenantController")
public class FolioTenantController extends TenantController {

  private final KafkaAdminService kafkaAdminService;

  public FolioTenantController(
    FolioSpringLiquibase folioSpringLiquibase,
    FolioExecutionContext context,
    KafkaAdminService kafkaAdminService) {
    super(folioSpringLiquibase, context);
    this.kafkaAdminService = kafkaAdminService;
  }

  @Override
  public ResponseEntity<String> postTenant(TenantAttributes tenantAttributes) {
    kafkaAdminService.createKafkaTopics();
    return super.postTenant(tenantAttributes);
  }
}
