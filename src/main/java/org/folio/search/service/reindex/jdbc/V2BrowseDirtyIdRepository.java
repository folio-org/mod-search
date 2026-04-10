package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getSchemaName;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.spring.FolioModuleMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class V2BrowseDirtyIdRepository {

  private static final String ENQUEUE_SQL = """
    INSERT INTO %s.v2_browse_dirty_id (browse_type, browse_id)
    VALUES (?, ?) ON CONFLICT DO NOTHING
    """;

  private static final String CLAIM_BATCH_SQL = """
    WITH claimed AS (
      SELECT browse_type, browse_id
      FROM %s.v2_browse_dirty_id
      ORDER BY created_at, browse_type, browse_id
      LIMIT ? FOR UPDATE SKIP LOCKED
    ), deleted AS (
      DELETE FROM %s.v2_browse_dirty_id dirty
      USING claimed
      WHERE dirty.browse_type = claimed.browse_type
        AND dirty.browse_id = claimed.browse_id
      RETURNING dirty.browse_type, dirty.browse_id
    )
    SELECT browse_type, browse_id FROM deleted
    """;

  private static final String COUNT_PENDING_SQL = """
    SELECT count(*) FROM %s.v2_browse_dirty_id
    """;

  private final JdbcTemplate jdbcTemplate;
  private final FolioModuleMetadata moduleMetadata;

  public void enqueueBatch(String tenantId, Collection<BrowseTypeAndId> rows) {
    if (rows.isEmpty()) {
      return;
    }
    var sql = formatSql(ENQUEUE_SQL, tenantId);
    jdbcTemplate.batchUpdate(sql, rows.stream()
      .map(row -> new Object[]{row.browseType(), row.browseId()})
      .toList());
  }

  public List<DirtyBrowseIdRow> claimBatch(String tenantId, int batchSize) {
    var schema = getSchemaName(tenantId, moduleMetadata);
    var sql = CLAIM_BATCH_SQL.formatted(schema, schema);
    return jdbcTemplate.query(sql,
      (rs, rowNum) -> new DirtyBrowseIdRow(rs.getString("browse_type"), rs.getString("browse_id")),
      batchSize);
  }

  public int countPending(String tenantId) {
    var sql = formatSql(COUNT_PENDING_SQL, tenantId);
    var result = jdbcTemplate.queryForObject(sql, Integer.class);
    return result != null ? result : 0;
  }

  private String formatSql(String sqlTemplate, String tenantId) {
    return sqlTemplate.formatted(getSchemaName(tenantId, moduleMetadata));
  }

  public record BrowseTypeAndId(String browseType, String browseId) {
  }

  public record DirtyBrowseIdRow(String browseType, String browseId) {
  }
}
