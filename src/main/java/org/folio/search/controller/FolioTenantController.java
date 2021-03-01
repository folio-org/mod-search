package org.folio.search.controller;

import lombok.extern.log4j.Log4j2;
import org.folio.search.service.KafkaAdminService;
import org.folio.search.service.systemuser.SystemUserService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.controller.TenantController;
import org.folio.spring.liquibase.FolioSpringLiquibase;
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
  private final SystemUserService systemUserService;
  private final FolioExecutionContext executionContext;

  public FolioTenantController(
    FolioSpringLiquibase folioSpringLiquibase,
    FolioExecutionContext context,
    KafkaAdminService kafkaAdminService,
    SystemUserService systemUserService) {

    super(folioSpringLiquibase, context);
    this.kafkaAdminService = kafkaAdminService;
    this.systemUserService = systemUserService;
    this.executionContext = context;
  }

  @Override
  public ResponseEntity<String> postTenant(TenantAttributes tenantAttributes) {
    kafkaAdminService.createKafkaTopics();
    var response = super.postTenant(tenantAttributes);

    if (response.getStatusCode() == HttpStatus.OK) {
      systemUserService.prepareSystemUser(executionContext);
    }

    return response;
  }
}
