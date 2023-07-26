package org.folio.search.service.consortia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.CONSORTIUM_TENANT_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsortiumTenantProviderTest {

  @Mock
  private ConsortiumTenantService consortiumTenantService;
  @InjectMocks
  private ConsortiumTenantProvider consortiumTenantProvider;

  @Test
  void getTenant_positive() {
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CONSORTIUM_TENANT_ID));

    var actual = consortiumTenantProvider.getTenant(TENANT_ID);

    assertThat(actual)
      .isEqualTo(CONSORTIUM_TENANT_ID);
  }

  @Test
  void getTenant_negative_emptyResponse() {
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());

    var actual = consortiumTenantProvider.getTenant(TENANT_ID);

    assertThat(actual)
      .isEqualTo(TENANT_ID);
  }

}
