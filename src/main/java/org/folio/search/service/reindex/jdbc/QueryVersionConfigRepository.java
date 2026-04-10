package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
public class QueryVersionConfigRepository {

  public static final String CONFIG_TABLE = "query_version_config";

  private static final String SELECT_DEFAULT_SQL = """
    SELECT default_version FROM %s WHERE tenant_id = ?;
    """;

  private static final String UPSERT_DEFAULT_SQL = """
    INSERT INTO %s (tenant_id, default_version) VALUES (?, ?)
    ON CONFLICT (tenant_id) DO UPDATE SET default_version = ?, updated_at = CURRENT_TIMESTAMP;
    """;

  private final JdbcTemplate jdbcTemplate;
  private final FolioExecutionContext context;

  public QueryVersionConfigRepository(JdbcTemplate jdbcTemplate, FolioExecutionContext context) {
    this.jdbcTemplate = jdbcTemplate;
    this.context = context;
  }

  public Optional<String> getDefaultVersion(String tenantId) {
    try {
      var sql = SELECT_DEFAULT_SQL.formatted(getFullTableName(context, CONFIG_TABLE));
      var results = jdbcTemplate.queryForList(sql, tenantId);
      if (results.isEmpty()) {
        return Optional.empty();
      }
      return Optional.ofNullable((String) results.getFirst().get("default_version"));
    } catch (Exception e) {
      log.debug("getDefaultVersion:: table may not exist yet, defaulting to empty", e);
      return Optional.empty();
    }
  }

  public void upsertDefaultVersion(String tenantId, String version) {
    var sql = UPSERT_DEFAULT_SQL.formatted(getFullTableName(context, CONFIG_TABLE));
    jdbcTemplate.update(sql, tenantId, version, version);
  }
}
