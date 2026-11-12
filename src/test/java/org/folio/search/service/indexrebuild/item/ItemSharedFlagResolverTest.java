package org.folio.search.service.indexrebuild.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ItemSharedFlagResolverTest {

  @Mock
  private ConsortiumTenantProvider consortiumTenantProvider;
  @InjectMocks
  private ItemSharedFlagResolver resolver;

  @Test
  void isShared_positive_consortiumInstanceSource() {
    assertThat(resolver.isShared(TENANT_ID, Map.of("instanceSource", "CONSORTIUM-MARC"))).isTrue();
  }

  @Test
  void isShared_positive_centralTenantHasNoPrefix() {
    when(consortiumTenantProvider.isCentralTenant(TENANT_ID)).thenReturn(true);

    assertThat(resolver.isShared(TENANT_ID, Map.of("instanceSource", "MARC"))).isTrue();
  }

  @Test
  void isShared_negative_localInstanceOfMemberOrNonConsortiumTenant() {
    assertThat(resolver.isShared(TENANT_ID, Map.of("instanceSource", "FOLIO"))).isFalse();
  }

  @Test
  void isShared_negative_noInstanceSource() {
    assertThat(resolver.isShared(TENANT_ID, Map.of())).isFalse();
  }
}
