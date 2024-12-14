package org.folio.search.service.reindex.jdbc;

import static jakarta.persistence.GenerationType.UUID;
import static org.folio.search.utils.JdbcUtils.getFullTableName;
import static org.folio.search.utils.JdbcUtils.getParamPlaceholderForUuidArray;

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
public class HoldingRepository extends MergeRangeRepository {

  private static final String INSERT_SQL = """
      INSERT INTO %s (id, tenant_id, instance_id, json)
      VALUES (?::uuid, ?, ?::uuid, ?::jsonb)
      ON CONFLICT (id, tenant_id)
      DO UPDATE SET
      instance_id = EXCLUDED.instance_id,
      json = EXCLUDED.json;
    """;

  private static final String DELETE_SQL = """
    DELETE FROM %s WHERE id = ANY (%s) AND tenant_id = ?;
    """;

  protected HoldingRepository(JdbcTemplate jdbcTemplate,
                              JsonConverter jsonConverter,
                              FolioExecutionContext context) {
    super(jdbcTemplate, jsonConverter, context);
  }

  @Override
  public ReindexEntityType entityType() {
    return ReindexEntityType.HOLDINGS;
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
        statement.setString(4, jsonConverter.toJson(entity));
      });
  }

  @Override
  public void deleteEntities(List<String> ids, String tenantId) {
    var fullTableName = getFullTableName(context, entityTable());
    var sql = DELETE_SQL.formatted(fullTableName, getParamPlaceholderForUuidArray(ids.size()));

    jdbcTemplate.update(sql, statement -> {
      statement.setArray(1, statement.getConnection().createArrayOf(UUID.name(), ids.toArray()));
      statement.setString(2, tenantId);
    });
  }

  @Override
  protected String entityTable() {
    return ReindexConstants.HOLDING_TABLE;
  }
}
