package org.folio.search.service.consortium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.folio.search.client.ConsortiumTenantsClient;
import org.folio.search.client.UserTenantsClient;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ConsortiumTenantServiceTest {

  @Mock
  private UserTenantsClient userTenantsClient;
  @Mock
  private ConsortiumTenantsClient consortiumTenantsClient;
  @Mock
  private FolioExecutionContext context;
  @InjectMocks
  private ConsortiumTenantService consortiumTenantService;

  @Test
  void getCentralTenant_positive() {
    var userTenants = new UserTenantsClient.UserTenants(Collections.singletonList(
      new UserTenantsClient.UserTenant(CENTRAL_TENANT_ID, "consortiaId")));

    when(userTenantsClient.getUserTenants(TENANT_ID)).thenReturn(userTenants);

    var actual = consortiumTenantService.getCentralTenant(TENANT_ID);

    assertThat(actual)
      .isNotEmpty()
      .get()
      .isEqualTo(CENTRAL_TENANT_ID);
  }

  @Test
  void getCentralTenant_negative_emptyResponse() {
    when(userTenantsClient.getUserTenants(TENANT_ID)).thenReturn(null);

    var actual = consortiumTenantService.getCentralTenant(TENANT_ID);

    assertThat(actual)
      .isEmpty();
  }

  @Test
  void getCentralTenant_negative_noCentralTenant() {
    var userTenants = new UserTenantsClient.UserTenants(Collections.singletonList(
      new UserTenantsClient.UserTenant(null, "consortiaId")));

    when(userTenantsClient.getUserTenants(TENANT_ID)).thenReturn(userTenants);

    var actual = consortiumTenantService.getCentralTenant(TENANT_ID);

    assertThat(actual)
      .isEmpty();
  }
}
