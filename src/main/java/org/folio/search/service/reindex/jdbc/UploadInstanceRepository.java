package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

@Repository
@Log4j2
public class UploadInstanceRepository extends UploadRangeRepository {

  private static final String SELECT_SQL_TEMPLATE = """
    SELECT i.json
      || jsonb_build_object('tenantId', i.tenant_id,
                            'shared', i.shared,
                            'isBoundWith', i.is_bound_with,
                            'holdings', COALESCE(jsonb_agg(DISTINCT h.json || jsonb_build_object('tenantId', h.tenant_id)) FILTER (WHERE h.json IS NOT NULL), '[]'::jsonb),
                            'items', COALESCE(jsonb_agg(it.json || jsonb_build_object('tenantId', it.tenant_id)) FILTER (WHERE it.json IS NOT NULL), '[]'::jsonb)) as json
    FROM %s i
      LEFT JOIN %s h on h.instance_id = i.id
      LEFT JOIN %s it on it.holding_id = h.id
      WHERE %s
      GROUP BY i.id;
    """;

  private static final String IDS_RANGE_WHERE_CLAUSE = "i.id >= ?::uuid AND i.id <= ?::uuid";
  private static final String INSTANCE_IDS_WHERE_CLAUSE = "i.id IN (%s)";

  private final ConsortiumTenantService consortiumTenantService;

  protected UploadInstanceRepository(JdbcTemplate jdbcTemplate, JsonConverter jsonConverter,
                                     FolioExecutionContext context,
                                     ReindexConfigurationProperties reindexConfig,
                                     ConsortiumTenantService consortiumTenantService) {
    super(jdbcTemplate, jsonConverter, context, reindexConfig);
    this.consortiumTenantService = consortiumTenantService;
  }

  @Override
  public SubResourceResult fetchByTimestamp(String tenant, Timestamp timestamp) {
    return null;
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
    }, rowToMapMapper());
  }

  public List<Map<String, Object>> fetchSharedInstancesWithAllTenantData(List<String> instanceIds) {
    if (instanceIds == null || instanceIds.isEmpty()) {
      return Collections.emptyList();
    }

    // Get central tenant ID for consortium deployments
    var centralTenantId = consortiumTenantService.getCentralTenant(context.getTenantId());
    if (centralTenantId.isEmpty()) {
      // Non-consortium deployment: no shared instances exist
      return Collections.emptyList();
    }

    // Use central tenant's schema for instance table, but allow cross-tenant holdings/items
    var moduleMetadata = context.getFolioModuleMetadata();
    var centralSchema = JdbcUtils.getSchemaName(centralTenantId.get(), moduleMetadata);
    var centralInstanceTable = centralSchema + "." + entityTable();

    // Reuse existing BATCH_OPERATION_SIZE for consistency
    List<Map<String, Object>> allResults = new ArrayList<>();

    for (int i = 0; i < instanceIds.size(); i += BATCH_OPERATION_SIZE) {
      List<String> batch = instanceIds.subList(i,
          Math.min(i + BATCH_OPERATION_SIZE, instanceIds.size()));

      String whereClause = "i.id IN (" + JdbcUtils.getParamPlaceholderForUuid(batch.size())
                         + ") AND i.shared = true";

      // Query central tenant's instances with cross-tenant holdings/items
      String sql = """
          SELECT i.json
            || jsonb_build_object('tenantId', i.tenant_id,
                                  'shared', i.shared,
                                  'isBoundWith', i.is_bound_with,
                                  'holdings', COALESCE(jsonb_agg(DISTINCT h.json ||
                                  jsonb_build_object('tenantId', h.tenant_id))
                                  FILTER (WHERE h.json IS NOT NULL), '[]'::jsonb),
                                  'items', COALESCE(jsonb_agg(it.json || jsonb_build_object('tenantId', it.tenant_id))
                                  FILTER (WHERE it.json IS NOT NULL), '[]'::jsonb)) as json
          FROM %s i
            LEFT JOIN %s h on h.instance_id = i.id
            LEFT JOIN %s it on it.holding_id = h.id
            WHERE %s
            GROUP BY i.id;
          """.formatted(
              centralInstanceTable,
              getFullTableName(context, "holding"),
              getFullTableName(context, "item"),
              whereClause);

      var batchResults = jdbcTemplate.query(sql, ps -> {
        for (int j = 0; j < batch.size(); j++) {
          ps.setObject(j + 1, batch.get(j));
        }
      }, rowToMapMapper());

      allResults.addAll(batchResults);
    }

    return allResults;
  }

  @Override
  public List<Map<String, Object>> fetchByIdRange(String lower, String upper) {
    String memberTenantId = ReindexContext.getMemberTenantId();

    if (memberTenantId != null) {
      // Member tenant reindex: Fetch from central tenant schema only
      // All member tenant data is already in central tenant after merge phase
      return fetchForMemberTenantReindex(lower, upper);
    }

    // Full reindex: Standard fetch from main tables
    var sql = getFetchBySql();
    return jdbcTemplate.query(sql, rowToMapMapper(), lower, upper);
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
    String sql = buildConditionalInstanceQuery(centralSchema);

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
   * Builds SQL query to fetch instances conditionally:
   * - UNION of local instances and shared instances with member holdings
   * - Maintains existing JSON aggregation for holdings/items
   * - Applies UUID range filtering
   */
  private String buildConditionalInstanceQuery(String centralSchema) {
    return """
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
        """.formatted(
            centralSchema, // Local instances table
            centralSchema, // Shared instances table
            centralSchema, // Holdings subquery table
            centralSchema, // Holdings join table
            centralSchema  // Items join table
        );
  }

  /**
   * Executes a fetch query using a different tenant's schema context.
   * This is used during member tenant reindex to fetch from the central tenant's schema
   * where all data has been merged.
   */
  private List<Map<String, Object>> fetchWithTenantContext(String targetTenantId, String lower, String upper) {
    // Build the query using the target tenant's schema
    var moduleMetadata = context.getFolioModuleMetadata();
    var targetSchema = JdbcUtils.getSchemaName(targetTenantId, moduleMetadata);

    // Use the standard SQL template but with the target tenant's schema
    String sql = SELECT_SQL_TEMPLATE.formatted(
      targetSchema + "." + entityTable(),
      targetSchema + ".holding",
      targetSchema + ".item",
      IDS_RANGE_WHERE_CLAUSE
    );

    return jdbcTemplate.query(sql, ps -> {
      ps.setObject(1, lower);
      ps.setObject(2, upper);
    }, rowToMapMapper());
  }

  @Override
  protected List<RangeGenerator.Range> createRanges() {
    var uploadRangeSize = reindexConfig.getUploadRangeSize();
    var rangesCount = (int) Math.ceil((double) countEntities() / uploadRangeSize);
    return RangeGenerator.createUuidRanges(rangesCount);
  }

  @Override
  protected String getFetchBySql() {
    return SELECT_SQL_TEMPLATE.formatted(getFullTableName(context, entityTable()),
      getFullTableName(context, "holding"),
      getFullTableName(context, "item"),
      IDS_RANGE_WHERE_CLAUSE);
  }

  @Override
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return (rs, rowNum) -> jsonConverter.fromJsonToMap(rs.getString("json"));
  }

  @Override
  public List<Map<String, Object>> fetchByIdRangeWithTimestamp(String lower, String upper, Timestamp timestamp) {
    // Instances are not child resources and don't need timestamp filtering for member tenant reindex
    // This method delegates to the standard range-based fetch
    return fetchByIdRange(lower, upper);
  }
}
