package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
public class MergeInstanceRepository extends MergeRangeRepository {

  private static final String INSERT_SQL = """
      INSERT INTO %s (id, tenant_id, shared, is_bound_with, json)
      VALUES (?::uuid, ?, ?, ?, ?::jsonb)
      ON CONFLICT (id)
      DO UPDATE SET shared = EXCLUDED.shared,
      is_bound_with = EXCLUDED.is_bound_with,
      json = EXCLUDED.json;
    """;

  private static final String UPDATE_BOUND_WITH_SQL = """
    UPDATE %s SET is_bound_with = ? WHERE id = ?::uuid;
    """;
  private final ConsortiumTenantProvider consortiumTenantProvider;

  public MergeInstanceRepository(JdbcTemplate jdbcTemplate, JsonConverter jsonConverter, FolioExecutionContext context,
                                 ConsortiumTenantProvider consortiumTenantProvider) {
    super(jdbcTemplate, jsonConverter, context);
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
  public void saveEntities(String tenantId, List<Map<String, Object>> entities) {
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
      log.warn("saveEntities::Failed to save batch. Starting processing one-by-one", e);
      for (Map<String, Object> entity : entities) {
        jdbcTemplate.update(sql, entity.get("id"),
          tenantId,
          shared,
          entity.getOrDefault("isBoundWith", false),
          jsonConverter.toJson(entity));
      }
    }
  }

  @Override
  public void updateBoundWith(String tenantId, String id, boolean bound) {
    var fullTableName = getFullTableName(context, entityTable());
    var sql = UPDATE_BOUND_WITH_SQL.formatted(fullTableName);
    jdbcTemplate.update(sql, bound /*? "true" : "false"*/, id);
  }
}
