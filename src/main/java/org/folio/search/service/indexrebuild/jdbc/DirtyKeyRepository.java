package org.folio.search.service.indexrebuild.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Queue of keys of derived indexes whose documents have to be rebuilt from their source index. It holds only ids.
 * Times come from the database clock, so module instances with different clocks agree on what was queued before a
 * refresh started.
 */
@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S2077")
public class DirtyKeyRepository {

  private static final String TABLE = "index_rebuild_dirty_key";
  private static final int BATCH_SIZE = 100;

  private static final String UPSERT_SQL = """
    INSERT INTO %s (resource, tenant_id, id, enqueued_at) VALUES (?, ?, ?, clock_timestamp())
    ON CONFLICT (resource, tenant_id, id) DO UPDATE SET enqueued_at = EXCLUDED.enqueued_at;
    """;
  private static final String SELECT_SQL = """
    SELECT DISTINCT id FROM %s WHERE resource = ? AND tenant_id = ANY (?) LIMIT ?;
    """;
  private static final String DELETE_SQL = """
    DELETE FROM %s WHERE resource = ? AND id = ANY (?) AND tenant_id = ANY (?) AND enqueued_at <= ?;
    """;
  private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_xact_lock(hashtext(?));";
  private static final String CURRENT_TIME_SQL = "SELECT clock_timestamp();";

  private final JdbcTemplate jdbcTemplate;
  private final FolioExecutionContext context;

  public void add(IndexRebuildTarget target, String tenantId, Collection<String> keys) {
    jdbcTemplate.batchUpdate(UPSERT_SQL.formatted(table()), keys, BATCH_SIZE, (statement, key) -> {
      statement.setString(1, target.getResource());
      statement.setString(2, tenantId);
      statement.setString(3, key);
    });
  }

  /**
   * Locks the queue of a target until the end of the current transaction, so only one module instance processes it at
   * a time.
   *
   * @return false if the queue is locked by another transaction
   */
  public boolean tryLock(IndexRebuildTarget target) {
    var lockName = table() + ":" + target.getResource();
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject(TRY_LOCK_SQL, Boolean.class, lockName));
  }

  public Timestamp currentTime() {
    return jdbcTemplate.queryForObject(CURRENT_TIME_SQL, Timestamp.class);
  }

  public List<String> findBatch(IndexRebuildTarget target, Collection<String> tenants, int limit) {
    return jdbcTemplate.query(SELECT_SQL.formatted(table()), statement -> {
      statement.setString(1, target.getResource());
      statement.setArray(2, statement.getConnection().createArrayOf("varchar", tenants.toArray()));
      statement.setInt(3, limit);
    }, (rs, rowNum) -> rs.getString("id"));
  }

  /**
   * Removes keys queued before the given time. Keys queued later have to be processed again.
   */
  public void delete(IndexRebuildTarget target, Collection<String> keys, Collection<String> tenants,
                     Timestamp enqueuedBefore) {
    jdbcTemplate.update(DELETE_SQL.formatted(table()), statement -> {
      statement.setString(1, target.getResource());
      statement.setArray(2, statement.getConnection().createArrayOf("varchar", keys.toArray()));
      statement.setArray(3, statement.getConnection().createArrayOf("varchar", tenants.toArray()));
      statement.setTimestamp(4, enqueuedBefore);
    });
  }

  private String table() {
    return getFullTableName(context, TABLE);
  }
}
