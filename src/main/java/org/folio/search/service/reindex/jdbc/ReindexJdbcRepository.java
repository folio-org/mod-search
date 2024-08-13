package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class ReindexJdbcRepository {

  protected static final String TRUNCATE_TABLE_SQL = "TRUNCATE TABLE %s;";
  private static final String COUNT_SQL = "SELECT COUNT(*) FROM %s;";

  protected final JsonConverter jsonConverter;
  protected final FolioExecutionContext context;
  protected final JdbcTemplate jdbcTemplate;

  protected ReindexJdbcRepository(JdbcTemplate jdbcTemplate,
                                  JsonConverter jsonConverter,
                                  FolioExecutionContext context) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonConverter = jsonConverter;
    this.context = context;
  }

  public Integer countEntities() {
    var fullTableName = getFullTableName(context, entityTable());
    var sql = COUNT_SQL.formatted(fullTableName);
    return jdbcTemplate.queryForObject(sql, Integer.class);
  }

  public void truncate() {
    String sql = TRUNCATE_TABLE_SQL.formatted(getFullTableName(context, entityTable()));
    jdbcTemplate.execute(sql);
  }

  protected abstract String entityTable();

}
