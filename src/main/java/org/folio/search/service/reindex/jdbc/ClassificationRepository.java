package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.LogUtils.logWarnDebugError;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_NUMBER_ENTITY_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_TYPE_FIELD;
import static org.folio.search.utils.SearchUtils.SUB_RESOURCE_INSTANCES_FIELD;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.entity.ChildResourceEntityBatch;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.service.reindex.ReindexContext;
import org.folio.search.utils.JdbcUtils;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
@SuppressWarnings("java:S2077")
public class ClassificationRepository extends UploadRangeRepository implements InstanceChildResourceRepository {

  private static final String SELECT_QUERY = """
    SELECT
        c.id,
        c.number,
        c.type_id,
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
                ins.classification_id,
                ins.tenant_id,
                ins.shared,
                COUNT(1) AS instance_count
            FROM
                %1$s.instance_classification ins
            WHERE
                %2$s
            GROUP BY
                ins.classification_id,
                ins.tenant_id,
                ins.shared
        ) sub
    JOIN
        %1$s.classification c ON c.id = sub.classification_id
    WHERE
        %3$s
    GROUP BY
        c.id;
    """;

  private static final String SELECT_BY_UPDATED_QUERY = """
      WITH cte AS (
          SELECT c.id,
                 c.number,
                 c.type_id,
                 c.last_updated_date
          FROM %1$s.classification c
          WHERE %2$s
          ORDER BY %3$s
          %4$s
      )
      SELECT c.id,
             c.number,
             c.type_id,
             c.last_updated_date,
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
      FROM cte c
      LEFT JOIN (
          SELECT cte.id,
                 ins.tenant_id,
                 ins.shared,
                 count(1) AS instance_count
          FROM %1$s.instance_classification ins
          INNER JOIN cte ON ins.classification_id = cte.id
          GROUP BY cte.id, ins.tenant_id, ins.shared
      ) sub ON c.id = sub.id
      GROUP BY c.id, c.number, c.type_id, c.last_updated_date
      ORDER BY %5$s;
    """;

  private static final String DELETE_QUERY = """
    WITH deleted_ids as (
        DELETE
        FROM %1$s.instance_classification
        WHERE instance_id IN (%2$s) %3$s
        RETURNING classification_id
    )
    UPDATE %1$s.classification
    SET last_updated_date = CURRENT_TIMESTAMP
    WHERE id IN (SELECT * FROM deleted_ids);
    """;

  private static final String INSERT_ENTITIES_SQL = """
      INSERT INTO %s.classification (id, number, type_id)
      VALUES (?, ?, ?)
      ON CONFLICT (id) DO UPDATE SET last_updated_date = CURRENT_TIMESTAMP;
    """;
  private static final String INSERT_STAGING_ENTITIES_SQL = """
      INSERT INTO %s.staging_classification (id, number, type_id, inserted_at)
      VALUES (?, ?, ?, CURRENT_TIMESTAMP)
      ON CONFLICT (id) DO NOTHING;
    """;
  private static final String INSERT_RELATIONS_SQL = """
      INSERT INTO %s.instance_classification (instance_id, classification_id, tenant_id, shared)
      VALUES (?::uuid, ?, ?, ?)
      ON CONFLICT DO NOTHING;
    """;

  private static final String INSERT_STAGING_RELATIONS_SQL = """
      INSERT INTO %s.staging_instance_classification (instance_id, classification_id, tenant_id, shared, inserted_at)
      VALUES (?::uuid, ?, ?, ?, CURRENT_TIMESTAMP);
    """;

  private static final String ID_RANGE_INS_WHERE_CLAUSE = "ins.classification_id >= ? AND ins.classification_id <= ?";
  private static final String ID_RANGE_CLAS_WHERE_CLAUSE = "c.id >= ? AND c.id <= ?";

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

  @Override
  protected Optional<String> stagingEntityTable() {
    return Optional.of(ReindexConstants.STAGING_CLASSIFICATION_TABLE);
  }

  @Override
  protected Optional<String> subEntityStagingTable() {
    return Optional.of(ReindexConstants.STAGING_INSTANCE_CLASSIFICATION_TABLE);
  }

  @Override
  protected boolean supportsTenantSpecificDeletion() {
    // Classification table doesn't have tenant_id column - it's shared across tenants
    return false;
  }

  @Override
  public List<Map<String, Object>> fetchByIdRange(String lower, String upper) {
    var sql = getFetchBySql();
    return jdbcTemplate.query(sql, rowToMapMapper(), lower, upper, lower, upper);
  }

