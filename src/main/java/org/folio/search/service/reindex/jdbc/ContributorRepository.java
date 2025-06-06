package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getParamPlaceholderForUuid;
import static org.folio.search.utils.LogUtils.logWarnDebugError;
import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.CONTRIBUTOR_TYPE_FIELD;
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
public class ContributorRepository extends UploadRangeRepository implements InstanceChildResourceRepository {

  private static final String SELECT_QUERY = """
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

  private static final String SELECT_BY_UPDATED_QUERY = """
    WITH cte AS (SELECT id,
                        name,
                        name_type_id,
                        authority_id,
                        last_updated_date
                 FROM %1$s.contributor
                 WHERE last_updated_date > ?
                 ORDER BY last_updated_date
                 )
    SELECT c.id,
           c.name,
           c.name_type_id,
           c.authority_id,
           c.last_updated_date,
           json_agg(
                   CASE
                                    WHEN sub.instance_count IS NULL THEN NULL
                                    ELSE json_build_object(
                           'count', sub.instance_count,
                           'typeId', sub.type_ids,
                           'shared', sub.shared,
                           'tenantId', sub.tenant_id
                   )
                   END
           ) AS instances
    FROM cte c
             LEFT JOIN
         (SELECT cte.id,
                 ins.tenant_id,
                 ins.shared,
                 array_agg(DISTINCT ins.type_id) FILTER (WHERE ins.type_id <> '') AS type_ids,
                 count(DISTINCT ins.instance_id)                                  AS instance_count
          FROM %1$s.instance_contributor ins
                   INNER JOIN cte
                              ON ins.contributor_id = cte.id
          GROUP BY cte.id,
                   ins.tenant_id,
                   ins.shared) sub ON c.id = sub.id
    GROUP BY c.id,
             c.name,
             c.name_type_id,
             c.authority_id,
             c.last_updated_date
          ORDER BY last_updated_date ASC;
    """;

  private static final String DELETE_QUERY = """
    WITH deleted_ids as (
        DELETE
        FROM %1$s.instance_contributor
        WHERE instance_id IN (%2$s) %3$s
        RETURNING contributor_id
    )
    UPDATE %1$s.contributor
    SET last_updated_date = CURRENT_TIMESTAMP
    WHERE id IN (SELECT * FROM deleted_ids);
    """;

  private static final String INSERT_ENTITIES_SQL = """
      INSERT INTO %s.contributor (id, name, name_type_id, authority_id)
      VALUES (?, ?, ?, ?)
      ON CONFLICT (id) DO UPDATE SET last_updated_date = CURRENT_TIMESTAMP;
    """;
  private static final String INSERT_RELATIONS_SQL = """
      INSERT INTO %s.instance_contributor (instance_id, contributor_id, type_id, tenant_id, shared)
      VALUES (?::uuid, ?, ?, ?, ?)
      ON CONFLICT DO NOTHING;
    """;

  private static final String ID_RANGE_INS_WHERE_CLAUSE = "ins.contributor_id >= ? AND ins.contributor_id <= ?";
  private static final String ID_RANGE_CONTR_WHERE_CLAUSE = "c.id >= ? AND c.id <= ?";

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
      ID_RANGE_INS_WHERE_CLAUSE, ID_RANGE_CONTR_WHERE_CLAUSE);
  }

  @Override
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return (rs, rowNum) -> {
      Map<String, Object> contributor = new HashMap<>();
      contributor.put("id", getId(rs));
      contributor.put("name", getName(rs));
      contributor.put("contributorNameTypeId", getNameTypeId(rs));
      contributor.put(AUTHORITY_ID_FIELD, getAuthorityId(rs));

      var maps = jsonConverter.fromJsonToListOfMaps(getInstances(rs)).stream().filter(Objects::nonNull).toList();
      if (!maps.isEmpty()) {
        contributor.put(SUB_RESOURCE_INSTANCES_FIELD, maps);
      }

      return contributor;
    };
  }

  protected RowMapper<Map<String, Object>> rowToMapMapper2() {
    return (rs, rowNum) -> {
      Map<String, Object> contributor = new HashMap<>();
      contributor.put("id", getId(rs));
      contributor.put("name", getName(rs));
      contributor.put("contributorNameTypeId", getNameTypeId(rs));
      contributor.put(LAST_UPDATED_DATE_FIELD, rs.getTimestamp("last_updated_date"));
      contributor.put(AUTHORITY_ID_FIELD, getAuthorityId(rs));

      var maps = jsonConverter.fromJsonToListOfMaps(getInstances(rs)).stream().filter(Objects::nonNull).toList();
      if (!maps.isEmpty()) {
        contributor.put(SUB_RESOURCE_INSTANCES_FIELD, maps);
      }

      return contributor;
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
          statement.setString(2, (String) entity.get("name"));
          statement.setObject(3, entity.get("nameTypeId"));
          statement.setObject(4, entity.get(AUTHORITY_ID_FIELD));
        });
    } catch (DataAccessException e) {
      logWarnDebugError(SAVE_ENTITIES_BATCH_ERROR_MESSAGE, e);
      for (var entity : entityBatch.resourceEntities()) {
        jdbcTemplate.update(entitiesSql,
          entity.get("id"), entity.get("name"), entity.get("nameTypeId"), entity.get(AUTHORITY_ID_FIELD));
      }
    }

    var relationsSql = INSERT_RELATIONS_SQL.formatted(JdbcUtils.getSchemaName(context));
    try {
      jdbcTemplate.batchUpdate(relationsSql, entityBatch.relationshipEntities(), BATCH_OPERATION_SIZE,
        (statement, entityRelation) -> {
          statement.setObject(1, entityRelation.get("instanceId"));
          statement.setString(2, (String) entityRelation.get("contributorId"));
          statement.setString(3, (String) entityRelation.get(CONTRIBUTOR_TYPE_FIELD));
          statement.setString(4, (String) entityRelation.get("tenantId"));
          statement.setObject(5, entityRelation.get("shared"));
        });
    } catch (DataAccessException e) {
      logWarnDebugError(SAVE_RELATIONS_BATCH_ERROR_MESSAGE, e);
      for (var entityRelation : entityBatch.relationshipEntities()) {
        jdbcTemplate.update(relationsSql, entityRelation.get("instanceId"), entityRelation.get("contributorId"),
          entityRelation.get(CONTRIBUTOR_TYPE_FIELD), entityRelation.get("tenantId"), entityRelation.get("shared"));
      }
    }
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
    return rs.getString(SUB_RESOURCE_INSTANCES_FIELD);
  }
}
