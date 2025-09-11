package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;
import static org.folio.search.utils.JdbcUtils.getSchemaName;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

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
      json = EXCLUDED.json,
      last_updated_date = CURRENT_TIMESTAMP;
    """;

  private static final String SELECT_BY_UPDATED_QUERY = """
    SELECT id, tenant_id, instance_id, holding_id, json, last_updated_date, is_deleted
    FROM %s.item
    WHERE last_updated_date > ?
    ORDER BY last_updated_date ASC
    """;

  protected ItemRepository(JdbcTemplate jdbcTemplate, JsonConverter jsonConverter, FolioExecutionContext context,
                           SearchConfigurationProperties searchConfigurationProperties) {
    super(jdbcTemplate, jsonConverter, context, searchConfigurationProperties);
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
  public void saveEntities(String tenantId, List<Map<String, Object>> entities) {
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
  public SubResourceResult fetchByTimestamp(String tenant, Timestamp timestamp) {
    var sql = SELECT_BY_UPDATED_QUERY.formatted(getSchemaName(tenant, context.getFolioModuleMetadata()));
    var records = jdbcTemplate.query(sql, itemRowMapper(), timestamp);
    var lastUpdateDate = records.isEmpty() ? null : records.get(records.size() - 1).get(LAST_UPDATED_DATE_FIELD);
    return new SubResourceResult(records, (Timestamp) lastUpdateDate);
  }

  private RowMapper<Map<String, Object>> itemRowMapper() {
    return (rs, rowNum) -> {
      Map<String, Object> item = new HashMap<>();
      item.put("id", rs.getString("id"));
      item.put("tenantId", rs.getString("tenant_id"));
      item.put("instanceId", rs.getString("instance_id"));
      item.put("holdingId", rs.getString("holding_id"));
      item.put("isDeleted", rs.getBoolean("is_deleted"));
      item.put(LAST_UPDATED_DATE_FIELD, rs.getTimestamp("last_updated_date"));

      var jsonContent = jsonConverter.fromJsonToMap(rs.getString("json"));
      item.putAll(jsonContent);

      return item;
    };
  }
}
