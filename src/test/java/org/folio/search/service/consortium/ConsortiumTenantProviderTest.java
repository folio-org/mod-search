package org.folio.search.service.consortium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ConsortiumTenantProviderTest {

  @Mock
  private ConsortiumTenantService consortiumTenantService;
  @InjectMocks
  private ConsortiumTenantProvider consortiumTenantProvider;

  @Test
  void getTenant_positive() {
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CENTRAL_TENANT_ID));

    var actual = consortiumTenantProvider.getTenant(TENANT_ID);

    assertThat(actual)
      .isEqualTo(CENTRAL_TENANT_ID);
  }

  @Test
  void getTenant_negative_emptyResponse() {
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());

    var actual = consortiumTenantProvider.getTenant(TENANT_ID);

    assertThat(actual)
      .isEqualTo(TENANT_ID);
  }

}
