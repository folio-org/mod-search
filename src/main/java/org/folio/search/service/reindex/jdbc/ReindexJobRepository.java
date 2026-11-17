package org.folio.search.service.reindex.jdbc;

import static org.folio.search.service.reindex.ReindexConstants.REINDEX_JOB_TABLE;
import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReindexJobRepository {

  private static final String DELETE_ALL_SQL = "DELETE FROM %s;";
  private static final String INSERT_SQL = "INSERT INTO %s (id, created_at) VALUES (?, ?);";
  private static final String SELECT_ACTIVE_SQL = "SELECT id FROM %s ORDER BY created_at DESC LIMIT 1;";

  private final FolioExecutionContext context;
  private final JdbcTemplate jdbcTemplate;

  /**
   * Replaces the active reindex job of the current tenant schema with the given one.
   * Must be called within a transaction so readers never observe an empty table in between.
   */
  public void replaceActiveJob(UUID jobId) {
    var fullTableName = getFullTableName(context, REINDEX_JOB_TABLE);
    jdbcTemplate.update(DELETE_ALL_SQL.formatted(fullTableName));
    jdbcTemplate.update(INSERT_SQL.formatted(fullTableName), jobId, Timestamp.from(Instant.now()));
  }

  public Optional<UUID> findActiveJobId() {
    var sql = SELECT_ACTIVE_SQL.formatted(getFullTableName(context, REINDEX_JOB_TABLE));
    return jdbcTemplate.queryForList(sql, UUID.class).stream().findFirst();
  }
}
