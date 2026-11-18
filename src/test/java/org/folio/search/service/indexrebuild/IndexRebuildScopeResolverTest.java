package org.folio.search.service.indexrebuild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IndexRebuildScopeResolverTest {

  private static final String CENTRAL = "central";
  private static final String MEMBER = "member";

  @Mock
  private ConsortiumTenantService consortiumTenantService;
  @InjectMocks
  private IndexRebuildScopeResolver resolver;

  @Test
  void resolve_positive_nonConsortiumTenant() {
    when(consortiumTenantService.getCentralTenant(CENTRAL)).thenReturn(Optional.empty());
    when(consortiumTenantService.getConsortiumTenants(CENTRAL)).thenReturn(List.of());

    assertThat(resolver.resolve(CENTRAL, null)).isEqualTo(new IndexRebuildScope(List.of(CENTRAL), true));
  }

  @Test
  void resolve_positive_wholeConsortium() {
    mockConsortium();

    assertThat(resolver.resolve(CENTRAL, null)).isEqualTo(new IndexRebuildScope(List.of(CENTRAL, MEMBER), true));
  }

  @Test
  void resolve_positive_singleMember() {
    mockConsortium();

    assertThat(resolver.resolve(CENTRAL, MEMBER)).isEqualTo(new IndexRebuildScope(List.of(MEMBER), false));
  }

  @Test
  void resolve_negative_requestFromMember() {
    when(consortiumTenantService.getCentralTenant(MEMBER)).thenReturn(Optional.of(CENTRAL));

    assertThatThrownBy(() -> resolver.resolve(MEMBER, null)).isInstanceOf(RequestValidationException.class);
  }

  @Test
  void resolve_negative_unknownTargetTenant() {
    mockConsortium();

    assertThatThrownBy(() -> resolver.resolve(CENTRAL, "unknown")).isInstanceOf(RequestValidationException.class);
  }

  @Test
  void resolve_negative_targetTenantWithoutConsortium() {
    when(consortiumTenantService.getCentralTenant(CENTRAL)).thenReturn(Optional.empty());
    when(consortiumTenantService.getConsortiumTenants(CENTRAL)).thenReturn(List.of());

    assertThatThrownBy(() -> resolver.resolve(CENTRAL, CENTRAL)).isInstanceOf(RequestValidationException.class);
  }

  private void mockConsortium() {
    when(consortiumTenantService.getCentralTenant(CENTRAL)).thenReturn(Optional.of(CENTRAL));
    when(consortiumTenantService.getConsortiumTenants(CENTRAL)).thenReturn(List.of(MEMBER));
  }
}
