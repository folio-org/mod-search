package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getParamPlaceholder;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.entity.InstanceSubjectEntityAgg;
import org.folio.search.model.index.InstanceSubResource;
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
    WITH subject_range AS(
        SELECT * FROM %1$s.subject WHERE %2$s
    )
    SELECT sr.id, sr.value, sr.authority_id, json_agg(json_build_object(
                'instanceId', ins.instance_id,
                'shared', ins.shared,
                'tenantId', ins.tenant_id
            )) AS instances
    FROM %1$s.instance_subject ins
    JOIN subject_range sr ON sr.id = ins.subject_id
    GROUP BY sr.id, sr.value, sr.authority_id;
    """;

  private static final String EMPTY_WHERE_CLAUSE = "true LIMIT ? OFFSET ?";
  private static final String IDS_WHERE_CLAUSE = "id IN (%s)";

  private static final TypeReference<LinkedHashSet<InstanceSubResource>> VALUE_TYPE_REF = new TypeReference<>() { };

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

  public List<InstanceSubjectEntityAgg> fetchByIds(List<String> ids) {
    if (CollectionUtils.isEmpty(ids)) {
      return Collections.emptyList();
    }
    var sql = SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context),
      IDS_WHERE_CLAUSE.formatted(getParamPlaceholder(ids.size())));
    return jdbcTemplate.query(sql, instanceAggRowMapper(), ids.toArray());
  }

  @Override
  protected String entityTable() {
    return ReindexConstants.SUBJECT_TABLE;
  }

  @Override
  protected String getFetchBySql() {
    return SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context), EMPTY_WHERE_CLAUSE);
  }

  @Override
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return (rs, rowNum) -> {
      Map<String, Object> subject = new HashMap<>();
      subject.put("id", rs.getString("id"));
      subject.put("value", rs.getString("value"));
      subject.put("authorityId", rs.getString("authority_id"));

      var maps = jsonConverter.fromJsonToListOfMaps(rs.getString("instances"));
      subject.put("instances", maps);

      return subject;
    };
  }


  private RowMapper<InstanceSubjectEntityAgg> instanceAggRowMapper() {
    return (rs, rowNum) -> {
      var id = rs.getString("id");
      var value = rs.getString("value");
      var authorityId = rs.getString("authority_id");
      var instancesJson = rs.getString("instances");
      Set<InstanceSubResource> instanceSubResources;
      try {
        instanceSubResources = jsonConverter.fromJson(instancesJson, VALUE_TYPE_REF);
      } catch (Exception e) {
        throw new IllegalArgumentException(e);
      }
      return new InstanceSubjectEntityAgg(id, value, authorityId, instanceSubResources);
    };
  }
}
