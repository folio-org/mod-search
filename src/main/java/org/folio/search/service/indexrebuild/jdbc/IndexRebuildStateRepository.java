package org.folio.search.service.indexrebuild.jdbc;

import static org.folio.search.service.indexrebuild.jdbc.IndexRebuildRangeRepository.RANGE_TABLE;
import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.indexrebuild.model.IndexRebuildState;
import org.folio.search.service.indexrebuild.model.IndexRebuildStatus;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S2077")
public class IndexRebuildStateRepository {

  private static final String STATE_TABLE = "index_rebuild_state";

  private static final String SELECT_SQL = "SELECT * FROM %s WHERE resource = ?;";

  private static final String CLAIM_SQL = """
    INSERT INTO %s AS state (resource, tenant_id, status, started_at)
    VALUES (?, ?, 'IN_PROGRESS', ?)
    ON CONFLICT (resource, tenant_id) DO UPDATE
    SET status = 'IN_PROGRESS', fingerprint = NULL, started_at = EXCLUDED.started_at, finished_at = NULL,
        fail_cause = NULL
    WHERE state.status NOT IN ('IN_PROGRESS', 'FINALIZING');
    """;

  private static final String MARK_FAILED_SQL = """
    UPDATE %s SET status = 'FAILED', finished_at = ?, fail_cause = ?
    WHERE resource = ? AND tenant_id = ? AND status IN ('IN_PROGRESS', 'FINALIZING');
    """;

  private static final String PREPARE_RETRY_SQL = """
    UPDATE %s SET status = 'IN_PROGRESS', finished_at = NULL, fail_cause = NULL
    WHERE resource = ? AND tenant_id = ? AND status IN ('FAILED', 'FINALIZING');
    """;

  private static final String START_FINALIZING_SQL = """
    UPDATE %s SET status = 'FINALIZING'
    WHERE resource = ? AND tenant_id = ? AND status = 'IN_PROGRESS'
      AND NOT EXISTS (SELECT 1 FROM %s WHERE resource = ? AND tenant_id = ? AND status <> 'SUCCESS');
    """;

  private static final String COMPLETE_SQL = """
    UPDATE %s SET status = 'COMPLETED', fingerprint = ?, finished_at = ?
    WHERE resource = ? AND tenant_id = ? AND status = 'FINALIZING';
    """;

  private static final String MARK_ALL_FAILED_SQL = """
    UPDATE %s SET status = 'FAILED', finished_at = ?, fail_cause = ?
    WHERE resource = ? AND status IN ('IN_PROGRESS', 'FINALIZING');
    """;

  private static final String PREPARE_ALL_RETRY_SQL = """
    UPDATE %s SET status = 'IN_PROGRESS', finished_at = NULL, fail_cause = NULL
    WHERE resource = ? AND status IN ('FAILED', 'FINALIZING');
    """;

  private static final String START_ALL_FINALIZING_SQL = """
    UPDATE %s SET status = 'FINALIZING'
    WHERE resource = ? AND status = 'IN_PROGRESS'
      AND NOT EXISTS (SELECT 1 FROM %s WHERE resource = ? AND status <> 'SUCCESS');
    """;

  private static final String COMPLETE_ALL_SQL = """
    UPDATE %s SET status = 'COMPLETED', fingerprint = ?, finished_at = ?
    WHERE resource = ? AND status = 'FINALIZING';
    """;

  private static final String IS_ANY_ACTIVE_SQL = """
    SELECT EXISTS (SELECT 1 FROM %s WHERE resource = ? AND status IN ('IN_PROGRESS', 'FINALIZING'));
    """;

  private static final String IS_TRACKING_SQL = """
    SELECT EXISTS (SELECT 1 FROM %s
                   WHERE resource = ? AND tenant_id = ? AND status IN ('IN_PROGRESS', 'FAILED', 'FINALIZING'));
    """;

  private final JdbcTemplate jdbcTemplate;
  private final FolioExecutionContext context;

