package org.folio.search.service.consortia;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.util.function.Supplier;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;

public abstract class DecoratorBaseTest {

  protected void mockExecutorRun(ConsortiumTenantExecutor consortiumTenantExecutor) {
    doAnswer(invocationOnMock -> {
      ((Runnable) invocationOnMock.getArgument(0)).run();
      return null;
    }).when(consortiumTenantExecutor).run(any());
  }

  @SuppressWarnings("unchecked")
  protected void mockExecutor(ConsortiumTenantExecutor consortiumTenantExecutor) {
    doAnswer(invocationOnMock -> ((Supplier<String>) invocationOnMock.getArgument(0)).get())
      .when(consortiumTenantExecutor).execute(any());
  }
}
