package org.folio.search.service;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.util.concurrent.Callable;
import org.folio.search.model.SystemUser;
import org.folio.search.service.context.FolioExecutionContextBuilder;
import org.folio.search.service.systemuser.SystemUserService;
import org.folio.search.utils.types.UnitTest;
import org.folio.spring.DefaultFolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TenantScopedExecutionServiceTest {

  @InjectMocks private TenantScopedExecutionService tenantScopedExecutionService;
  @Mock private FolioExecutionContextBuilder contextBuilder;
  @Mock private SystemUserService systemUserService;

  @Test
  void executeTenantScoped_positive() {
    var systemUser = SystemUser.builder().build();
    when(systemUserService.getSystemUser(TENANT_ID)).thenReturn(systemUser);
    when(contextBuilder.forSystemUser(systemUser)).thenReturn(new DefaultFolioExecutionContext(null, emptyMap()));

    var actual = tenantScopedExecutionService.executeTenantScoped(TENANT_ID, () -> "result");

    assertThat(actual).isEqualTo("result");
  }

  @Test
  void executeTenantScoped_negative_throwsException() {
    var systemUser = SystemUser.builder().build();
    when(systemUserService.getSystemUser(TENANT_ID)).thenReturn(systemUser);
    when(contextBuilder.forSystemUser(systemUser)).thenReturn(new DefaultFolioExecutionContext(null, emptyMap()));

    Callable<Object> callable = () -> {
      throw new Exception("error");
    };

    assertThatThrownBy(() -> tenantScopedExecutionService.executeTenantScoped(TENANT_ID, callable))
      .isInstanceOf(Exception.class)
      .hasMessage("error");
  }
}
