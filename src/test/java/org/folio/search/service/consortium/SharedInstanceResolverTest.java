package org.folio.search.service.consortium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.function.Supplier;
import org.folio.search.service.reindex.jdbc.MergeInstanceRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SharedInstanceResolverTest {

  private static final Set<String> INSTANCE_IDS = Set.of("id1", "id2");

  @Mock
  private MergeInstanceRepository mergeInstanceRepository;
  @Mock
  private ConsortiumTenantProvider consortiumTenantProvider;
  @Mock
  private ConsortiumTenantExecutor consortiumTenantExecutor;
  @InjectMocks
  private SharedInstanceResolver resolver;

  @Test
  void findSharedInstanceIds_positive_nonConsortiumTenant() {
    when(consortiumTenantProvider.isConsortiumTenant(TENANT_ID)).thenReturn(false);

    var actual = resolver.findSharedInstanceIds(TENANT_ID, INSTANCE_IDS);

    assertThat(actual).isEmpty();
    verifyNoInteractions(mergeInstanceRepository);
  }

  @Test
  void findSharedInstanceIds_positive_centralTenantSkipsLookup() {
    when(consortiumTenantProvider.isConsortiumTenant(CENTRAL_TENANT_ID)).thenReturn(true);
    when(consortiumTenantProvider.isCentralTenant(CENTRAL_TENANT_ID)).thenReturn(true);

    var actual = resolver.findSharedInstanceIds(CENTRAL_TENANT_ID, INSTANCE_IDS);

    assertThat(actual).isEqualTo(INSTANCE_IDS);
    verifyNoInteractions(mergeInstanceRepository);
  }

  @Test
  void findSharedInstanceIds_positive_memberTenantLooksUpInCentralContext() {
    when(consortiumTenantProvider.isConsortiumTenant(MEMBER_TENANT_ID)).thenReturn(true);
    when(consortiumTenantProvider.isCentralTenant(MEMBER_TENANT_ID)).thenReturn(false);
    when(mergeInstanceRepository.findSharedInstanceIds(INSTANCE_IDS)).thenReturn(Set.of("id1"));
    when(consortiumTenantExecutor.execute(eq(MEMBER_TENANT_ID), any(Supplier.class)))
      .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());

    var actual = resolver.findSharedInstanceIds(MEMBER_TENANT_ID, INSTANCE_IDS);

    assertThat(actual).containsExactly("id1");
  }
}
