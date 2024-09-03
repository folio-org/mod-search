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
import org.folio.search.model.entity.InstanceContributorEntityAgg;
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
public class ContributorRepository extends UploadRangeRepository {

  public static final String SELECT_QUERY = """
    WITH contributor_range AS(
        SELECT * FROM %1$s.contributor WHERE %2$s
    )
    SELECT cr.id, cr.name, cr.name_type_id, cr.authority_id, json_agg(json_build_object(
                'instanceId', ic.instance_id,
                'typeId', NULLIF(ic.type_id, ''),
                'shared', ic.shared,
                'tenantId', ic.tenant_id
            )) AS instances
    FROM %1$s.instance_contributor ic
    JOIN contributor_range cr ON cr.id = ic.contributor_id
    GROUP BY cr.id, cr.name, cr.name_type_id, cr.authority_id;
    """;

  private static final String EMPTY_WHERE_CLAUSE = "true LIMIT ? OFFSET ?";
  private static final String IDS_WHERE_CLAUSE = "id IN (%s)";

  private static final TypeReference<LinkedHashSet<InstanceSubResource>> VALUE_TYPE_REF = new TypeReference<>() { };

  protected ContributorRepository(JdbcTemplate jdbcTemplate, JsonConverter jsonConverter,
                                  FolioExecutionContext context,
                                  ReindexConfigurationProperties reindexConfig) {
    super(jdbcTemplate, jsonConverter, context, reindexConfig);
  }

  @Override
  public ReindexEntityType entityType() {
    return ReindexEntityType.CONTRIBUTOR;
  }

  public List<InstanceContributorEntityAgg> fetchByIds(List<String> ids) {
    if (CollectionUtils.isEmpty(ids)) {
      return Collections.emptyList();
    }
    var sql = SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context),
      IDS_WHERE_CLAUSE.formatted(getParamPlaceholder(ids.size())));
    return jdbcTemplate.query(sql, instanceAggRowMapper(), ids.toArray());
  }

  @Override
  protected String entityTable() {
    return ReindexConstants.CONTRIBUTOR_TABLE;
  }

  @Override
  protected String getFetchBySql() {
    return SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context), EMPTY_WHERE_CLAUSE);
  }

  @Override
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return (rs, rowNum) -> {
      Map<String, Object> contributor = new HashMap<>();
      contributor.put("id", rs.getString("id"));
      contributor.put("name", rs.getString("name"));
      contributor.put("contributorNameTypeId", rs.getString("name_type_id"));
      contributor.put("authorityId", rs.getString("authority_id"));

      var maps = jsonConverter.fromJsonToListOfMaps(rs.getString("instances"));
      contributor.put("instances", maps);

      return contributor;
    };
  }

  private RowMapper<InstanceContributorEntityAgg> instanceAggRowMapper() {
    return (rs, rowNum) -> {
      var id = rs.getString("id");
      var name = rs.getString("name");
      var typeId = rs.getString("name_type_id");
      var authorityId = rs.getString("authority_id");
      var instancesJson = rs.getString("instances");
      Set<InstanceSubResource> instanceSubResources;
      try {
        instanceSubResources = jsonConverter.fromJson(instancesJson, VALUE_TYPE_REF);
      } catch (Exception e) {
        throw new IllegalArgumentException(e);
      }
      return new InstanceContributorEntityAgg(id, name, typeId, authorityId, instanceSubResources);
    };
  }
}
