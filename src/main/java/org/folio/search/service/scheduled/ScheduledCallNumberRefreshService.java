package org.folio.search.service.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.search.service.EgressExecutionContextService;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.indexrebuild.derived.DerivedRefreshService;
import org.folio.search.service.reindex.jdbc.TenantRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "folio.search-config.indexing.call-number-index-from-items-enabled", havingValue = "true")
public class ScheduledCallNumberRefreshService {

  private final TenantRepository tenantRepository;
  private final EgressExecutionContextService executionService;
  private final DerivedRefreshService refreshService;

  @Scheduled(fixedDelayString = "#{searchConfigurationProperties.indexing.callNumberRefreshDelayMs}")
  public void refreshCallNumbers() {
    try {
      tenantRepository.fetchDataTenantIds().forEach(this::refresh);
    } catch (BadSqlGrammarException e) {
      log.warn("refreshCallNumbers:: Module upgrade may be in progress - database schema is outdated. Skipping cycle.");
    }
  }

  private void refresh(String tenant) {
    try {
      executionService.execute(tenant, () -> {
        refreshService.refresh(IndexRebuildTarget.CALL_NUMBER, tenant);
        return null;
      });
    } catch (RuntimeException e) {
      log.warn(new FormattedMessage("refresh:: failed to refresh call numbers [tenant: {}]", tenant), e);
    }
  }
}
