package org.folio.search.service.indexrebuild.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.indexrebuild.model.IndexRebuildRange;
import org.folio.search.service.indexrebuild.model.IndexRebuildRangeStatus;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S2077")
public class IndexRebuildRangeRepository {

  static final String RANGE_TABLE = "index_rebuild_range";

  private static final int BATCH_SIZE = 100;

  private static final String INSERT_SQL = """
    INSERT INTO %s (id, trace_id, resource, tenant_id, lower, upper, status, created_at, scope_tenant_id)
    VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?);
    """;
  private static final String SELECT_BY_ID_SQL = "SELECT * FROM %s WHERE id = ?;";
  private static final String SELECT_UNFINISHED_SQL =
    "SELECT * FROM %s WHERE resource = ? AND tenant_id = ? AND status <> 'SUCCESS';";
  private static final String SELECT_ALL_UNFINISHED_SQL =
    "SELECT * FROM %s WHERE resource = ? AND status <> 'SUCCESS';";
  private static final String DELETE_SQL = "DELETE FROM %s WHERE resource = ? AND tenant_id = ?;";
  private static final String DELETE_ALL_SQL = "DELETE FROM %s WHERE resource = ?;";
  private static final String UPDATE_STATUS_SQL =
    "UPDATE %s SET status = ?, finished_at = ?, fail_cause = ? WHERE id = ?;";
  private static final String RESET_FAILED_SQL = """
    UPDATE %s SET status = 'PENDING', finished_at = NULL, fail_cause = NULL
    WHERE resource = ? AND tenant_id = ? AND status = 'FAILED';
    """;
  private static final String RESET_ALL_FAILED_SQL = """
    UPDATE %s SET status = 'PENDING', finished_at = NULL, fail_cause = NULL
    WHERE resource = ? AND status = 'FAILED';
    """;
  private static final String COUNT_SQL = """
    SELECT tenant_id, count(*) AS total, count(*) FILTER (WHERE status = 'SUCCESS') AS processed
    FROM %s WHERE resource = ? GROUP BY tenant_id;
    """;

  private final JdbcTemplate jdbcTemplate;
  private final FolioExecutionContext context;

  public void saveAll(List<IndexRebuildRange> ranges) {
    var now = Timestamp.from(Instant.now());
    jdbcTemplate.batchUpdate(INSERT_SQL.formatted(table()), ranges, BATCH_SIZE, (statement, range) -> {
      statement.setObject(1, range.id());
      statement.setObject(2, range.traceId());
      statement.setString(3, range.target().getResource());
      statement.setString(4, range.tenantId());
      statement.setString(5, range.lower());
      statement.setString(6, range.upper());
      statement.setTimestamp(7, now);
      statement.setString(8, range.scopeTenantId());
    });
  }

  public Optional<IndexRebuildRange> findById(IndexRebuildTarget target, UUID id) {
    return jdbcTemplate.query(SELECT_BY_ID_SQL.formatted(table()), rowMapper(target), id).stream().findFirst();
  }

  /**
   * Finds ranges of the tenant that haven't been processed successfully.
   */
  public List<IndexRebuildRange> findUnfinished(IndexRebuildTarget target, String tenantId) {
    return jdbcTemplate.query(SELECT_UNFINISHED_SQL.formatted(table()), rowMapper(target), target.getResource(),
      tenantId);
  }

  public List<IndexRebuildRange> findAllUnfinished(IndexRebuildTarget target) {
    return jdbcTemplate.query(SELECT_ALL_UNFINISHED_SQL.formatted(table()), rowMapper(target), target.getResource());
  }

  public void deleteAll(IndexRebuildTarget target) {
    jdbcTemplate.update(DELETE_ALL_SQL.formatted(table()), target.getResource());
  }

  public void resetAllFailed(IndexRebuildTarget target) {
    jdbcTemplate.update(RESET_ALL_FAILED_SQL.formatted(table()), target.getResource());
  }

  public void delete(IndexRebuildTarget target, String tenantId) {
    jdbcTemplate.update(DELETE_SQL.formatted(table()), target.getResource(), tenantId);
  }

  public void markSuccess(UUID id) {
    updateStatus(id, IndexRebuildRangeStatus.SUCCESS, null);
  }

  public void markFailed(UUID id, String failCause) {
    updateStatus(id, IndexRebuildRangeStatus.FAILED, failCause);
  }

  public void resetFailed(IndexRebuildTarget target, String tenantId) {
    jdbcTemplate.update(RESET_FAILED_SQL.formatted(table()), target.getResource(), tenantId);
  }

  /**
   * Counts ranges of the latest run per tenant.
   */
  public Map<String, RangeCounts> countByTenant(IndexRebuildTarget target) {
    var counts = new HashMap<String, RangeCounts>();
    jdbcTemplate.query(COUNT_SQL.formatted(table()), rs -> {
      counts.put(rs.getString("tenant_id"), new RangeCounts(rs.getInt("total"), rs.getInt("processed")));
    }, target.getResource());
    return counts;
  }

  private void updateStatus(UUID id, IndexRebuildRangeStatus status, String failCause) {
    jdbcTemplate.update(UPDATE_STATUS_SQL.formatted(table()), status.name(), Timestamp.from(Instant.now()), failCause,
      id);
  }

  private String table() {
    return getFullTableName(context, RANGE_TABLE);
  }

  private static RowMapper<IndexRebuildRange> rowMapper(IndexRebuildTarget target) {
    return (rs, rowNum) -> new IndexRebuildRange(rs.getObject("id", UUID.class), rs.getObject("trace_id", UUID.class),
      target, rs.getString("tenant_id"), rs.getString("lower"), rs.getString("upper"),
      IndexRebuildRangeStatus.valueOf(rs.getString("status")), rs.getString("scope_tenant_id"));
  }

  public record RangeCounts(int total, int processed) { }
}
