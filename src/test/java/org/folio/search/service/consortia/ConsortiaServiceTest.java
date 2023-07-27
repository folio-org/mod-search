package org.folio.search.service.consortia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.CONSORTIUM_TENANT_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.folio.search.client.UserTenantsClient;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsortiaServiceTest {

  @Mock
  private UserTenantsClient userTenantsClient;
  @InjectMocks
  private ConsortiumTenantService consortiumTenantService;

  @Test
  void getCentralTenant_positive() {
    var userTenants = new UserTenantsClient.UserTenants(Collections.singletonList(
      new UserTenantsClient.UserTenant(CONSORTIUM_TENANT_ID)));

    when(userTenantsClient.getUserTenants(TENANT_ID)).thenReturn(userTenants);

    var actual = consortiumTenantService.getCentralTenant(TENANT_ID);

    assertThat(actual)
      .isNotEmpty()
      .get()
      .isEqualTo(CONSORTIUM_TENANT_ID);
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
      new UserTenantsClient.UserTenant(null)));

    when(userTenantsClient.getUserTenants(TENANT_ID)).thenReturn(userTenants);

    var actual = consortiumTenantService.getCentralTenant(TENANT_ID);

    assertThat(actual)
      .isEmpty();
  }

}
