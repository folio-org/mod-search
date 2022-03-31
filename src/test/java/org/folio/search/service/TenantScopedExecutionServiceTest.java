package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.util.concurrent.Callable;
import org.folio.search.configuration.properties.ModuleConfigurationProperties;
import org.folio.search.service.systemuser.ModuleUserProvider;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TenantScopedExecutionServiceTest {

  @InjectMocks private TenantScopedExecutionService tenantScopedExecutionService;
  @Mock private ModuleUserProvider moduleUserProvider;
  @Mock private ModuleConfigurationProperties moduleConfigurationProperties;

  @Test
  void executeTenantScoped_positive() {
    when(moduleUserProvider.getOkapiToken(TENANT_ID)).thenReturn("okapiToken");
    when(moduleConfigurationProperties.getOkapiUrl()).thenReturn("http://localhost:8000");
    var actual = tenantScopedExecutionService.executeTenantScoped(TENANT_ID, () -> "result");
    assertThat(actual).isEqualTo("result");
  }

  @Test
  void executeTenantScoped_negative_throwsException() {
    when(moduleUserProvider.getOkapiToken(TENANT_ID)).thenReturn("okapiToken");
    when(moduleConfigurationProperties.getOkapiUrl()).thenReturn("http://localhost:8000");

    Callable<Object> callable = () -> {
      throw new Exception("error");
    };

    assertThatThrownBy(() -> tenantScopedExecutionService.executeTenantScoped(TENANT_ID, callable))
      .isInstanceOf(Exception.class)
      .hasMessage("error");
  }
}
