package org.folio.search.service.reindex.jdbc;

import java.util.HashMap;
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
public class SubjectRepository extends UploadRangeRepository {

  public static final String SELECT_QUERY = """
    WITH subject_range AS(
        SELECT * FROM %1$s.subject LIMIT ? OFFSET ?
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
  protected String getFetchBySql() {
    return SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context));
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
}
