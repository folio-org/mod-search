package org.folio.search.service.scheduled;

import java.util.Date;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.StreamIdsProperties;
import org.folio.search.repository.ResourceIdsJobRepository;
import org.folio.search.repository.ResourceIdsTemporaryRepository;
import org.folio.search.service.reindex.jdbc.TenantRepository;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ScheduledResourceIdsJobCleanupService {

  private final TenantRepository tenantRepository;
  private final ResourceIdsJobRepository jobRepository;
  private final ResourceIdsTemporaryRepository tempTableRepository;
  private final SystemUserScopedExecutionService executionService;
  private final StreamIdsProperties streamIdsProperties;

  public ScheduledResourceIdsJobCleanupService(TenantRepository tenantRepository,
                                               ResourceIdsJobRepository jobRepository,
                                               ResourceIdsTemporaryRepository tempTableRepository,
                                               SystemUserScopedExecutionService executionService,
                                               StreamIdsProperties streamIdsProperties) {
    this.tenantRepository = tenantRepository;
    this.jobRepository = jobRepository;
    this.tempTableRepository = tempTableRepository;
    this.executionService = executionService;
    this.streamIdsProperties = streamIdsProperties;
  }

  /**
   * Cleans up expired resource ids jobs daily at midnight (00:00:00).
   */
  @Scheduled(cron = "0 0 0 * * *")
  public void cleanupExpiredResourceIdsJobs() {
    log.info("cleanupExpiredResourceIdsJobs:: Starting cleanup of expired resource ids jobs");
    tenantRepository.fetchDataTenantIds()
        .forEach(tenant -> executionService.executeSystemUserScoped(tenant, () -> {
          log.info("cleanupExpiredResourceIdsJobs:: Processing tenant: {}", tenant);
          var expirationThresholdDate = getJobExpirationThresholdDate();
          var tempTables = jobRepository.deleteByCreatedDateLessThan(expirationThresholdDate);
          tempTables.forEach(tempTable -> {
            log.info("cleanupExpiredResourceIdsJobs:: Dropping temporary table: {}", tempTable);
            tempTableRepository.dropTableForIds(tempTable);
          });
          return null;
        }));

    log.info("cleanupExpiredResourceIdsJobs:: Cleanup completed");
  }

  private Date getJobExpirationThresholdDate() {
    return new Date(System.currentTimeMillis() - streamIdsProperties.getJobExpirationDays() * 24L * 60 * 60 * 1000);
  }
}
