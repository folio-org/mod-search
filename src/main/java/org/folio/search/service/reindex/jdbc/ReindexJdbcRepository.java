package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.sql.Timestamp;
import java.util.UUID;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class ReindexJdbcRepository {

  protected static final int BATCH_OPERATION_SIZE = 100;
  protected static final String TRUNCATE_TABLE_SQL = "TRUNCATE TABLE %s;";
  private static final String COUNT_SQL = "SELECT COUNT(*) FROM %s;";
  private static final String UPDATE_FINISHED_AT_RANGE_SQL = "UPDATE %s SET finished_at = ? WHERE id = ?;";

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

  public void setIndexRangeFinishDate(UUID id, Timestamp timestamp) {
    var sql = UPDATE_FINISHED_AT_RANGE_SQL.formatted(getFullTableName(context, rangeTable()));
    jdbcTemplate.update(sql, timestamp, id);
  }

  public abstract void upsert(List<Map<String, Object>> records);

  public abstract void delete(List<String> ids);

  protected String getFetchBySql() {
    return SELECT_RECORD_SQL.formatted(getFullTableName(context, entityTable()));
  }

  public abstract ReindexEntityType entityType();

  protected abstract String entityTable();

  protected abstract String rangeTable();

}
