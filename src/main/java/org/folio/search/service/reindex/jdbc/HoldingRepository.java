package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.util.Arrays;
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
    var fullTableName = getFullTableName(context, entityTable());
    log.info("saveEntities:: fullTableName {}", fullTableName);
    var sql = INSERT_SQL.formatted(fullTableName);
    log.info("saveEntities:: Save Holdings {}", entities);

    try {
      var count = jdbcTemplate.batchUpdate(sql, entities, BATCH_OPERATION_SIZE,
        (statement, entity) -> {
          statement.setObject(1, entity.get("id"));
          statement.setString(2, tenantId);
          statement.setObject(3, entity.get("instanceId"));
          statement.setString(4, jsonConverter.toJson(entity));
        });
      log.info(String.format("Saved Holdings count %s", Arrays.deepToString(count)));
    } catch (Exception ex) {
      log.error("Failed to save Holdings {}", entities, ex);
    }
  }

  @Override
  protected String entityTable() {
    return ReindexConstants.HOLDING_TABLE;
  }
}
