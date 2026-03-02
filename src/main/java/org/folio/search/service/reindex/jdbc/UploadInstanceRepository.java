package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.search.service.reindex.RangeGenerator;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.service.reindex.ReindexContext;
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
  private static final String INSTANCE_NOT_DELETED_FILTER = "i.is_deleted = false AND ";

  private static final String CONDITIONAL_INSTANCE_QUERY = """
        SELECT combined.json
          || jsonb_build_object('tenantId', combined.tenant_id,
                                'shared', combined.shared,
                                'isBoundWith', combined.is_bound_with,
                                'holdings', COALESCE(jsonb_agg(DISTINCT h.json ||
                                    jsonb_build_object('tenantId', h.tenant_id))
                                    FILTER (WHERE h.json IS NOT NULL), '[]'::jsonb),
                                'items', COALESCE(jsonb_agg(it.json ||
                                    jsonb_build_object('tenantId', it.tenant_id))
                                    FILTER (WHERE it.json IS NOT NULL), '[]'::jsonb)) as json
        FROM (
            -- Local instances from central tenant (after merge)
            SELECT i.id, i.tenant_id, i.shared, i.is_bound_with, i.json
            FROM %s.instance i
            WHERE i.id >= ?::uuid AND i.id <= ?::uuid
              AND i.tenant_id = ?
            UNION ALL
            -- Shared instances that have holdings for member tenant
            SELECT i.id, i.tenant_id, i.shared, i.is_bound_with, i.json
            FROM %s.instance i
            WHERE i.id >= ?::uuid AND i.id <= ?::uuid
              AND i.shared = true
              AND EXISTS (
                SELECT 1 FROM %s.holding h
                WHERE h.instance_id = i.id AND h.tenant_id = ?
              )
        ) combined
        LEFT JOIN %s.holding h ON h.instance_id = combined.id
        LEFT JOIN %s.item it ON it.holding_id = h.id
        GROUP BY combined.id, combined.tenant_id, combined.shared,
                 combined.is_bound_with, combined.json
        """;

  private final ConsortiumTenantService consortiumTenantService;

  protected UploadInstanceRepository(JdbcTemplate jdbcTemplate, JsonConverter jsonConverter,
                                     FolioExecutionContext context,
                                     ReindexConfigurationProperties reindexConfig,
                                     ConsortiumTenantService consortiumTenantService) {
    super(jdbcTemplate, jsonConverter, context, reindexConfig);
    this.consortiumTenantService = consortiumTenantService;
  }

  @Override
  public ReindexEntityType entityType() {
    return ReindexEntityType.INSTANCE;
  }

  @Override
  protected String entityTable() {
    return ReindexConstants.INSTANCE_TABLE;
  }

  @Override
  protected Optional<String> stagingEntityTable() {
    return Optional.of(ReindexConstants.STAGING_INSTANCE_TABLE);
  }

  public List<Map<String, Object>> fetchByIds(Collection<String> ids) {
    log.debug("Fetching instances by ids: {} on tenant: {}", ids, context.getTenantId());
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }
    var instanceWhereClause = INSTANCE_NOT_DELETED_FILTER + INSTANCE_IDS_WHERE_CLAUSE.formatted("i.id",
      JdbcUtils.getParamPlaceholderForUuid(ids.size()));
    var itemWhereClause = INSTANCE_IDS_WHERE_CLAUSE.formatted("it.instance_id",
      JdbcUtils.getParamPlaceholderForUuid(ids.size())) + ITEM_NOT_DELETED_FILTER;
    var holdingsWhereClause = INSTANCE_IDS_WHERE_CLAUSE.formatted("h.instance_id",
      JdbcUtils.getParamPlaceholderForUuid(ids.size()));
    var sql = SELECT_SQL_TEMPLATE.formatted(getFullTableName(context, entityTable()),
      getFullTableName(context, "holding"),
      getFullTableName(context, "item"),
      holdingsWhereClause, itemWhereClause, instanceWhereClause);
    log.debug("fetchByIds:: SQL query: {}", sql);
    return jdbcTemplate.query(sql, ps -> {
      int i = 1;
      for (int paramSet = 0; paramSet < 3; paramSet++) {
        for (String id : ids) {
          ps.setObject(i++, id);
        }
      }
    }, rowToMapMapper());
  }

  private List<Map<String, Object>> fetchForMemberTenantReindex(String lower, String upper) {
    var memberTenantId = ReindexContext.getMemberTenantId();
    var centralTenantId = consortiumTenantService.getCentralTenant(context.getTenantId())
      .orElseThrow(() -> new IllegalStateException("No central tenant found"));

    return fetchConditionalInstances(centralTenantId, memberTenantId, lower, upper);
  }

  /**
   * Fetches instances conditionally for member tenant reindex:
   * 1. Local instances (tenant_id = memberTenantId)
   * 2. Shared instances that have holdings belonging to memberTenantId
   *
   * @param centralTenantId Central tenant where merged data resides
   * @param memberTenantId Member tenant being reindexed
   * @param lower Lower UUID bound for range processing
   * @param upper Upper UUID bound for range processing
   * @return List of instance maps with holdings and items
   */
  private List<Map<String, Object>> fetchConditionalInstances(
    String centralTenantId, String memberTenantId, String lower, String upper) {

    log.info("fetchConditionalInstances:: Fetching instances for member tenant reindex "
        + "[memberTenant: {}, centralTenant: {}, range: {}-{}]",
      memberTenantId, centralTenantId, lower, upper);

    var moduleMetadata = context.getFolioModuleMetadata();
    var centralSchema = JdbcUtils.getSchemaName(centralTenantId, moduleMetadata);

    // SQL to fetch both local and relevant shared instances
    var sql = buildConditionalInstanceQuery(centralSchema);

    var results = jdbcTemplate.query(sql, ps -> {
      ps.setObject(1, lower);        // UUID range lower bound
      ps.setObject(2, upper);        // UUID range upper bound
      ps.setString(3, memberTenantId); // For local instances
      ps.setObject(4, lower);        // UUID range lower bound for shared instances
      ps.setObject(5, upper);        // UUID range upper bound for shared instances
      ps.setString(6, memberTenantId); // For shared instances with member holdings
    }, rowToMapMapper());

    log.debug("fetchConditionalInstances:: Found {} instances for range {}-{}",
      results.size(), lower, upper);

    return results;
  }

  /**
   * Builds SQL query to fetch instances conditionally.
   * - UNION of local instances and shared instances with member holdings
   * - Maintains existing JSON aggregation for holdings/items
   * - Applies UUID range filtering
   */
  private String buildConditionalInstanceQuery(String centralSchema) {
    return CONDITIONAL_INSTANCE_QUERY.formatted(
      centralSchema, // Local instances table
      centralSchema, // Shared instances table
      centralSchema, // Holdings subquery table
      centralSchema, // Holdings join table
      centralSchema  // Items join table
    );
  }

  @Override
  public List<Map<String, Object>> fetchByIdRange(String lower, String upper) {
    var memberTenantId = ReindexContext.getMemberTenantId();

    if (memberTenantId != null) {
      // Member tenant reindex: Fetch from central tenant schema only
      // All member tenant data is already in central tenant after merge phase
      return fetchForMemberTenantReindex(lower, upper);
    }

    // Full reindex: Standard fetch from main tables
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

  @Override
  public List<Map<String, Object>> fetchByIdRangeWithTimestamp(String lower, String upper, Timestamp timestamp) {
    // Instances are not child resources and don't need timestamp filtering for member tenant reindex
    // This method delegates to the standard range-based fetch
    return fetchByIdRange(lower, upper);
  }
}
