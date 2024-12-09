package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getParamPlaceholder;
import static org.folio.search.utils.JdbcUtils.getParamPlaceholderForUuid;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_TYPE_FIELD;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.entity.ChildResourceEntityBatch;
import org.folio.search.model.entity.InstanceClassificationEntityAgg;
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

  private static final String DELETE_QUERY = """
    DELETE
    FROM %s.instance_classification
    WHERE instance_id IN (%s);
    """;
  private static final String INSERT_ENTITIES_SQL = """
      INSERT INTO %s.classification (id, number, type_id)
      VALUES (?, ?, ?)
      ON CONFLICT DO NOTHING;
    """;
  private static final String INSERT_RELATIONS_SQL = """
      INSERT INTO %s.instance_classification (instance_id, classification_id, tenant_id, shared)
      VALUES (?::uuid, ?, ?, ?)
      ON CONFLICT DO NOTHING;
    """;

  private static final String ID_RANGE_INS_WHERE_CLAUSE = "ins.classification_id >= ? AND ins.classification_id <= ?";
  private static final String ID_RANGE_CLAS_WHERE_CLAUSE = "c.id >= ? AND c.id <= ?";
  private static final String IDS_INS_WHERE_CLAUSE = "ins.classification_id IN (%1$s)";
  private static final String IDS_CLAS_WHERE_CLAUSE = "c.id IN (%1$s)";

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
      IDS_INS_WHERE_CLAUSE.formatted(getParamPlaceholder(ids.size())),
      IDS_CLAS_WHERE_CLAUSE.formatted(getParamPlaceholder(ids.size())));
    return jdbcTemplate.query(sql, instanceClassificationAggRowMapper(), ListUtils.union(ids, ids).toArray());
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
      ID_RANGE_CLAS_WHERE_CLAUSE);
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

  @Override
  public void deleteByInstanceIds(List<String> instanceIds) {
    var sql = DELETE_QUERY.formatted(JdbcUtils.getSchemaName(context), getParamPlaceholderForUuid(instanceIds.size()));
    jdbcTemplate.update(sql, instanceIds.toArray());
  }

  @Override
  public void saveAll(ChildResourceEntityBatch entityBatch) {
    var entitiesSql = INSERT_ENTITIES_SQL.formatted(JdbcUtils.getSchemaName(context));
    try {
      jdbcTemplate.batchUpdate(entitiesSql, entityBatch.resourceEntities(), BATCH_OPERATION_SIZE,
        (statement, entity) -> {
          statement.setString(1, (String) entity.get("id"));
          statement.setString(2, (String) entity.get(CLASSIFICATION_NUMBER_FIELD));
          statement.setObject(3, entity.get(CLASSIFICATION_TYPE_FIELD));
        });
    } catch (DataAccessException e) {
      log.warn("saveAll::Failed to save entities batch. Starting processing one-by-one", e);
      for (var entity : entityBatch.resourceEntities()) {
        jdbcTemplate.update(entitiesSql,
          entity.get("id"), entity.get(CLASSIFICATION_NUMBER_FIELD), entity.get(CLASSIFICATION_TYPE_FIELD));
      }
    }

    var relationsSql = INSERT_RELATIONS_SQL.formatted(JdbcUtils.getSchemaName(context));
    try {
      jdbcTemplate.batchUpdate(relationsSql, entityBatch.relationshipEntities(), BATCH_OPERATION_SIZE,
        (statement, entityRelation) -> {
          statement.setObject(1, entityRelation.get("instanceId"));
          statement.setString(2, (String) entityRelation.get("classificationId"));
          statement.setString(3, (String) entityRelation.get("tenantId"));
          statement.setObject(4, entityRelation.get("shared"));
        });
    } catch (DataAccessException e) {
      log.warn("saveAll::Failed to save relations batch. Starting processing one-by-one", e);
      for (var entityRelation : entityBatch.relationshipEntities()) {
        jdbcTemplate.update(relationsSql, entityRelation.get("instanceId"), entityRelation.get("classificationId"),
          entityRelation.get("tenantId"), entityRelation.get("shared"));
      }
    }
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
