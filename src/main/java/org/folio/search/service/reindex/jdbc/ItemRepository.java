package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.service.reindex.ReindexContext;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
public class ItemRepository extends MergeRangeRepository {

  private static final String INSERT_SQL = """
      INSERT INTO %s (id, tenant_id, instance_id, holding_id, json)
      VALUES (?::uuid, ?, ?::uuid, ?::uuid, ?::jsonb)
      ON CONFLICT (id, tenant_id)
      DO UPDATE SET
      instance_id = EXCLUDED.instance_id,
      holding_id = EXCLUDED.holding_id,
      tenant_id = EXCLUDED.tenant_id,
      json = EXCLUDED.json;
    """;

  private static final String INSERT_STAGING_SQL = """
      INSERT INTO %s (id, tenant_id, instance_id, holding_id, json, inserted_at)
      VALUES (?::uuid, ?, ?::uuid, ?::uuid, ?::jsonb, CURRENT_TIMESTAMP);
    """;

  protected ItemRepository(JdbcTemplate jdbcTemplate,
                           JsonConverter jsonConverter,
                           FolioExecutionContext context) {
    super(jdbcTemplate, jsonConverter, context);
  }

  @Override
  public ReindexEntityType entityType() {
    return ReindexEntityType.ITEM;
  }

  @Override
  protected String entityTable() {
    return ReindexConstants.ITEM_TABLE;
  }

  @Override
  protected Optional<String> stagingEntityTable() {
    return Optional.of(ReindexConstants.STAGING_ITEM_TABLE);
  }

  @Override
  public void saveEntities(String tenantId, List<Map<String, Object>> entities) {
    if (ReindexContext.isReindexMode()) {
      saveEntitiesToStaging(tenantId, entities);
    } else {
      saveEntitiesToMain(tenantId, entities);
    }
  }

  private void saveEntitiesToMain(String tenantId, List<Map<String, Object>> entities) {
    var fullTableName = getFullTableName(context, entityTable());
    var sql = INSERT_SQL.formatted(fullTableName);

    jdbcTemplate.batchUpdate(sql, entities, BATCH_OPERATION_SIZE,
      (statement, entity) -> {
        statement.setObject(1, entity.get("id"));
        statement.setString(2, tenantId);
        statement.setObject(3, entity.get("instanceId"));
        statement.setObject(4, entity.get("holdingsRecordId"));
        statement.setString(5, jsonConverter.toJson(entity));
      });

  }

  private void saveEntitiesToStaging(String tenantId, List<Map<String, Object>> entities) {
    var fullTableName = getFullTableName(context, ReindexConstants.STAGING_ITEM_TABLE);
    var sql = INSERT_STAGING_SQL.formatted(fullTableName);

    jdbcTemplate.batchUpdate(sql, entities, BATCH_OPERATION_SIZE,
      (statement, entity) -> {
        statement.setObject(1, entity.get("id"));
        statement.setString(2, tenantId);
        statement.setObject(3, entity.get("instanceId"));
        statement.setObject(4, entity.get("holdingsRecordId"));
        statement.setString(5, jsonConverter.toJson(entity));
      });

    log.debug("Saved {} entities to staging table {}", entities.size(), ReindexConstants.STAGING_ITEM_TABLE);
  }
}
