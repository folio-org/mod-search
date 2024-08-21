package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.util.Map;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UploadInstanceRepository extends UploadRangeRepository {

  private static final String SELECT_SQL = """
    SELECT i.instance_json
      || jsonb_build_object('tenantId', i.tenant_id)
      || jsonb_build_object('shared', i.shared)
      || jsonb_build_object('isBoundWith', i.is_bound_with)
      || jsonb_build_object('holdings', jsonb_agg(h.holding_json || jsonb_build_object('tenantId', h.tenant_id)))
      || jsonb_build_object('items', jsonb_agg(it.item_json || jsonb_build_object('tenantId', it.tenant_id))) as json
        FROM %s i
        JOIN %s h on h.instance_id = i.id
        JOIN %s it on it.holding_id = h.id
        GROUP BY i.id LIMIT ? OFFSET ?;
    """;

  protected UploadInstanceRepository(JdbcTemplate jdbcTemplate, JsonConverter jsonConverter,
                                     FolioExecutionContext context,
                                     ReindexConfigurationProperties reindexConfig) {
    super(jdbcTemplate, jsonConverter, context, reindexConfig);
  }

  @Override
  public ReindexEntityType entityType() {
    return ReindexEntityType.INSTANCE;
  }

  @Override
  protected String getFetchBySql() {
    return SELECT_SQL.formatted(getFullTableName(context, entityTable()),
      getFullTableName(context, "holding"),
      getFullTableName(context, "item"));
  }

  @Override
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return (rs, rowNum) -> jsonConverter.fromJsonToMap(rs.getString("json"));
  }

  @Override
  protected String entityTable() {
    return ReindexConstants.INSTANCE_TABLE;
  }
}
