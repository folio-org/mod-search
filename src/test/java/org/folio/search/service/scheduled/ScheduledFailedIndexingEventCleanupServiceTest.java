package org.folio.search.service.scheduled;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.Callable;
import org.folio.search.service.EgressExecutionContextService;
import org.folio.search.service.FailedIndexingEventService;
import org.folio.search.service.reindex.jdbc.TenantRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ScheduledFailedIndexingEventCleanupServiceTest {

  private @Mock TenantRepository tenantRepository;
  private @Mock FailedIndexingEventService failedIndexingEventService;
  private @Mock EgressExecutionContextService executionService;

  private @InjectMocks ScheduledFailedIndexingEventCleanupService cleanupService;

  @Test
  void cleanupExpiredFailedIndexingEvents_positive_deletesForEachTenant() {
    when(tenantRepository.fetchDataTenantIds()).thenReturn(List.of("tenant1", "tenant2"));
    doAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call())
      .when(executionService).execute(anyString(), any(Callable.class));

    cleanupService.cleanupExpiredFailedIndexingEvents();

    verify(executionService).execute(eq("tenant1"), any(Callable.class));
    verify(executionService).execute(eq("tenant2"), any(Callable.class));
    verify(failedIndexingEventService, times(2)).deleteExpired();
  }

  @Test
  void cleanupExpiredFailedIndexingEvents_positive_noTenants() {
    when(tenantRepository.fetchDataTenantIds()).thenReturn(List.of());

    cleanupService.cleanupExpiredFailedIndexingEvents();

    verifyNoInteractions(executionService, failedIndexingEventService);
  }
}
