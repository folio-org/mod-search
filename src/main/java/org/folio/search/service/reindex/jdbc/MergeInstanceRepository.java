package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.util.List;
import java.util.Map;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MergeInstanceRepository extends MergeRangeRepository {

  private static final String INSERT_SQL = """
      INSERT INTO %s
      VALUES (?, ?, ?, ?, ?::json);
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
  public void saveEntities(String tenantId, List<Map<String, Object>> entities) {
    var fullTableName = getFullTableName(context, entityTable());
    var sql = INSERT_SQL.formatted(fullTableName);
    var shared = consortiumTenantProvider.isCentralTenant(tenantId);

    jdbcTemplate.batchUpdate(sql, entities, BATCH_OPERATION_SIZE,
      (statement, entity) -> {
        statement.setObject(1, entity.get("id"));
        statement.setString(2, tenantId);
        statement.setObject(3, shared);
        statement.setObject(4, entity.get("isBoundWith"));
        statement.setString(5, jsonConverter.toJson(entity));
      });
  }

  @Override
  protected String entityTable() {
    return ReindexConstants.INSTANCE_TABLE;
  }
}
