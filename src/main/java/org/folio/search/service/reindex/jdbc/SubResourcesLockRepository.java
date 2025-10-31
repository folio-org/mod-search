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

  private static final String UNLOCK_SUB_RESOURCE_SQL = """
    UPDATE %s.sub_resources_lock
    SET locked_flag = FALSE, last_updated_date = ?
    WHERE entity_type = ?
    """;

  private static final String UPDATE_LOCK_TIMESTAMP_SQL = """
    UPDATE %s.sub_resources_lock
    SET last_updated_date = ?
    WHERE entity_type = ? AND locked_flag = TRUE
    """;

  private static final String RELEASE_STALE_LOCK_SQL = """
    UPDATE %s.sub_resources_lock
    SET locked_flag = FALSE
    WHERE entity_type = ?
      AND locked_flag = TRUE
      AND last_updated_date < ?
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

  public void unlockSubResource(ReindexEntityType entityType, Timestamp lastUpdatedDate, String tenantId) {

    var formattedSql = formatSqlWithSchema(UNLOCK_SUB_RESOURCE_SQL, tenantId);
    jdbcTemplate.update(formattedSql, lastUpdatedDate, entityType.getType());
  }

  /**
   * Updates the lock timestamp for a sub-resource without releasing the lock.
   * This prevents the lock from appearing stale during long-running batch processing.
   *
   * @param entityType the type of entity being processed
   * @param lastUpdatedDate the timestamp to update in the lock record
   * @param tenantId the tenant identifier
   */
  public void updateLockTimestamp(ReindexEntityType entityType, Timestamp lastUpdatedDate, String tenantId) {
    var formattedSql = formatSqlWithSchema(UPDATE_LOCK_TIMESTAMP_SQL, tenantId);
    jdbcTemplate.update(formattedSql, lastUpdatedDate, entityType.getType());
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

  private String formatSqlWithSchema(String sqlTemplate, String tenantId) {
    return sqlTemplate.formatted(getSchemaName(tenantId, moduleMetadata));
  }
}
