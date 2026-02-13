package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.service.reindex.ReindexContext;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
public class MergeInstanceRepository extends MergeRangeRepository {

  private static final String INSERT_SQL = """
      INSERT INTO %s (id, tenant_id, shared, is_bound_with, json)
      VALUES (?::uuid, ?, ?, ?, ?::jsonb)
      ON CONFLICT (id)
      DO UPDATE SET shared = EXCLUDED.shared,
      tenant_id = EXCLUDED.tenant_id,
      is_bound_with = EXCLUDED.is_bound_with,
      json = EXCLUDED.json,
      last_updated_date = CURRENT_TIMESTAMP;
    """;

  private static final String INSERT_STAGING_SQL = """
      INSERT INTO %s (id, tenant_id, shared, is_bound_with, json, inserted_at)
      VALUES (?::uuid, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP);
    """;

  private static final String UPDATE_BOUND_WITH_SQL = """
    UPDATE %s SET is_bound_with = ? WHERE id = ?::uuid;
    """;

  private static final String SELECT_BY_UPDATED_QUERY = """
    SELECT id, tenant_id, shared, is_bound_with, json, last_updated_date, is_deleted
    FROM %s.instance
    WHERE %s
    ORDER BY %s
    %s
    """;

  private final ConsortiumTenantProvider consortiumTenantProvider;

  public MergeInstanceRepository(JdbcTemplate jdbcTemplate, JsonConverter jsonConverter, FolioExecutionContext context,
                                 ConsortiumTenantProvider consortiumTenantProvider,
                                 SearchConfigurationProperties searchConfigurationProperties) {
    super(jdbcTemplate, jsonConverter, context, searchConfigurationProperties);
    this.consortiumTenantProvider = consortiumTenantProvider;
  }

  @Override
  public ReindexEntityType entityType() {
    return ReindexEntityType.INSTANCE;
  }

  @Override
  protected String entityTable() {
    return ReindexConstants.INSTANCE_TABLE;
  }

  @Override
  protected Optional<String> stagingEntityTable() {
    return Optional.of(ReindexConstants.STAGING_INSTANCE_TABLE);
  }

  @Override
  public void saveEntities(String tenantId, List<Map<String, Object>> entities) {
    if (ReindexContext.isReindexMode() && ReindexContext.getMemberTenantId() != null) {
      saveEntitiesToStaging(tenantId, entities);
    } else {
      saveEntitiesToMain(tenantId, entities);
    }
  }

  @SuppressWarnings("java:S2077")
  private void saveEntitiesToMain(String tenantId, List<Map<String, Object>> entities) {
    var fullTableName = getFullTableName(context, entityTable());
    var sql = INSERT_SQL.formatted(fullTableName);
    var shared = consortiumTenantProvider.isCentralTenant(tenantId);

    try {
      jdbcTemplate.batchUpdate(sql, entities, BATCH_OPERATION_SIZE,
        (statement, entity) -> {
          statement.setObject(1, entity.get("id"));
          statement.setString(2, tenantId);
          statement.setObject(3, shared);
          statement.setObject(4, entity.getOrDefault("isBoundWith", false));
          statement.setString(5, jsonConverter.toJson(entity));
        });
    } catch (DataAccessException e) {
      log.warn("saveEntitiesToMain::Failed to save batch. Starting processing one-by-one", e);
      for (Map<String, Object> entity : entities) {
        jdbcTemplate.update(sql, entity.get("id"),
          tenantId,
          shared,
          entity.getOrDefault("isBoundWith", false),
          jsonConverter.toJson(entity));
      }
    }
  }

  @SuppressWarnings("java:S2077")
  private void saveEntitiesToStaging(String tenantId, List<Map<String, Object>> entities) {
    var fullTableName = getFullTableName(context, ReindexConstants.STAGING_INSTANCE_TABLE);
    var sql = INSERT_STAGING_SQL.formatted(fullTableName);
    var shared = consortiumTenantProvider.isCentralTenant(tenantId);

    jdbcTemplate.batchUpdate(sql, entities, BATCH_OPERATION_SIZE,
      (statement, entity) -> {
        statement.setObject(1, entity.get("id"));
        statement.setString(2, tenantId);
        statement.setObject(3, shared);
        statement.setObject(4, entity.getOrDefault("isBoundWith", false));
        statement.setString(5, jsonConverter.toJson(entity));
      });

    log.debug("Saved {} entities to staging table {}", entities.size(), ReindexConstants.STAGING_INSTANCE_TABLE);
  }

  @Override
  @SuppressWarnings("java:S2077")
  public void updateBoundWith(String tenantId, String id, boolean bound) {
    if (ReindexContext.isReindexMode()) {
      // Staging tables don't need update operations, they only accumulate data
      log.debug("Update operation not supported for staging table {}", ReindexConstants.STAGING_INSTANCE_TABLE);
    } else {
      var fullTableName = getFullTableName(context, entityTable());
      var sql = UPDATE_BOUND_WITH_SQL.formatted(fullTableName);
      jdbcTemplate.update(sql, bound, id);
    }
  }

  @Override
  public SubResourceResult fetchByTimestamp(String tenant, Timestamp timestamp, int limit) {
    return fetchByTimestamp(SELECT_BY_UPDATED_QUERY, instanceRowMapper(), timestamp, limit, tenant);
  }

  @Override
  public SubResourceResult fetchByTimestamp(String tenant, Timestamp timestamp, String fromId, int limit) {
    return fetchByTimestamp(SELECT_BY_UPDATED_QUERY, instanceRowMapper(), timestamp, fromId, limit, tenant);
  }

  private RowMapper<Map<String, Object>> instanceRowMapper() {
    return (rs, rowNum) -> {
      Map<String, Object> instance = new HashMap<>();
      instance.put("id", rs.getString("id"));
      instance.put("tenantId", rs.getString("tenant_id"));
      instance.put("shared", rs.getBoolean("shared"));
      instance.put("isBoundWith", rs.getBoolean("is_bound_with"));
      instance.put("isDeleted", rs.getBoolean("is_deleted"));
      instance.put(LAST_UPDATED_DATE_FIELD, rs.getTimestamp("last_updated_date"));

      var jsonContent = jsonConverter.fromJsonToMap(rs.getString("json"));
      instance.putAll(jsonContent);

      return instance;
    };
  }
}
