package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

  private static final String SELECT_SQL_TEMPLATE = """
    SELECT i.instance_json
      || jsonb_build_object('tenantId', i.tenant_id)
      || jsonb_build_object('shared', i.shared)
      || jsonb_build_object('isBoundWith', i.is_bound_with)
      || jsonb_build_object('holdings', jsonb_agg(h.holding_json || jsonb_build_object('tenantId', h.tenant_id)))
      || jsonb_build_object('items', jsonb_agg(it.item_json || jsonb_build_object('tenantId', it.tenant_id))) as json
        FROM %s i
        LEFT JOIN %s h on h.instance_id = i.id
        LEFT JOIN %s it on it.holding_id = h.id
        WHERE %s
        GROUP BY i.id LIMIT ? OFFSET ?;
    """;

  private static final String UPSERT_SQL = """
      INSERT INTO %s (id, tenant_id, shared, is_bound_with, instance_json)
      VALUES (?::uuid, ?, ?, ?, ?::jsonb)
      ON CONFLICT (id)
      DO UPDATE SET shared = EXCLUDED.shared,
      is_bound_with = EXCLUDED.is_bound_with,
      instance_json = EXCLUDED.instance_json;
    """;

  private static final String EMPTY_WHERE_CLAUSE = "true";
  private static final String INSTANCE_IDS_WHERE_CLAUSE = "i.id IN (%s)";

  private final JdbcTemplate jdbcTemplate;

  protected UploadInstanceRepository(JdbcTemplate jdbcTemplate, JsonConverter jsonConverter,
                                     FolioExecutionContext context,
                                     ReindexConfigurationProperties reindexConfig) {
    super(jdbcTemplate, jsonConverter, context, reindexConfig);
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public ReindexEntityType entityType() {
    return ReindexEntityType.INSTANCE;
  }

  @Override
  public void upsert(List<Map<String, Object>> records) {

  }

  @Override
  public void delete(List<String> ids) {

  }

  public List<Map<String, Object>> fetchByIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }
    var whereClause = INSTANCE_IDS_WHERE_CLAUSE.formatted(ids.stream().map(v -> "?::uuid")
      .collect(Collectors.joining(", ")));
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

  public void upsert(String id, String tenant, boolean shared, Map<String, Object> instanceMap) {
    var sql = UPSERT_SQL.formatted(getFullTableName(context, entityTable()));
    jdbcTemplate.update(sql, id, tenant, shared, false, jsonConverter.toJson(instanceMap));
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

  @Override
  protected String entityTable() {
    return ReindexConstants.INSTANCE_TABLE;
  }
}
