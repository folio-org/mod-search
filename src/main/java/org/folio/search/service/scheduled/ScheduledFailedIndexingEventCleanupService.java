package org.folio.search.service.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.service.EgressExecutionContextService;
import org.folio.search.service.FailedIndexingEventService;
import org.folio.search.service.reindex.jdbc.TenantRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ScheduledFailedIndexingEventCleanupService {

  private final TenantRepository tenantRepository;
  private final FailedIndexingEventService failedIndexingEventService;
  private final EgressExecutionContextService executionService;

  /**
   * Cleans up failed indexing events older than the retention period daily at 00:30:00.
   */
  @Scheduled(cron = "0 30 0 * * *")
  public void cleanupExpiredFailedIndexingEvents() {
    log.info("cleanupExpiredFailedIndexingEvents:: Starting cleanup of expired failed indexing events");
    tenantRepository.fetchDataTenantIds()
      .forEach(tenant -> executionService.execute(tenant, () -> {
        var deleted = failedIndexingEventService.deleteExpired();
        log.info("cleanupExpiredFailedIndexingEvents:: Deleted {} events [tenant: {}]", deleted, tenant);
        return null;
      }));
    log.info("cleanupExpiredFailedIndexingEvents:: Cleanup completed");
  }
}
