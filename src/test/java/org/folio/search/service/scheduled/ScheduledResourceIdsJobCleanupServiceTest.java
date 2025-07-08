package org.folio.search.service.scheduled;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import org.folio.search.configuration.properties.StreamIdsProperties;
import org.folio.search.repository.ResourceIdsJobRepository;
import org.folio.search.repository.ResourceIdsTemporaryRepository;
import org.folio.search.service.reindex.jdbc.TenantRepository;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ScheduledResourceIdsJobCleanupServiceTest {

  private @Mock TenantRepository tenantRepository;
  private @Mock ResourceIdsJobRepository jobRepository;
  private @Mock ResourceIdsTemporaryRepository tempTableRepository;
  private @Mock SystemUserScopedExecutionService executionService;
  private @Mock StreamIdsProperties streamIdsProperties;

  private @InjectMocks ScheduledResourceIdsJobCleanupService cleanupService;

  @Test
  void cleanupExpiredResourceIdsJobs_shouldCleanUpExpiredJobs() {
    // Arrange
    var tenantIds = List.of("tenant1", "tenant2");
    var tableNames = List.of("temp_table_1", "temp_table_2");
    doAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call())
      .when(executionService).executeSystemUserScoped(anyString(), any());
    when(tenantRepository.fetchDataTenantIds()).thenReturn(tenantIds);
    when(streamIdsProperties.getJobExpirationDays()).thenReturn(7);
    when(jobRepository.deleteByCreatedDateLessThan(any(Date.class))).thenReturn(tableNames);

    // Act
    cleanupService.cleanupExpiredResourceIdsJobs();

    // Assert
    verify(tenantRepository).fetchDataTenantIds();
    verify(executionService, times(2)).executeSystemUserScoped(argThat(tenantIds::contains), any());
    verify(jobRepository, times(2)).deleteByCreatedDateLessThan(any(Date.class));
    verify(tempTableRepository, times(4)).dropTableForIds(argThat(tableNames::contains));
  }

  @Test
  void cleanupExpiredResourceIdsJobs_shouldHandleNoTenants() {
    // Arrange
    when(tenantRepository.fetchDataTenantIds()).thenReturn(Collections.emptyList());

    // Act
    cleanupService.cleanupExpiredResourceIdsJobs();

    // Assert
    verify(tenantRepository).fetchDataTenantIds();
    verifyNoInteractions(executionService, jobRepository, tempTableRepository);
  }
}