  @Override
  public SubResourceResult fetchByTimestamp(String tenant, Timestamp timestamp) {
    return fetchByTimestamp(SELECT_BY_UPDATED_QUERY, rowToMapMapper2(), timestamp, tenant);
  }

  @Override
  public SubResourceResult fetchByTimestamp(String tenant, Timestamp timestamp, int limit) {
    return fetchByTimestamp(SELECT_BY_UPDATED_QUERY, rowToMapMapper2(), timestamp, limit, tenant);
  }

  @Override
  public SubResourceResult fetchByTimestamp(String tenant, Timestamp timestamp, String fromId, int limit) {
    return fetchByTimestamp(SELECT_BY_UPDATED_QUERY, rowToMapMapper2(), timestamp, fromId, limit, tenant);
  }

  @Override
  protected String getFetchBySql() {
    return SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context),
      ID_RANGE_INS_WHERE_CLAUSE,
      ID_RANGE_CLAS_WHERE_CLAUSE);
  }

  @Override
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return (rs, rowNum) -> {
      Map<String, Object> classification = new HashMap<>();
      classification.put("id", getId(rs));
      classification.put(CLASSIFICATION_NUMBER_ENTITY_FIELD, getNumber(rs));
      classification.put("typeId", getTypeId(rs));

      var maps = jsonConverter.fromJsonToListOfMaps(getInstances(rs)).stream().filter(Objects::nonNull).toList();
      if (!maps.isEmpty()) {
        classification.put(SUB_RESOURCE_INSTANCES_FIELD, maps);
      }

      return classification;
    };
  }

  @Override
  public void deleteByInstanceIds(List<String> instanceIds, String tenantId) {
    deleteByInstanceIds(DELETE_QUERY, instanceIds, tenantId);
  }

  @Override
  public void saveAll(ChildResourceEntityBatch entityBatch) {
    // Use staging tables only for member tenant specific full reindex
    if (ReindexContext.isReindexMode() && ReindexContext.isMemberTenantReindex()) {
      saveEntitiesToStaging(entityBatch.resourceEntities().stream().toList());
      saveRelationshipsToStaging(entityBatch.relationshipEntities().stream().toList());
    } else {
      saveEntitiesToMain(entityBatch.resourceEntities().stream().toList());
      saveRelationshipsToMain(entityBatch.relationshipEntities().stream().toList());
    }
  }

  private void saveEntitiesToMain(List<Map<String, Object>> entities) {
    var entitiesSql = INSERT_ENTITIES_SQL.formatted(JdbcUtils.getSchemaName(context));
    try {
      jdbcTemplate.batchUpdate(entitiesSql, entities, BATCH_OPERATION_SIZE,
        (statement, entity) -> {
          statement.setString(1, (String) entity.get("id"));
          statement.setString(2, (String) entity.get(CLASSIFICATION_NUMBER_FIELD));
          statement.setObject(3, entity.get(CLASSIFICATION_TYPE_FIELD));
        });
    } catch (DataAccessException e) {
      logWarnDebugError(SAVE_ENTITIES_BATCH_ERROR_MESSAGE, e);
      for (var entity : entities) {
        try {
          jdbcTemplate.update(entitiesSql,
            entity.get("id"), entity.get(CLASSIFICATION_NUMBER_FIELD), entity.get(CLASSIFICATION_TYPE_FIELD));
        } catch (DataAccessException ex) {
          log.debug("Failed to save classification entity {}: {}", entity.get("id"), ex.getMessage());
        }
      }
    }
  }

  private void saveEntitiesToStaging(List<Map<String, Object>> entities) {
    var stagingEntitiesSql = INSERT_STAGING_ENTITIES_SQL.formatted(JdbcUtils.getSchemaName(context));
    try {
      jdbcTemplate.batchUpdate(stagingEntitiesSql, entities, BATCH_OPERATION_SIZE,
        (statement, entity) -> {
          statement.setString(1, (String) entity.get("id"));
          statement.setString(2, (String) entity.get(CLASSIFICATION_NUMBER_FIELD));
          statement.setString(3, (String) entity.get(CLASSIFICATION_TYPE_FIELD));
        });
    } catch (DataAccessException e) {
      log.warn("saveEntitiesToStaging::Failed to save entities batch. Processing one-by-one", e);
      for (var entity : entities) {
        try {
          jdbcTemplate.update(stagingEntitiesSql, entity.get("id"),
            entity.get(CLASSIFICATION_NUMBER_FIELD), entity.get(CLASSIFICATION_TYPE_FIELD));
        } catch (DataAccessException ex) {
          log.debug("Failed to save staging classification entity {}: {}", entity.get("id"), ex.getMessage());
        }
      }
    }
    log.debug("Saved {} classification entities to staging table", entities.size());
  }

  private void saveRelationshipsToMain(List<Map<String, Object>> relationships) {
    var relationsSql = INSERT_RELATIONS_SQL.formatted(JdbcUtils.getSchemaName(context));
    try {
      jdbcTemplate.batchUpdate(relationsSql, relationships, BATCH_OPERATION_SIZE,
        (statement, entityRelation) -> {
          statement.setObject(1, entityRelation.get("instanceId"));
          statement.setString(2, (String) entityRelation.get("classificationId"));
          statement.setString(3, (String) entityRelation.get("tenantId"));
          statement.setObject(4, entityRelation.get("shared"));
        });
    } catch (DataAccessException e) {
      logWarnDebugError(SAVE_RELATIONS_BATCH_ERROR_MESSAGE, e);
      for (var entityRelation : relationships) {
        try {
          jdbcTemplate.update(relationsSql, entityRelation.get("instanceId"), entityRelation.get("classificationId"),
            entityRelation.get("tenantId"), entityRelation.get("shared"));
        } catch (DataAccessException ex) {
          log.debug("Failed to save classification relationship for {}: {}",
            entityRelation.get("classificationId"), ex.getMessage());
        }
      }
    }
  }

  private void saveRelationshipsToStaging(List<Map<String, Object>> relationships) {
    var stagingRelationsSql = INSERT_STAGING_RELATIONS_SQL.formatted(JdbcUtils.getSchemaName(context));
    try {
      jdbcTemplate.batchUpdate(stagingRelationsSql, relationships, BATCH_OPERATION_SIZE,
        (statement, entityRelation) -> {
          statement.setObject(1, entityRelation.get("instanceId"));
          statement.setString(2, (String) entityRelation.get("classificationId"));
          statement.setString(3, (String) entityRelation.get("tenantId"));
          statement.setObject(4, entityRelation.get("shared"));
        });
    } catch (DataAccessException e) {
      log.warn("saveRelationshipsToStaging::Failed to save relationships batch. Processing one-by-one", e);
      for (var entityRelation : relationships) {
        try {
          jdbcTemplate.update(stagingRelationsSql, entityRelation.get("instanceId"),
            entityRelation.get("classificationId"), entityRelation.get("tenantId"), entityRelation.get("shared"));
        } catch (DataAccessException ex) {
          log.debug("Failed to save staging classification relationship for {}: {}",
            entityRelation.get("classificationId"), ex.getMessage());
        }
      }
    }
    log.debug("Saved {} classification relationships to staging table", relationships.size());
  }

  protected RowMapper<Map<String, Object>> rowToMapMapper2() {
    return (rs, rowNum) -> {
      Map<String, Object> classification = new HashMap<>();
      classification.put("id", getId(rs));
      classification.put(CLASSIFICATION_NUMBER_ENTITY_FIELD, getNumber(rs));
      classification.put("typeId", getTypeId(rs));
      classification.put(LAST_UPDATED_DATE_FIELD, rs.getTimestamp("last_updated_date"));

      var maps = jsonConverter.fromJsonToListOfMaps(getInstances(rs)).stream().filter(Objects::nonNull).toList();
      if (!maps.isEmpty()) {
        classification.put(SUB_RESOURCE_INSTANCES_FIELD, maps);
      }

      return classification;
    };
  }

  @Override
  public List<Map<String, Object>> fetchByIdRangeWithTimestamp(String lower, String upper, Timestamp timestamp) {
    var sql = SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context),
      ID_RANGE_INS_WHERE_CLAUSE,
      ID_RANGE_CLAS_WHERE_CLAUSE + " AND c.last_updated_date = ?");
    return jdbcTemplate.query(sql, rowToMapMapper(), lower, upper, lower, upper, timestamp);
  }

  private String getId(ResultSet rs) throws SQLException {
    return rs.getString("id");
  }

  private String getTypeId(ResultSet rs) throws SQLException {
    return rs.getString("type_id");
  }

  private String getNumber(ResultSet rs) throws SQLException {
    return rs.getString(CLASSIFICATION_NUMBER_ENTITY_FIELD);
  }

  private String getInstances(ResultSet rs) throws SQLException {
    return rs.getString(SUB_RESOURCE_INSTANCES_FIELD);
  }
}
