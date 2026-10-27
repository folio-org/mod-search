package org.folio.search.repository;

import java.sql.Timestamp;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.entity.FailedIndexingEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Repository for failed indexing events stored in the tenant schema.
 *
 * <p>Table name is not schema-qualified: the folio {@code DataSource} wrapper sets {@code search_path} to the
 * current tenant schema for each connection.</p>
 */
@Repository
@RequiredArgsConstructor
public class FailedIndexingEventRepository {

  private static final String INSERT_SQL = """
    INSERT INTO failed_indexing_event (id, resource_type, resource_id, event_type, event, error_message, status,
                                       error_type, created_date)
    VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
    """;
  private static final String DELETE_OLDER_THAN_SQL = "DELETE FROM failed_indexing_event WHERE created_date < ?";

  private final JdbcTemplate jdbcTemplate;

  /**
   * Stores failed indexing event in the current tenant schema.
   *
   * @param failedEvent failed indexing event to store
   */
  public void save(FailedIndexingEvent failedEvent) {
    jdbcTemplate.update(INSERT_SQL,
      failedEvent.id(), failedEvent.resourceType(), failedEvent.resourceId(), failedEvent.eventType(),
      failedEvent.event(), failedEvent.errorMessage(), failedEvent.status(), failedEvent.errorType(),
      failedEvent.createdDate());
  }

  /**
   * Deletes failed indexing events created before the given date in the current tenant schema.
   *
   * @param threshold date before which events are deleted
   * @return number of deleted events
   */
  public int deleteOlderThan(Timestamp threshold) {
    return jdbcTemplate.update(DELETE_OLDER_THAN_SQL, threshold);
  }
}
