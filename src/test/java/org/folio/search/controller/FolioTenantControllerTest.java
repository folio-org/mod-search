package org.folio.search.controller;

import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import liquibase.exception.LiquibaseException;
import org.folio.search.service.IndexService;
import org.folio.search.service.KafkaAdminService;
import org.folio.search.service.SearchTenantService;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.service.TenantService;
import org.folio.tenant.domain.dto.Parameter;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FolioTenantControllerTest {
  private static final TenantAttributes TENANT_ATTRIBUTES = new TenantAttributes()
    .moduleTo("mod-search-1.0.0");

  @Mock private SearchTenantService tenantService;
  @Mock private TenantService baseTenantService;
  @Mock private KafkaAdminService kafkaAdminService;
  @Mock private IndexService indexService;
  @Mock private FolioExecutionContext context;
  @InjectMocks private FolioTenantController tenantController;

  @Test
  void postTenant_shouldCallTenantInitialize() {
    tenantController.postTenant(TENANT_ATTRIBUTES);

    verify(tenantService).initializeTenant();
    verify(kafkaAdminService).createKafkaTopics();
    verify(kafkaAdminService).restartEventListeners();
  }

  @Test
  void postTenant_shouldNotCallTenantInitialize_liquibaseError() throws Exception {
    doThrow(new LiquibaseException()).when(baseTenantService).createTenant();

    tenantController.postTenant(TENANT_ATTRIBUTES);

    verifyNoInteractions(tenantService);
  }

  @Test
  void shouldRemoveElasticIndexOnTenantDelete() {
    tenantController.deleteTenant();

    verify(tenantService).removeElasticsearchIndexes();
  }

  @Test
  void shouldRunReindexOnTenantParamPresent() {
    tenantController.postTenant(TENANT_ATTRIBUTES.addParametersItem(new Parameter().key("runReindex").value("true")));
    verify(indexService).dropIndex(INSTANCE_RESOURCE, null);
    verify(indexService).createIndex(INSTANCE_RESOURCE, null);
  }

  @Test
  void shouldNotRunReindexOnTenantParamNotPresent() {
    tenantController.postTenant(TENANT_ATTRIBUTES);
    verifyNoInteractions(indexService);
  }
}
