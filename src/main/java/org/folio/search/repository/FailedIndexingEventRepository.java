package org.folio.search.repository;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.sql.Timestamp;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.entity.FailedIndexingEvent;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FailedIndexingEventRepository {

  static final String TABLE_NAME = "failed_indexing_event";

  private static final String INSERT_SQL = """
    INSERT INTO %s (id, resource_type, resource_id, event_type, event, error_message, status, error_type,
                    created_date)
    VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
    """;
  private static final String DELETE_OLDER_THAN_SQL = "DELETE FROM %s WHERE created_date < ?";

  private final JdbcTemplate jdbcTemplate;
  private final FolioExecutionContext context;

  /**
   * Stores failed indexing event in the current tenant schema.
   *
   * @param failedEvent failed indexing event to store
   */
  public void save(FailedIndexingEvent failedEvent) {
    jdbcTemplate.update(INSERT_SQL.formatted(getFullTableName(context, TABLE_NAME)),
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
    return jdbcTemplate.update(DELETE_OLDER_THAN_SQL.formatted(getFullTableName(context, TABLE_NAME)), threshold);
  }
}
