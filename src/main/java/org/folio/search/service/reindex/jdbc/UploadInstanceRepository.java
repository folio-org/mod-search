package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.utils.JdbcUtils;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UploadInstanceRepository extends UploadRangeRepository {

  private static final String SELECT_SQL_TEMPLATE = """
    SELECT i.json
      || jsonb_build_object('tenantId', i.tenant_id)
      || jsonb_build_object('shared', i.shared)
      || jsonb_build_object('isBoundWith', i.is_bound_with)
      || jsonb_build_object('holdings', COALESCE(jsonb_agg(DISTINCT h.json || jsonb_build_object('tenantId', h.tenant_id)) FILTER (WHERE h.json IS NOT NULL), '[]'::jsonb))
      || jsonb_build_object('items', COALESCE(jsonb_agg(it.json || jsonb_build_object('tenantId', it.tenant_id)) FILTER (WHERE it.json IS NOT NULL), '[]'::jsonb)) as json
    FROM %s i
      LEFT JOIN %s h on h.instance_id = i.id
      LEFT JOIN %s it on it.holding_id = h.id
      WHERE %s
      GROUP BY i.id LIMIT ? OFFSET ?;
    """;

  private static final String EMPTY_WHERE_CLAUSE = "true";
  private static final String INSTANCE_IDS_WHERE_CLAUSE = "i.id IN (%s)";

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
  protected String entityTable() {
    return ReindexConstants.INSTANCE_TABLE;
  }

  public List<Map<String, Object>> fetchByIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }
    var whereClause = INSTANCE_IDS_WHERE_CLAUSE.formatted(JdbcUtils.getParamPlaceholderForUuid(ids.size()));
    var sql = SELECT_SQL_TEMPLATE.formatted(getFullTableName(context, entityTable()),
      getFullTableName(context, "holding"),
      getFullTableName(context, "item"),
      whereClause);
    return jdbcTemplate.query(sql, ps -> {
      int i = 1;
      for (; i <= ids.size(); i++) {
        ps.setObject(i, ids.get(i - 1)); // set instance ids
      }
      ps.setInt(i++, ids.size()); // set limit
      ps.setInt(i, 0); // set offset
    }, rowToMapMapper());
  }

  @Override
  protected String getFetchBySql() {
    return SELECT_SQL_TEMPLATE.formatted(getFullTableName(context, entityTable()),
      getFullTableName(context, "holding"),
      getFullTableName(context, "item"),
      EMPTY_WHERE_CLAUSE);
  }

  @Override
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return (rs, rowNum) -> jsonConverter.fromJsonToMap(rs.getString("json"));
  }
}
