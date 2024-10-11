package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getParamPlaceholder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.entity.InstanceContributorEntityAgg;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.utils.JdbcUtils;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ContributorRepository extends UploadRangeRepository {

  public static final String SELECT_QUERY = """
    SELECT
        c.id,
        c.name,
        c.name_type_id,
        c.authority_id,
        json_agg(
            json_build_object(
                'count', sub.instance_count,
                'typeId', sub.type_ids,
                'shared', sub.shared,
                'tenantId', sub.tenant_id
            )
        ) AS instances
    FROM
        (
            SELECT
                ins.contributor_id,
                ins.tenant_id,
                ins.shared,
                array_agg(DISTINCT ins.type_id) FILTER (WHERE ins.type_id <> '') as type_ids,
                COUNT(DISTINCT ins.instance_id) AS instance_count
            FROM
                %1$s.instance_contributor ins
            WHERE
                %2$s
            GROUP BY
                ins.contributor_id,
                ins.tenant_id,
                ins.shared
        ) sub
    JOIN
        %1$s.contributor c ON c.id = sub.contributor_id
    WHERE
        %3$s
    GROUP BY
        c.id;
    """;

  private static final String ID_RANGE_INS_WHERE_CLAUSE = "ins.contributor_id >= ? AND ins.contributor_id <= ?";
  private static final String ID_RANGE_CONTR_WHERE_CLAUSE = "c.id >= ? AND c.id <= ?";
  private static final String IDS_INS_WHERE_CLAUSE = "ins.contributor_id IN (%1$s)";
  private static final String IDS_CONTR_WHERE_CLAUSE = "c.id IN (%1$s)";

  protected ContributorRepository(JdbcTemplate jdbcTemplate, JsonConverter jsonConverter,
                                  FolioExecutionContext context,
                                  ReindexConfigurationProperties reindexConfig) {
    super(jdbcTemplate, jsonConverter, context, reindexConfig);
  }

  @Override
  public ReindexEntityType entityType() {
    return ReindexEntityType.CONTRIBUTOR;
  }

  @Override
  protected String entityTable() {
    return ReindexConstants.CONTRIBUTOR_TABLE;
  }

  @Override
  protected Optional<String> subEntityTable() {
    return Optional.of(ReindexConstants.INSTANCE_CONTRIBUTOR_TABLE);
  }

  public List<InstanceContributorEntityAgg> fetchByIds(List<String> ids) {
    if (CollectionUtils.isEmpty(ids)) {
      return Collections.emptyList();
    }
    var sql = SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context),
      IDS_INS_WHERE_CLAUSE.formatted(getParamPlaceholder(ids.size())),
      IDS_CONTR_WHERE_CLAUSE.formatted(getParamPlaceholder(ids.size()))
    );
    return jdbcTemplate.query(sql, instanceAggRowMapper(), ListUtils.union(ids, ids).toArray());
  }

  @Override
  public List<Map<String, Object>> fetchByIdRange(String lower, String upper) {
    var sql = getFetchBySql();
    return jdbcTemplate.query(sql, rowToMapMapper(), lower, upper, lower, upper);
  }

  @Override
  protected String getFetchBySql() {
    return SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context),
      ID_RANGE_INS_WHERE_CLAUSE, ID_RANGE_CONTR_WHERE_CLAUSE);
  }

  @Override
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return (rs, rowNum) -> {
      Map<String, Object> contributor = new HashMap<>();
      contributor.put("id", getId(rs));
      contributor.put("name", getName(rs));
      contributor.put("contributorNameTypeId", getNameTypeId(rs));
      contributor.put("authorityId", getAuthorityId(rs));

      var maps = jsonConverter.fromJsonToListOfMaps(getInstances(rs));
      contributor.put("instances", maps);

      return contributor;
    };
  }

  private RowMapper<InstanceContributorEntityAgg> instanceAggRowMapper() {
    return (rs, rowNum) -> new InstanceContributorEntityAgg(
      getId(rs),
      getName(rs),
      getNameTypeId(rs),
      getAuthorityId(rs),
      parseInstanceSubResources(getInstances(rs))
    );
  }

  private String getId(ResultSet rs) throws SQLException {
    return rs.getString("id");
  }

  private String getName(ResultSet rs) throws SQLException {
    return rs.getString("name");
  }

  private String getNameTypeId(ResultSet rs) throws SQLException {
    return rs.getString("name_type_id");
  }

  private String getAuthorityId(ResultSet rs) throws SQLException {
    return rs.getString("authority_id");
  }

  private String getInstances(ResultSet rs) throws SQLException {
    return rs.getString("instances");
  }

}
