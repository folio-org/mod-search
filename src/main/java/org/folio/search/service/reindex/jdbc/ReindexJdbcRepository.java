package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexRangeStatus;
import org.folio.search.utils.JdbcUtils;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class ReindexJdbcRepository {

  protected static final int BATCH_OPERATION_SIZE = 100;
  protected static final String LAST_UPDATED_DATE_FIELD = "lastUpdatedDate";
  
  private static final String COUNT_SQL = "SELECT COUNT(*) FROM %s;";
  private static final String UPDATE_STATUS_SQL = """
    UPDATE %s
    SET finished_at = ?, status = ?, fail_cause = ?
    WHERE id = ?;
    """;

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
    subEntityTable().ifPresent(tableName -> JdbcUtils.truncateTable(tableName, jdbcTemplate, context));
    JdbcUtils.truncateTable(entityTable(), jdbcTemplate, context);
  }

  public void updateRangeStatus(UUID id, Timestamp timestamp, ReindexRangeStatus status, String failCause) {
    var sql = UPDATE_STATUS_SQL.formatted(getFullTableName(context, rangeTable()));
    jdbcTemplate.update(sql, timestamp, status.name(), failCause, id);
  }

  /**
   * Fetch records updated after the given timestamp for background processing.
   * Default implementation returns null - subclasses can override if they support timestamp-based fetching.
   */
  public SubResourceResult fetchByTimestamp(String tenant, Timestamp timestamp) {
    return null;
  }

  public abstract ReindexEntityType entityType();

  protected abstract String entityTable();

  protected Optional<String> subEntityTable() {
    return Optional.empty();
  }

  protected abstract String rangeTable();
}
