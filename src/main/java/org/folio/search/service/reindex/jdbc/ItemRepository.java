package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;
import static org.folio.search.utils.JdbcUtils.getParamPlaceholderForUuid;

import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.ReindexConstants;
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
      json = EXCLUDED.json;
    """;

  private static final String DELETE_SQL = """
    DELETE FROM %s WHERE id IN (%s) AND tenant_id = ?;
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
  public void saveEntities(String tenantId, List<Map<String, Object>> entities) {
    log.info("ItemRepository::saveEntities tenantId {}, entities {}", tenantId, entities);
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

  @Override
  public void deleteEntities(List<String> ids, String tenantId) {
    var fullTableName = getFullTableName(context, entityTable());
    var sql = DELETE_SQL.formatted(fullTableName, getParamPlaceholderForUuid(ids.size()));

    jdbcTemplate.update(sql, ids.toArray(), tenantId);
  }

  @Override
  protected String entityTable() {
    return ReindexConstants.ITEM_TABLE;
  }
}
