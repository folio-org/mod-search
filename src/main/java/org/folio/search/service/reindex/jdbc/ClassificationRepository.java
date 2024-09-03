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
import org.folio.search.model.entity.InstanceClassificationEntityAgg;
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

  private static final String EMPTY_WHERE_CLAUSE = "true LIMIT ? OFFSET ?";
  private static final String IDS_WHERE_CLAUSE = "id IN (%s)";
  private static final TypeReference<LinkedHashSet<InstanceSubResource>> VALUE_TYPE_REF = new TypeReference<>() { };
  private static final String CLASSIFICATION_TYPE_COLUMN = "type_id";
  private static final String CLASSIFICATION_NUMBER_COLUMN = "number";

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
    return SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context), EMPTY_WHERE_CLAUSE);
  }

  @Override
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return (rs, rowNum) -> {
      Map<String, Object> classification = new HashMap<>();
      classification.put("id", rs.getString("id"));
      classification.put("number", rs.getString("number"));
      classification.put("typeId", rs.getString("type_id"));

      var maps = jsonConverter.fromJsonToListOfMaps(rs.getString("instances"));
      classification.put("instances", maps);

      return classification;
    };
  }

  private RowMapper<InstanceClassificationEntityAgg> instanceClassificationAggRowMapper() {
    return (rs, rowNum) -> {
      var id = rs.getString("id");
      var number = rs.getString(CLASSIFICATION_NUMBER_COLUMN);
      var typeId = rs.getString(CLASSIFICATION_TYPE_COLUMN);
      var instancesJson = rs.getString("instances");
      Set<InstanceSubResource> instanceSubResources;
      try {
        instanceSubResources = jsonConverter.fromJson(instancesJson, VALUE_TYPE_REF);
      } catch (Exception e) {
        throw new IllegalArgumentException(e);
      }
      return new InstanceClassificationEntityAgg(id, typeId, number, instanceSubResources);
    };
  }
}
