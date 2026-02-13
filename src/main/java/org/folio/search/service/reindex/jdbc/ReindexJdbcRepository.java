package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;
import static org.folio.search.utils.JdbcUtils.getParamPlaceholderForUuid;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexRangeStatus;
import org.folio.search.utils.JdbcUtils;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

public abstract class ReindexJdbcRepository {

  public static final int BATCH_OPERATION_SIZE = 100;
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

  public void truncateStaging() {
    subEntityStagingTable().ifPresent(tableName -> JdbcUtils.truncateTable(tableName, jdbcTemplate, context));
    stagingEntityTable().ifPresent(tableName -> JdbcUtils.truncateTable(tableName, jdbcTemplate, context));
  }

  @Transactional
  @SuppressWarnings("java:S2077")
  public void deleteByTenantId(String tenantId) {
    // Delete from sub-entity table if present
    subEntityTable().ifPresent(tableName -> {
      var fullTableName = getFullTableName(context, tableName);
      var sql = "DELETE FROM %s WHERE tenant_id = ?".formatted(fullTableName);
      jdbcTemplate.update(sql, tenantId);
    });

    // Delete from main entity table only if it supports tenant-specific deletion
    if (supportsTenantSpecificDeletion()) {
      var fullTableName = getFullTableName(context, entityTable());
      var sql = "DELETE FROM %s WHERE tenant_id = ?".formatted(fullTableName);
      jdbcTemplate.update(sql, tenantId);
    }
  }

  // Override in subclasses that don't have tenant_id columns (like Subject, Contributor, etc.)
  protected boolean supportsTenantSpecificDeletion() {
    return true;
  }

  public void updateRangeStatus(UUID id, Timestamp timestamp, ReindexRangeStatus status, String failCause) {
    var sql = UPDATE_STATUS_SQL.formatted(getFullTableName(context, rangeTable()));
    jdbcTemplate.update(sql, timestamp, status.name(), failCause, id);
  }

  /**
   * Fetch records updated after the given timestamp with a limit for background processing.
   * Default implementation returns null - subclasses can override if they support timestamp-based fetching with limit.
   */
  @SuppressWarnings("unused")
  public SubResourceResult fetchByTimestamp(String tenant, Timestamp timestamp, int limit) {
    return null;
  }

  /**
   * Fetch records starting from a specific timestamp and ID for background processing.
   * Default implementation returns null - subclasses can override if they support ID-based seeking.
   */
  @SuppressWarnings("unused")
  public SubResourceResult fetchByTimestamp(String tenant, Timestamp timestamp, String fromId, int limit) {
    return null;
  }

  protected SubResourceResult fetchByTimestamp(String query, RowMapper<Map<String, Object>> rowMapper,
                                               Timestamp timestamp, int limit, String tenant) {
    var sql = buildSelectQuery(query, tenant, false);
    var records = jdbcTemplate.query(sql, rowMapper, timestamp, limit);
    var lastUpdateDate = records.isEmpty() ? null : records.getLast().get(LAST_UPDATED_DATE_FIELD);
    return new SubResourceResult(records, (Timestamp) lastUpdateDate);
  }

  protected SubResourceResult fetchByTimestamp(String query, RowMapper<Map<String, Object>> rowMapper,
                                               Timestamp timestamp, String fromId, int limit, String tenant) {
    var sql = buildSelectQuery(query, tenant, true);
    var records = jdbcTemplate.query(sql, rowMapper, timestamp, fromId, limit);
    var lastUpdateDate = records.isEmpty() ? null : records.getLast().get(LAST_UPDATED_DATE_FIELD);
    return new SubResourceResult(records, (Timestamp) lastUpdateDate);
  }

  public abstract ReindexEntityType entityType();

  protected abstract String entityTable();

  protected Optional<String> subEntityTable() {
    return Optional.empty();
  }

  protected Optional<String> stagingEntityTable() {
    return Optional.empty();
  }

  protected Optional<String> subEntityStagingTable() {
    return Optional.empty();
  }

  protected abstract String rangeTable();

  @SuppressWarnings("java:S2077")
  protected void deleteByInstanceIds(String query, List<String> instanceIds, String tenantId) {
    var sql = query.formatted(
      JdbcUtils.getSchemaName(context),
      getParamPlaceholderForUuid(instanceIds.size()),
      tenantId == null ? "" : "AND tenant_id = ?");

    if (tenantId != null) {
      var params = Stream.of(instanceIds, List.of(tenantId)).flatMap(List::stream).toArray();
      jdbcTemplate.update(sql, params);
      return;
    }

    jdbcTemplate.update(sql, instanceIds.toArray());
  }

  protected String buildSelectQuery(String sql, String tenant, boolean includeFromId) {
    var whereClause = includeFromId ? "(last_updated_date, id) > (?, ?)" : "last_updated_date > ?";
    var orderBy = "last_updated_date, id";
    var orderByAsc = "last_updated_date ASC, id ASC";
    var limitClause = "LIMIT ?";
    return sql.formatted(
      JdbcUtils.getSchemaName(tenant, context.getFolioModuleMetadata()), whereClause, orderBy, limitClause, orderByAsc);
  }
}
