package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.RangeGenerator;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.utils.JdbcUtils;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
public class UploadInstanceRepository extends UploadRangeRepository {

  private static final String SELECT_SQL_TEMPLATE = """
          WITH aggregated_holdings AS (
            SELECT
                h.instance_id,
                jsonb_agg(                   h.json || jsonb_build_object('tenantId', h.tenant_id)
                ) AS holdings_json
            FROM %2$s h
            WHERE %4$s
            GROUP BY h.instance_id
          ),
          aggregated_items AS (
            SELECT
                it.instance_id,
                jsonb_agg(
                    it.json || jsonb_build_object('tenantId', it.tenant_id)
                ) AS items_json
            FROM %3$s it
            WHERE %5$s
            GROUP BY it.instance_id
          )
          SELECT
              i.json || jsonb_build_object(
                  'tenantId', i.tenant_id,
                  'shared', i.shared,
                  'isBoundWith', i.is_bound_with,
                  'holdings', COALESCE(ah.holdings_json, '[]'::jsonb),
                  'items', COALESCE(ai.items_json, '[]'::jsonb)
              ) AS json
          FROM %1$s i
          LEFT JOIN aggregated_holdings ah ON ah.instance_id = i.id
          LEFT JOIN aggregated_items ai ON ai.instance_id = i.id
          WHERE %6$s;
    """;

  private static final String IDS_RANGE_WHERE_CLAUSE = "%1$s >= ?::uuid AND %1$s <= ?::uuid";
  private static final String INSTANCE_IDS_WHERE_CLAUSE = "%s IN (%s)";
  private static final String ITEM_NOT_DELETED_FILTER = " AND it.is_deleted = false";

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

  public List<Map<String, Object>> fetchByIds(Collection<String> ids) {
    log.info("Fetching instances by ids: {} on tenant: {}", ids, context.getTenantId());
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }
    var instanceWhereClause = INSTANCE_IDS_WHERE_CLAUSE.formatted("i.id",
      JdbcUtils.getParamPlaceholderForUuid(ids.size()));
    var itemWhereClause = INSTANCE_IDS_WHERE_CLAUSE.formatted("it.instance_id",
      JdbcUtils.getParamPlaceholderForUuid(ids.size())) + ITEM_NOT_DELETED_FILTER;
    var holdingsWhereClause = INSTANCE_IDS_WHERE_CLAUSE.formatted("h.instance_id",
      JdbcUtils.getParamPlaceholderForUuid(ids.size()));
    var sql = SELECT_SQL_TEMPLATE.formatted(getFullTableName(context, entityTable()),
      getFullTableName(context, "holding"),
      getFullTableName(context, "item"),
      holdingsWhereClause, itemWhereClause, instanceWhereClause);
    return jdbcTemplate.query(sql, ps -> {
      int i = 1;
      for (int paramSet = 0; paramSet < 3; paramSet++) {
        for (String id : ids) {
          ps.setObject(i++, id);
        }
      }
    }, rowToMapMapper());
  }

  @Override
  public List<Map<String, Object>> fetchByIdRange(String lower, String upper) {
    var sql = getFetchBySql();
    return jdbcTemplate.query(sql, rowToMapMapper(), lower, upper, lower, upper, lower, upper);
  }

  @Override
  protected String getFetchBySql() {
    var instanceWhereClause = IDS_RANGE_WHERE_CLAUSE.formatted("i.id");
    var itemWhereClause = IDS_RANGE_WHERE_CLAUSE.formatted("it.instance_id");
    var holdingsWhereClause = IDS_RANGE_WHERE_CLAUSE.formatted("h.instance_id");
    return SELECT_SQL_TEMPLATE.formatted(getFullTableName(context, entityTable()),
      getFullTableName(context, "holding"),
      getFullTableName(context, "item"),
      holdingsWhereClause,
      itemWhereClause,
      instanceWhereClause);
  }

  @Override
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return (rs, rowNum) -> jsonConverter.fromJsonToMap(rs.getString("json"));
  }

  @Override
  protected List<RangeGenerator.Range> createRanges() {
    var uploadRangeSize = reindexConfig.getUploadRangeSize();
    var rangesCount = (int) Math.ceil((double) countEntities() / uploadRangeSize);
    return RangeGenerator.createUuidRanges(rangesCount);
  }
}
