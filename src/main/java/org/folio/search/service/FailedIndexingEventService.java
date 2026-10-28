package org.folio.search.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.entity.FailedIndexingEvent;
import org.folio.search.repository.FailedIndexingEventRepository;
import org.folio.search.utils.IndexingFailureUtils;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Service;

/**
 * Stores resource events that failed to be indexed and cleans up the outdated ones.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class FailedIndexingEventService {

  private final FailedIndexingEventRepository repository;
  private final JsonConverter jsonConverter;
  private final SearchConfigurationProperties searchConfigurationProperties;

  /**
   * Stores failed resource event with the failure details (full cause chain, OpenSearch status and error type) in the
   * current tenant schema. Never throws, so a storage
   * failure does not break event processing.
   *
   * @param event     resource event that failed to be indexed
   * @param exception failure reason
   */
  public void save(ResourceEvent event, Exception exception) {
    try {
      var eventType = event.getType() != null ? event.getType().getValue() : null;
      repository.save(new FailedIndexingEvent(UUID.randomUUID(), event.getResourceName(), event.getId(), eventType,
        jsonConverter.toJson(event), IndexingFailureUtils.getFullMessage(exception),
        IndexingFailureUtils.getStatus(exception), IndexingFailureUtils.getErrorType(exception),
        Timestamp.from(Instant.now())));
    } catch (Exception e) {
      log.warn("save:: Failed to store failed indexing event [resource: {}, id: {}, tenant: {}]",
        event.getResourceName(), event.getId(), event.getTenant(), e);
    }
  }

  /**
   * Deletes failed indexing events older than the configured retention period in the current tenant schema.
   *
   * @return number of deleted events
   */
  public int deleteExpired() {
    var retentionDays = searchConfigurationProperties.getIndexing().getFailedEventsRetentionDays();
    var threshold = Timestamp.from(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
    return repository.deleteOlderThan(threshold);
  }
}
