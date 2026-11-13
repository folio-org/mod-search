package org.folio.search.service.indexrebuild.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Temporary list of records deleted while a rebuild of their index is running. It holds no record data.
 */
@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S2077")
public class DeletedRecordRepository {

  private static final String TABLE = "index_rebuild_deleted_record";
  private static final int BATCH_SIZE = 100;

  private static final String INSERT_SQL = """
    INSERT INTO %s (resource, tenant_id, id, deleted_at) VALUES (?, ?, ?::uuid, ?)
    ON CONFLICT (resource, tenant_id, id) DO NOTHING;
    """;
  private static final String DELETE_SQL = "DELETE FROM %s WHERE resource = ? AND tenant_id = ? AND id = ?::uuid;";
  private static final String DELETE_ALL_SQL = "DELETE FROM %s WHERE resource = ? AND tenant_id = ?;";
  private static final String SELECT_IDS_SQL = "SELECT id FROM %s WHERE resource = ? AND tenant_id = ?;";

  private final JdbcTemplate jdbcTemplate;
  private final FolioExecutionContext context;

  public void add(IndexRebuildTarget target, String tenantId, List<String> ids) {
    var deletedAt = Timestamp.from(Instant.now());
    jdbcTemplate.batchUpdate(INSERT_SQL.formatted(table()), ids, BATCH_SIZE, (statement, id) -> {
      statement.setString(1, target.getResource());
      statement.setString(2, tenantId);
      statement.setString(3, id);
      statement.setTimestamp(4, deletedAt);
    });
  }

  public void remove(IndexRebuildTarget target, String tenantId, List<String> ids) {
    jdbcTemplate.batchUpdate(DELETE_SQL.formatted(table()), ids, BATCH_SIZE, (statement, id) -> {
      statement.setString(1, target.getResource());
      statement.setString(2, tenantId);
      statement.setString(3, id);
    });
  }

  public List<String> findIds(IndexRebuildTarget target, String tenantId) {
    return jdbcTemplate.query(SELECT_IDS_SQL.formatted(table()),
      (rs, rowNum) -> rs.getObject("id", UUID.class).toString(), target.getResource(), tenantId);
  }

  public void deleteAll(IndexRebuildTarget target, String tenantId) {
    jdbcTemplate.update(DELETE_ALL_SQL.formatted(table()), target.getResource(), tenantId);
  }

  private String table() {
    return getFullTableName(context, TABLE);
  }
}
