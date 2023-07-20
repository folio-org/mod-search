package org.folio.search.service.consortia;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.util.function.Supplier;
import org.folio.search.service.consortium.ConsortiaTenantExecutor;

public abstract class DecoratorBaseTest {

  protected void mockExecutorRun(ConsortiaTenantExecutor consortiaTenantExecutor) {
    doAnswer(invocationOnMock -> {
      ((Runnable) invocationOnMock.getArgument(0)).run();
      return null;
    }).when(consortiaTenantExecutor).run(any());
  }

  protected void mockExecutor(ConsortiaTenantExecutor consortiaTenantExecutor) {
    doAnswer(invocationOnMock -> ((Supplier<String>) invocationOnMock.getArgument(0)).get())
      .when(consortiaTenantExecutor).execute(any());
  }
}
