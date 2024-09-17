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
    WITH classification_range AS(
        SELECT * FROM %1$s.classification WHERE %2$s
    )
    SELECT cr.id as id, cr.number as number, cr.type_id as type_id, json_agg(json_build_object(
                'instanceId', ic.instance_id,
                'shared', ic.shared,
                'tenantId', ic.tenant_id
            )) AS instances
    FROM %1$s.instance_classification ic
    JOIN classification_range cr ON cr.id = ic.classification_id
    GROUP BY cr.id, cr.number, cr.type_id;
    """;

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
    return jdbcTemplate.query(sql, instanceClassificationAggRowMapper(), ids.toArray());
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