  public List<IndexRebuildState> findAll(IndexRebuildTarget target) {
    return jdbcTemplate.query(SELECT_SQL.formatted(table()), stateRowMapper(), target.getResource());
  }

  /**
   * Marks a run as started for the tenant.
   *
   * @return false if a run is already active for the tenant
   */
  public boolean claim(IndexRebuildTarget target, String tenantId) {
    return jdbcTemplate.update(CLAIM_SQL.formatted(table()), target.getResource(), tenantId, now()) == 1;
  }

  public void markFailed(IndexRebuildTarget target, String tenantId, String failCause) {
    jdbcTemplate.update(MARK_FAILED_SQL.formatted(table()), now(), failCause, target.getResource(), tenantId);
  }

  /**
   * Returns a failed or interrupted run of the tenant to the in progress status.
   *
   * @return false if the tenant has no run to retry
   */
  public boolean prepareRetry(IndexRebuildTarget target, String tenantId) {
    return jdbcTemplate.update(PREPARE_RETRY_SQL.formatted(table()), target.getResource(), tenantId) == 1;
  }

  /**
   * Moves the run of the tenant to finalization if all its ranges succeeded. Only one caller can succeed.
   *
   * @return true if the caller has to finalize the run
   */
  public boolean tryStartFinalizing(IndexRebuildTarget target, String tenantId) {
    var sql = START_FINALIZING_SQL.formatted(table(), getFullTableName(context, RANGE_TABLE));
    return jdbcTemplate.update(sql, target.getResource(), tenantId, target.getResource(), tenantId) == 1;
  }

  public void complete(IndexRebuildTarget target, String tenantId, String fingerprint) {
    jdbcTemplate.update(COMPLETE_SQL.formatted(table()), fingerprint, now(), target.getResource(), tenantId);
  }

  /**
   * Marks every active run of the target as failed. Used for combined indexes, where a run isn't divided by tenant.
   */
  public void markAllFailed(IndexRebuildTarget target, String failCause) {
    jdbcTemplate.update(MARK_ALL_FAILED_SQL.formatted(table()), now(), failCause, target.getResource());
  }

  /**
   * Returns failed or interrupted runs of the target to the in progress status.
   *
   * @return false if there is no run to retry
   */
  public boolean prepareAllRetry(IndexRebuildTarget target) {
    return jdbcTemplate.update(PREPARE_ALL_RETRY_SQL.formatted(table()), target.getResource()) > 0;
  }

  /**
   * Moves runs of the target to finalization if all its ranges succeeded. Only one caller can succeed.
   *
   * @return true if the caller has to finalize the runs
   */
  public boolean tryStartAllFinalizing(IndexRebuildTarget target) {
    var sql = START_ALL_FINALIZING_SQL.formatted(table(), getFullTableName(context, RANGE_TABLE));
    return jdbcTemplate.update(sql, target.getResource(), target.getResource()) > 0;
  }

  public void completeAll(IndexRebuildTarget target, String fingerprint) {
    jdbcTemplate.update(COMPLETE_ALL_SQL.formatted(table()), fingerprint, now(), target.getResource());
  }

  public boolean isAnyActive(IndexRebuildTarget target) {
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject(IS_ANY_ACTIVE_SQL.formatted(table()), Boolean.class,
      target.getResource()));
  }

  /**
   * Checks if changes of the tenant's records have to be tracked because a run may still write older records.
   */
  public boolean isTracking(IndexRebuildTarget target, String tenantId) {
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject(IS_TRACKING_SQL.formatted(table()), Boolean.class,
      target.getResource(), tenantId));
  }

  private String table() {
    return getFullTableName(context, STATE_TABLE);
  }

  private static Timestamp now() {
    return Timestamp.from(Instant.now());
  }

  private static RowMapper<IndexRebuildState> stateRowMapper() {
    return (rs, rowNum) -> new IndexRebuildState(rs.getString("tenant_id"),
      IndexRebuildStatus.valueOf(rs.getString("status")), rs.getString("fingerprint"), rs.getTimestamp("started_at"),
      rs.getTimestamp("finished_at"), rs.getString("fail_cause"));
  }
}
