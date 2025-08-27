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
import org.springframework.transaction.annotation.Transactional;

public abstract class ReindexJdbcRepository {

  public static final int BATCH_OPERATION_SIZE = 100;
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
  public void deleteByTenantId(String tenantId) {
    // Delete from sub-entity table if it exists and supports tenant-specific deletion
    subEntityTable().ifPresent(tableName -> {
      if (supportsTenantSpecificDeletion()) {
        var fullTableName = getFullTableName(context, tableName);
        var sql = String.format("DELETE FROM %s WHERE tenant_id = ?", fullTableName);
        jdbcTemplate.update(sql, tenantId);
      }
    });

    // Delete from main entity table if it supports tenant-specific deletion
    if (supportsTenantSpecificDeletion()) {
      var fullTableName = getFullTableName(context, entityTable());
      var sql = String.format("DELETE FROM %s WHERE tenant_id = ?", fullTableName);
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

}
