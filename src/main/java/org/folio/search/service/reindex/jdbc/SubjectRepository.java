package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getParamPlaceholder;
import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_SOURCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_TYPE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_VALUE_FIELD;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.entity.InstanceSubjectEntityAgg;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.utils.JdbcUtils;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
public class SubjectRepository extends UploadRangeRepository implements InstanceChildResourceRepository {

  // TODO: override shared based on tenant id
  private static final String SELECT_QUERY = """
    SELECT
        s.id,
        s.value,
        s.authority_id,
        s.source_id,
        s.type_id,
        json_agg(
            json_build_object(
                'count', sub.instance_count,
                'shared', false,
                'tenantId', sub.tenant_id
            )
        ) AS instances
    FROM
        (
            SELECT
                ins.subject_id,
                ins.tenant_id,
                sum(ins.count) AS instance_count
            FROM
                %1$s.subject_count ins
            WHERE
                %2$s
            GROUP BY
                ins.subject_id,
                ins.tenant_id
        ) sub
    JOIN
        %1$s.subject s ON s.id = sub.subject_id
    WHERE
        %3$s
    GROUP BY
        s.id;
    """;

  private static final String UPDATE_COUNTS_QUERY = """
    UPDATE %s.classification_count set count = count -1
    WHERE classification_id = ?
      and tenant_id = ?
      and count >0
    """;
  private static final String INSERT_ENTITIES_SQL = """
      INSERT INTO %s.subject (id, value, authority_id, source_id, type_id)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT DO NOTHING;
    """;
  private static final String INSERT_RELATIONS_SQL = """
      INSERT INTO %s.subject_count (subject_id, tenant_id, count)
      VALUES (?, ?, 1)
      ON CONFLICT (subject_id, tenant_id) DO UPDATE SET count = %s.subject_count.count + 1;
    """;

  private static final String ID_RANGE_INS_WHERE_CLAUSE = "ins.subject_id >= ? AND ins.subject_id <= ?";
  private static final String ID_RANGE_SUBJ_WHERE_CLAUSE = "s.id >= ? AND s.id <= ?";
  private static final String IDS_INS_WHERE_CLAUSE = "ins.subject_id IN (%1$s)";
  private static final String IDS_SUB_WHERE_CLAUSE = "s.id IN (%1$s)";

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
      IDS_INS_WHERE_CLAUSE.formatted(getParamPlaceholder(ids.size())),
      IDS_SUB_WHERE_CLAUSE.formatted(getParamPlaceholder(ids.size())));
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
      ID_RANGE_INS_WHERE_CLAUSE,
      ID_RANGE_SUBJ_WHERE_CLAUSE);
  }

  @Override
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return (rs, rowNum) -> {
      Map<String, Object> subject = new HashMap<>();
      subject.put("id", getId(rs));
      subject.put(SUBJECT_VALUE_FIELD, getValue(rs));
      subject.put(AUTHORITY_ID_FIELD, getAuthorityId(rs));
      subject.put("sourceId", getSourceId(rs));
      subject.put("typeId", getTypeId(rs));

      var maps = jsonConverter.fromJsonToListOfMaps(getInstances(rs));
      subject.put("instances", maps);

      return subject;
    };
  }

  @Override
  public void updateCounts(List<Map<String, Object>> relations) {
    var sql = UPDATE_COUNTS_QUERY.formatted(JdbcUtils.getSchemaName(context));
    try {
      jdbcTemplate.batchUpdate(sql, relations, BATCH_OPERATION_SIZE,
        (statement, relation) -> {
          statement.setString(1, (String) relation.get("subjectId"));
          statement.setString(2, (String) relation.get("tenantId"));
        });
    } catch (DataAccessException e) {
      log.warn("updateCounts::Failed to save relations batch. Starting processing one-by-one", e);
      for (var relation : relations) {
        jdbcTemplate.update(sql, relation.get("subjectId"),
          relation.get("tenantId"));
      }
    }
  }

  @Override
  public void saveAll(Set<Map<String, Object>> entities, List<Map<String, Object>> entityRelations) {
    var entitiesSql = INSERT_ENTITIES_SQL.formatted(JdbcUtils.getSchemaName(context));
    try {
      jdbcTemplate.batchUpdate(entitiesSql, entities, BATCH_OPERATION_SIZE,
        (statement, entity) -> {
          statement.setString(1, (String) entity.get("id"));
          statement.setString(2, (String) entity.get(SUBJECT_VALUE_FIELD));
          statement.setString(3, (String) entity.get(AUTHORITY_ID_FIELD));
          statement.setString(4, (String) entity.get(SUBJECT_SOURCE_ID_FIELD));
          statement.setString(5, (String) entity.get(SUBJECT_TYPE_ID_FIELD));
        });
    } catch (DataAccessException e) {
      log.warn("saveAll::Failed to save entities batch. Starting processing one-by-one", e);
      for (var entity : entities) {
        jdbcTemplate.update(entitiesSql, entity.get("id"), entity.get(SUBJECT_VALUE_FIELD),
          entity.get(AUTHORITY_ID_FIELD), entity.get(SUBJECT_SOURCE_ID_FIELD), entity.get(SUBJECT_TYPE_ID_FIELD));
      }
    }

    var relationsSql =
      INSERT_RELATIONS_SQL.formatted(JdbcUtils.getSchemaName(context), JdbcUtils.getSchemaName(context));

    for (var entityRelation : entityRelations) {
      jdbcTemplate.update(relationsSql, entityRelation.get("subjectId"),
        entityRelation.get("tenantId"));
    }
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
