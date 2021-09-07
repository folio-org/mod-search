package org.folio.search.controller;

import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import java.util.Collection;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.folio.search.service.IndexService;
import org.folio.search.service.KafkaAdminService;
import org.folio.search.service.SearchTenantService;
import org.folio.spring.FolioExecutionContext;
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

  private static final String REINDEX_PARAM_NAME = "runReindex";

  private final KafkaAdminService kafkaAdminService;
  private final SearchTenantService tenantService;
  private final IndexService indexService;
  private final FolioExecutionContext context;


  public FolioTenantController(TenantService baseTenantService,
                               KafkaAdminService kafkaAdminService, SearchTenantService tenantService,
                               IndexService indexService, FolioExecutionContext context) {

    super(baseTenantService);
    this.kafkaAdminService = kafkaAdminService;
    this.tenantService = tenantService;
    this.indexService = indexService;
    this.context = context;
  }

  @Override
  public ResponseEntity<String> postTenant(TenantAttributes tenantAttributes) {
    kafkaAdminService.createKafkaTopics();
    kafkaAdminService.restartEventListeners();

    var tenantInit = super.postTenant(tenantAttributes);

    if (tenantInit.getStatusCode() == HttpStatus.OK) {
      tenantService.initializeTenant();

      Stream.ofNullable(tenantAttributes.getParameters()).flatMap(Collection::stream)
        .filter(parameter -> parameter.getKey().equals(REINDEX_PARAM_NAME)
          && Boolean.parseBoolean(parameter.getValue()))
        .findFirst().ifPresent(parameter -> {
          indexService.dropIndex(INSTANCE_RESOURCE, context.getTenantId());
          indexService.createIndex(INSTANCE_RESOURCE, context.getTenantId());
        });
    }

    log.info("Tenant init has been completed [response={}]", tenantInit);
    return tenantInit;
  }

  @Override
  public ResponseEntity<Void> deleteTenant() {
    var deleteResponse = super.deleteTenant();

    tenantService.removeElasticsearchIndexes();
    return deleteResponse;
  }
}
