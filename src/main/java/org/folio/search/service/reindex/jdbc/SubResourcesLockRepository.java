package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getSchemaName;

import java.sql.Timestamp;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.spring.FolioModuleMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SubResourcesLockRepository {

  private static final String LOCK_SUB_RESOURCE_SQL = """
    UPDATE %s.sub_resources_lock
    SET locked_flag = TRUE
    WHERE entity_type = ? AND locked_flag = FALSE
    RETURNING last_updated_date
    """;

  /**
   * Scheduler-side unlock with fencing. Releases the lock only if {@code expectedLastUpdatedDate}
   * still matches the row, so a lock taken over by a reindex via {@link #forceLockAllForReindex(String)}
   * is not cleared by the scheduler.
   */
  private static final String UNLOCK_SUB_RESOURCE_FENCED_SQL = """
    UPDATE %s.sub_resources_lock
    SET locked_flag = FALSE, last_updated_date = ?
    WHERE entity_type = ? AND last_updated_date = ?
    """;

  /**
   * Scheduler-side heartbeat with fencing semantics, see {@link #UNLOCK_SUB_RESOURCE_FENCED_SQL}.
   */
  private static final String UPDATE_LOCK_TIMESTAMP_FENCED_SQL = """
    UPDATE %s.sub_resources_lock
    SET last_updated_date = ?
    WHERE entity_type = ? AND locked_flag = TRUE AND last_updated_date = ?
    """;

  private static final String RELEASE_STALE_LOCK_SQL = """
    UPDATE %s.sub_resources_lock
    SET locked_flag = FALSE
    WHERE entity_type = ?
      AND locked_flag = TRUE
      AND last_updated_date < ?
    """;

  /**
   * Reindex-side unconditional lock of all entity types. Bumps {@code last_updated_date}
   * so any in-flight scheduler cycle holding a stale fencing token is preempted.
   */
  private static final String FORCE_LOCK_ALL_SUB_RESOURCES_SQL = """
    UPDATE %s.sub_resources_lock
    SET locked_flag = TRUE, last_updated_date = ?
    """;

  private final JdbcTemplate jdbcTemplate;
  private final FolioModuleMetadata moduleMetadata;

  public Optional<Timestamp> lockSubResource(ReindexEntityType entityType, String tenantId) {
    var formattedSql = formatSqlWithSchema(LOCK_SUB_RESOURCE_SQL, tenantId);
    return jdbcTemplate.query(
      formattedSql,
      rs -> rs.next() ? Optional.of(rs.getTimestamp(1)) : Optional.empty(),
      entityType.getType()
    );
  }

  /**
   * Releases the lock from the scheduler side, but only if the row has not been
   * preempted by a reindex (fencing token check on {@code expectedLastUpdatedDate}).
   *
   * @return {@code true} if the lock was released, {@code false} if it had been taken over
   *     (typically by {@link #forceLockAllForReindex(String)} during a reindex).
   */
  public boolean unlockSubResourceFenced(ReindexEntityType entityType, Timestamp lastUpdatedDate, String tenantId,
                                         Timestamp expectedLastUpdatedDate) {
    var formattedSql = formatSqlWithSchema(UNLOCK_SUB_RESOURCE_FENCED_SQL, tenantId);
    var rowsAffected = jdbcTemplate.update(formattedSql, lastUpdatedDate, entityType.getType(),
      expectedLastUpdatedDate);
    return rowsAffected > 0;
  }

  /**
   * Updates the lock timestamp for a sub-resource without releasing the lock, using a fencing
   * token. This prevents the lock from appearing stale during long-running batch processing
   * while also allowing the scheduler to detect preemption by a reindex
   * (see {@link #forceLockAllForReindex(String)}).
   *
   * @param entityType the type of entity being processed
   * @param lastUpdatedDate the new timestamp to set
   * @param tenantId the tenant identifier
   * @param expectedLastUpdatedDate the timestamp value the caller previously saw / wrote
   * @return {@code true} if the timestamp was refreshed, {@code false} if the lock was
   *     preempted (caller should abort its current cycle without releasing the lock).
   */
  public boolean updateLockTimestampFenced(ReindexEntityType entityType, Timestamp lastUpdatedDate, String tenantId,
                                           Timestamp expectedLastUpdatedDate) {
    var formattedSql = formatSqlWithSchema(UPDATE_LOCK_TIMESTAMP_FENCED_SQL, tenantId);
    var rowsAffected = jdbcTemplate.update(formattedSql, lastUpdatedDate, entityType.getType(),
      expectedLastUpdatedDate);
    return rowsAffected > 0;
  }

  /**
   * Checks for and releases a stale lock if it exists.
   * A lock is considered stale if its last_updated_date is older than the specified threshold.
   * This handles the case where an application instance crashes while holding a lock.
   *
   * @param entityType the type of entity to check
   * @param tenantId the tenant identifier
   * @param thresholdMs the age threshold in milliseconds for considering a lock stale
   * @return true if a stale lock was released, false otherwise
   */
  public boolean checkAndReleaseStaleLock(ReindexEntityType entityType, String tenantId, long thresholdMs) {
    var formattedSql = formatSqlWithSchema(RELEASE_STALE_LOCK_SQL, tenantId);
    var staleThresholdTimestamp = new Timestamp(System.currentTimeMillis() - thresholdMs);

    var rowsAffected = jdbcTemplate.update(formattedSql, entityType.getType(), staleThresholdTimestamp);
    return rowsAffected > 0;
  }

  /**
   * Reindex-side: unconditionally locks all sub-resource rows for the given tenant and
   * advances {@code last_updated_date}. The timestamp bump invalidates any fencing token
   * held by an in-flight scheduler cycle so it will abort instead of clearing the lock.
   *
   * @param tenantId the tenant identifier
   */
  public void forceLockAllForReindex(String tenantId) {
    var formattedSql = formatSqlWithSchema(FORCE_LOCK_ALL_SUB_RESOURCES_SQL, tenantId);
    jdbcTemplate.update(formattedSql, new Timestamp(System.currentTimeMillis()));
  }

  private String formatSqlWithSchema(String sqlTemplate, String tenantId) {
    return sqlTemplate.formatted(getSchemaName(tenantId, moduleMetadata));
  }
}
