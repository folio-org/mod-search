package org.folio.search.service.consortium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ConsortiumTenantExecutorTest {

  private static final String OPERATION_RESPONSE_MOCK = "test";

  @Mock
  private FolioExecutionContext folioExecutionContext;
  @Mock
  private TenantProvider tenantProvider;
  @Mock
  private SystemUserScopedExecutionService scopedExecutionService;
  @Spy
  @InjectMocks
  private ConsortiumTenantExecutor consortiumTenantExecutor;

  @Test
  void execute_positive_noConsortiaMode() {
    when(folioExecutionContext.getTenantId()).thenReturn(TENANT_ID);
    when(tenantProvider.getTenant(TENANT_ID)).thenReturn(TENANT_ID);

    var actual = consortiumTenantExecutor.execute(() -> OPERATION_RESPONSE_MOCK);

    assertThat(actual).isEqualTo(OPERATION_RESPONSE_MOCK);
    verifyNoInteractions(scopedExecutionService);
  }

  @Test
  void execute_positive_consortiaMode() {
    var operation = Mockito.spy(operation());

    when(folioExecutionContext.getTenantId()).thenReturn(TENANT_ID);
    when(tenantProvider.getTenant(TENANT_ID)).thenReturn(CENTRAL_TENANT_ID);
    doAnswer(invocationOnMock -> ((Callable<String>) invocationOnMock.getArgument(1)).call())
      .when(scopedExecutionService).executeSystemUserScoped(eq(CENTRAL_TENANT_ID), any());

    var actual = consortiumTenantExecutor.execute(operation);

    assertThat(actual).isEqualTo(OPERATION_RESPONSE_MOCK);
    verify(operation).get();
    verify(scopedExecutionService).executeSystemUserScoped(eq(CENTRAL_TENANT_ID), any());
  }

  @Test
  void execute_positive_consortiaModeForTenant() {
    var operation = Mockito.spy(operation());

    when(tenantProvider.getTenant(TENANT_ID)).thenReturn(CENTRAL_TENANT_ID);
    doAnswer(invocationOnMock -> ((Callable<String>) invocationOnMock.getArgument(1)).call())
      .when(scopedExecutionService).executeSystemUserScoped(eq(CENTRAL_TENANT_ID), any());

    var actual = consortiumTenantExecutor.execute(TENANT_ID, operation);

    assertThat(actual).isEqualTo(OPERATION_RESPONSE_MOCK);
    verify(operation).get();
    verify(scopedExecutionService).executeSystemUserScoped(eq(CENTRAL_TENANT_ID), any());
  }

  @Test
  void run_positive() {
    var operation = Mockito.spy(operationRunnable());

    when(folioExecutionContext.getTenantId()).thenReturn(TENANT_ID);
    when(tenantProvider.getTenant(TENANT_ID)).thenReturn(TENANT_ID);

    consortiumTenantExecutor.run(operation);

    verify(consortiumTenantExecutor).execute(any());
    verify(operation).run();
    verifyNoInteractions(scopedExecutionService);
  }

  private Supplier<String> operation() {
    return new Supplier<String>() {
      @Override
      public String get() {
        return OPERATION_RESPONSE_MOCK;
      }
    };
  }

  private Runnable operationRunnable() {
    return new Runnable() {
      @Override
      public void run() {
      }
    };
  }

}
