package org.folio.search.controller;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import liquibase.exception.LiquibaseException;
import org.folio.search.service.KafkaAdminService;
import org.folio.search.service.SearchTenantService;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.exception.TenantUpgradeException;
import org.folio.spring.service.TenantService;
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
  @InjectMocks private FolioTenantController tenantController;

  @Test
  void postTenant_positive_upgradeTenant() {
    tenantController.postTenant(TENANT_ATTRIBUTES);

    verify(tenantService).initializeTenant(TENANT_ATTRIBUTES);
    verify(kafkaAdminService).createKafkaTopics();
    verify(kafkaAdminService).restartEventListeners();
  }

  @Test
  void postTenant_negative_upgradeTenatnWithLiquibaseError() {
    doThrow(new TenantUpgradeException(new LiquibaseException("error"))).when(baseTenantService).createTenant();

    assertThatThrownBy(() -> tenantController.postTenant(TENANT_ATTRIBUTES))
      .isInstanceOf(TenantUpgradeException.class)
      .hasMessage("liquibase.exception.LiquibaseException: error");

    verifyNoInteractions(tenantService);
  }

  @Test
  void postTenant_positive_disableTenant() {
    tenantController.postTenant(new TenantAttributes().moduleFrom("mod-search").purge(true));
    verify(baseTenantService).deleteTenant();
    verify(tenantService).removeElasticsearchIndexes();
  }
}
