package org.folio.search.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import liquibase.exception.LiquibaseException;
import org.folio.search.service.TenantService;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.BeforeEach;
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

  @Mock private FolioSpringLiquibase liquibase;
  @Mock private FolioExecutionContext context;
  @Mock private TenantService tenantService;
  @InjectMocks private FolioTenantController tenantController;

  @BeforeEach
  void setUpStubs() {
    when(context.getTenantId()).thenReturn("tenant");
    when(context.getFolioModuleMetadata()).thenReturn(mock(FolioModuleMetadata.class));
    when(context.getFolioModuleMetadata().getDBSchemaName(any()))
      .thenReturn("db_schema");
  }

  @Test
  void postTenant_shouldCallTenantInitialize() {
    tenantController.postTenant(TENANT_ATTRIBUTES);

    verify(tenantService).initializeTenant();
  }

  @Test
  void postTenant_shouldNotCallTenantInitialize_liquibaseError() throws Exception {
    doThrow(new LiquibaseException()).when(liquibase).performLiquibaseUpdate();

    tenantController.postTenant(TENANT_ATTRIBUTES);

    verifyNoInteractions(tenantService);
  }
}
