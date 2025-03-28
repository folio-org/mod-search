package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getParamPlaceholderForUuid;
import static org.folio.search.utils.LogUtils.logWarnDebugError;
import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_SOURCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_TYPE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_VALUE_FIELD;
import static org.folio.search.utils.SearchUtils.SUB_RESOURCE_INSTANCES_FIELD;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.entity.ChildResourceEntityBatch;
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
                'shared', sub.shared,
                'tenantId', sub.tenant_id
            )
        ) AS instances
    FROM
        (
            SELECT
                ins.subject_id,
                ins.tenant_id,
                ins.shared,
                COUNT(1) AS instance_count
            FROM
                %1$s.instance_subject ins
            WHERE
                %2$s
            GROUP BY
                ins.subject_id,
                ins.tenant_id,
                ins.shared
        ) sub
    JOIN
        %1$s.subject s ON s.id = sub.subject_id
    WHERE
        %3$s
    GROUP BY
        s.id;
    """;

  private static final String SELECT_BY_UPDATED_QUERY = """
    WITH cte AS (SELECT s.id,
                              s.value,
                              s.authority_id,
                              s.source_id,
                              s.type_id,
                              s.last_updated_date
                       FROM %1$s.subject s
                       WHERE last_updated_date > ?
                       ORDER BY last_updated_date
                       )
          SELECT s.id,
                 s.value,
                 s.authority_id,
                 s.source_id,
                 s.type_id,
                 s.last_updated_date,
                                         json_agg(
                                CASE
                                    WHEN sub.instance_count IS NULL THEN NULL
                                    ELSE json_build_object(
                                            'count', sub.instance_count,
                                             'shared', sub.shared,
                                             'tenantId', sub.tenant_id
                                         )
                                    END
                        ) AS instances
          FROM cte s
                   LEFT JOIN
               (SELECT cte.id,
                       ins.tenant_id,
                       ins.shared,
                       count(1) AS instance_count
                FROM %1$s.instance_subject ins
                         INNER JOIN cte ON ins.subject_id = cte.id
                GROUP BY cte.id,
                         ins.tenant_id,
                         ins.shared) sub ON s.id = sub.id
          GROUP BY s.id,
                   s.value,
                   s.authority_id,
                   s.source_id,
                   s.type_id,
                   s.last_updated_date
          ORDER BY last_updated_date ASC;
    """;

  private static final String DELETE_QUERY = """
    WITH deleted_ids as (
        DELETE
        FROM %1$s.instance_subject
        WHERE instance_id IN (%2$s) %3$s
        RETURNING subject_id
    )
    UPDATE %1$s.subject
    SET last_updated_date = CURRENT_TIMESTAMP
    WHERE id IN (SELECT * FROM deleted_ids);
    """;

  private static final String INSERT_ENTITIES_SQL = """
      INSERT INTO %s.subject (id, value, authority_id, source_id, type_id)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT (id) DO UPDATE SET last_updated_date = CURRENT_TIMESTAMP;
    """;
  private static final String INSERT_RELATIONS_SQL = """
      INSERT INTO %s.instance_subject (instance_id, subject_id, tenant_id, shared)
      VALUES (?::uuid, ?, ?, ?)
      ON CONFLICT DO NOTHING;
    """;

  private static final String ID_RANGE_INS_WHERE_CLAUSE = "ins.subject_id >= ? AND ins.subject_id <= ?";
  private static final String ID_RANGE_SUBJ_WHERE_CLAUSE = "s.id >= ? AND s.id <= ?";

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

  @Override
  public List<Map<String, Object>> fetchByIdRange(String lower, String upper) {
    var sql = getFetchBySql();
    return jdbcTemplate.query(sql, rowToMapMapper(), lower, upper, lower, upper);
  }

  @Override
  public SubResourceResult fetchByTimestamp(String tenant, Timestamp timestamp) {
    var sql = SELECT_BY_UPDATED_QUERY.formatted(JdbcUtils.getSchemaName(tenant, context.getFolioModuleMetadata()));
    var records = jdbcTemplate.query(sql, rowToMapMapper2(), timestamp);
    var lastUpdateDate = records.isEmpty() ? null : records.getLast().get(LAST_UPDATED_DATE_FIELD);
    return new SubResourceResult(records, (Timestamp) lastUpdateDate);
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

      var maps = jsonConverter.fromJsonToListOfMaps(getInstances(rs)).stream().filter(Objects::nonNull).toList();
      if (!maps.isEmpty()) {
        subject.put(SUB_RESOURCE_INSTANCES_FIELD, maps);
      }

      return subject;
    };
  }

  @Override
  public void deleteByInstanceIds(List<String> instanceIds, String tenantId) {
    var sql = DELETE_QUERY.formatted(
      JdbcUtils.getSchemaName(context),
      getParamPlaceholderForUuid(instanceIds.size()),
      tenantId == null ? "" : "AND tenant_id = ?");

    if (tenantId != null) {
      var params = Stream.of(instanceIds, List.of(tenantId)).flatMap(List::stream).toArray();
      jdbcTemplate.update(sql, params);
      return;
    }

    jdbcTemplate.update(sql, instanceIds.toArray());
  }

  @Override
  public void saveAll(ChildResourceEntityBatch entityBatch) {
    var entitiesSql = INSERT_ENTITIES_SQL.formatted(JdbcUtils.getSchemaName(context));
    try {
      jdbcTemplate.batchUpdate(entitiesSql, entityBatch.resourceEntities(), BATCH_OPERATION_SIZE,
        (statement, entity) -> {
          statement.setString(1, (String) entity.get("id"));
          statement.setString(2, (String) entity.get(SUBJECT_VALUE_FIELD));
          statement.setString(3, (String) entity.get(AUTHORITY_ID_FIELD));
          statement.setString(4, (String) entity.get(SUBJECT_SOURCE_ID_FIELD));
          statement.setString(5, (String) entity.get(SUBJECT_TYPE_ID_FIELD));
        });
    } catch (DataAccessException e) {
      logWarnDebugError(SAVE_ENTITIES_BATCH_ERROR_MESSAGE, e);
      for (var entity : entityBatch.resourceEntities()) {
        jdbcTemplate.update(entitiesSql, entity.get("id"), entity.get(SUBJECT_VALUE_FIELD),
          entity.get(AUTHORITY_ID_FIELD), entity.get(SUBJECT_SOURCE_ID_FIELD), entity.get(SUBJECT_TYPE_ID_FIELD));
      }
    }

    var relationsSql = INSERT_RELATIONS_SQL.formatted(JdbcUtils.getSchemaName(context));
    try {
      jdbcTemplate.batchUpdate(relationsSql, entityBatch.relationshipEntities(), BATCH_OPERATION_SIZE,
        (statement, entityRelation) -> {
          statement.setObject(1, entityRelation.get("instanceId"));
          statement.setString(2, (String) entityRelation.get("subjectId"));
          statement.setString(3, (String) entityRelation.get("tenantId"));
          statement.setObject(4, entityRelation.get("shared"));
        });
    } catch (DataAccessException e) {
      logWarnDebugError(SAVE_RELATIONS_BATCH_ERROR_MESSAGE, e);
      for (var entityRelation : entityBatch.relationshipEntities()) {
        jdbcTemplate.update(relationsSql, entityRelation.get("instanceId"), entityRelation.get("subjectId"),
          entityRelation.get("tenantId"), entityRelation.get("shared"));
      }
    }
  }

  protected RowMapper<Map<String, Object>> rowToMapMapper2() {
    return (rs, rowNum) -> {
      Map<String, Object> subject = new HashMap<>();
      subject.put("id", getId(rs));
      subject.put(SUBJECT_VALUE_FIELD, getValue(rs));
      subject.put(AUTHORITY_ID_FIELD, getAuthorityId(rs));
      subject.put("sourceId", getSourceId(rs));
      subject.put("typeId", getTypeId(rs));
      subject.put(LAST_UPDATED_DATE_FIELD, rs.getTimestamp("last_updated_date"));

      var maps = jsonConverter.fromJsonToListOfMaps(getInstances(rs)).stream().filter(Objects::nonNull).toList();
      if (!maps.isEmpty()) {
        subject.put(SUB_RESOURCE_INSTANCES_FIELD, maps);
      }

      return subject;
    };
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
    return rs.getString(SUB_RESOURCE_INSTANCES_FIELD);
  }
}
