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
import org.folio.search.model.entity.InstanceClassificationEntityAgg;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.utils.JdbcUtils;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ClassificationRepository extends UploadRangeRepository {

  private static final String SELECT_QUERY = """
    SELECT c.id as id, c.number as number, c.type_id as type_id, json_agg(json_build_object(
                'instanceId', ic.instance_id,
                'shared', ic.shared,
                'tenantId', ic.tenant_id
            )) AS instances
    FROM %1$s.instance_classification ic
    JOIN %1$s.classification c ON c.id = ic.classification_id
    WHERE %2$s
    GROUP BY c.id;
    """;

  private static final String ID_RANGE_WHERE_CLAUSE = "ic.classification_id >= ? AND ic.classification_id <= ? "
                                                      + "AND c.id >= ? AND c.id <= ?";
  private static final String IDS_WHERE_CLAUSE = "ic.classification_id IN (%1$s) AND c.id IN (%1$s)";

  protected ClassificationRepository(JdbcTemplate jdbcTemplate,
                                     JsonConverter jsonConverter,
                                     FolioExecutionContext context,
                                     ReindexConfigurationProperties reindexConfig) {
    super(jdbcTemplate, jsonConverter, context, reindexConfig);
  }

  @Override
  public ReindexEntityType entityType() {
    return ReindexEntityType.CLASSIFICATION;
  }

  @Override
  protected String entityTable() {
    return ReindexConstants.CLASSIFICATION_TABLE;
  }

  @Override
  protected Optional<String> subEntityTable() {
    return Optional.of(ReindexConstants.INSTANCE_CLASSIFICATION_TABLE);
  }

  public List<InstanceClassificationEntityAgg> fetchByIds(List<String> ids) {
    if (CollectionUtils.isEmpty(ids)) {
      return Collections.emptyList();
    }
    var sql = SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context),
      IDS_WHERE_CLAUSE.formatted(getParamPlaceholder(ids.size())));
    return jdbcTemplate.query(sql, instanceClassificationAggRowMapper(), ListUtils.union(ids, ids).toArray());
  }

  @Override
  public List<Map<String, Object>> fetchByIdRange(String lower, String upper) {
    var sql = getFetchBySql();
    return jdbcTemplate.query(sql, rowToMapMapper(), lower, upper, lower, upper);
  }

  @Override
  protected String getFetchBySql() {
    return SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context), ID_RANGE_WHERE_CLAUSE);
  }

  @Override
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return (rs, rowNum) -> {
      Map<String, Object> classification = new HashMap<>();
      classification.put("id", getId(rs));
      classification.put("number", getNumber(rs));
      classification.put("typeId", getTypeId(rs));

      var maps = jsonConverter.fromJsonToListOfMaps(getInstances(rs));
      classification.put("instances", maps);

      return classification;
    };
  }

  private RowMapper<InstanceClassificationEntityAgg> instanceClassificationAggRowMapper() {
    return (rs, rowNum) -> new InstanceClassificationEntityAgg(
      getId(rs),
      getTypeId(rs),
      getNumber(rs),
      parseInstanceSubResources(getInstances(rs))
    );
  }

  private String getId(ResultSet rs) throws SQLException {
    return rs.getString("id");
  }

  private String getTypeId(ResultSet rs) throws SQLException {
    return rs.getString("type_id");
  }

  private String getNumber(ResultSet rs) throws SQLException {
    return rs.getString("number");
  }

  private String getInstances(ResultSet rs) throws SQLException {
    return rs.getString("instances");
  }
}
