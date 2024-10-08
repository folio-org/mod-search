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
import org.folio.search.model.entity.InstanceSubjectEntityAgg;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.utils.JdbcUtils;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SubjectRepository extends UploadRangeRepository {

  public static final String SELECT_QUERY = """
    SELECT s.id, s.value, s.authority_id, s.source_id, s.type_id, json_agg(json_build_object(
                'instanceId', ins.instance_id,
                'shared', ins.shared,
                'tenantId', ins.tenant_id
            )) AS instances
    FROM %1$s.instance_subject ins
    JOIN %1$s.subject s ON s.id = ins.subject_id
    WHERE %2$s
    GROUP BY s.id;
    """;

  private static final String ID_RANGE_WHERE_CLAUSE = "ins.subject_id >= ? AND ins.subject_id <= ? "
                                                      + "AND s.id >= ? AND s.id <= ?";
  private static final String IDS_WHERE_CLAUSE = "ins.subject_id IN (%1$s) AND s.id IN (%1$s)";


  protected SubjectRepository(JdbcTemplate jdbcTemplate,
                              JsonConverter jsonConverter,
                              FolioExecutionContext context,
                              ReindexConfigurationProperties reindexConfig) {
    super(jdbcTemplate, jsonConverter, context, reindexConfig);
  }

  @Override
  public ReindexEntityType entityType() {
    return ReindexEntityType.SUBJECT;
  }

  @Override
  protected String entityTable() {
    return ReindexConstants.SUBJECT_TABLE;
  }

  @Override
  protected Optional<String> subEntityTable() {
    return Optional.of(ReindexConstants.INSTANCE_SUBJECT_TABLE);
  }

  public List<InstanceSubjectEntityAgg> fetchByIds(List<String> ids) {
    if (CollectionUtils.isEmpty(ids)) {
      return Collections.emptyList();
    }
    var sql = SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context),
      IDS_WHERE_CLAUSE.formatted(getParamPlaceholder(ids.size())));
    return jdbcTemplate.query(sql, instanceAggRowMapper(), ListUtils.union(ids, ids).toArray());
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
      Map<String, Object> subject = new HashMap<>();
      subject.put("id", getId(rs));
      subject.put("value", getValue(rs));
      subject.put("authorityId", getAuthorityId(rs));

      var maps = jsonConverter.fromJsonToListOfMaps(getInstances(rs));
      subject.put("instances", maps);

      return subject;
    };
  }

  private RowMapper<InstanceSubjectEntityAgg> instanceAggRowMapper() {
    return (rs, rowNum) -> new InstanceSubjectEntityAgg(
      getId(rs),
      getValue(rs),
      getAuthorityId(rs),
      getSourceId(rs),
      getTypeId(rs),
      parseInstanceSubResources(getInstances(rs))
    );
  }

  private String getId(ResultSet rs) throws SQLException {
    return rs.getString("id");
  }

  private String getValue(ResultSet rs) throws SQLException {
    return rs.getString("value");
  }

  private String getAuthorityId(ResultSet rs) throws SQLException {
    return rs.getString("authority_id");
  }

  private String getSourceId(ResultSet rs) throws SQLException {
    return rs.getString("source_id");
  }

  private String getTypeId(ResultSet rs) throws SQLException {
    return rs.getString("type_id");
  }

  private String getInstances(ResultSet rs) throws SQLException {
    return rs.getString("instances");
  }
}
