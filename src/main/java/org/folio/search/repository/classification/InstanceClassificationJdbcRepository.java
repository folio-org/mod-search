package org.folio.search.repository.classification;

import static org.folio.search.utils.JdbcUtils.getQuestionMarkPlaceholder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.utils.JdbcUtils;
import org.folio.spring.FolioExecutionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
@RequiredArgsConstructor
public class InstanceClassificationJdbcRepository implements InstanceClassificationRepository {

  private static final String INSTANCE_CLASSIFICATION_TABLE_NAME = "instance_classification";
  private static final String CLASSIFICATION_TYPE_COLUMN = "classification_type";
  private static final String CLASSIFICATION_NUMBER_COLUMN = "classification_number";
  private static final String TENANT_ID_COLUMN = "tenant_id";
  private static final String INSTANCE_ID_COLUMN = "instance_id";
  private static final String SHARED_COLUMN = "shared";
  private static final String CLASSIFICATION_TYPE_DEFAULT = "<null>";

  private static final String SELECT_ALL_SQL = "SELECT * FROM %s;";
  private static final String SELECT_ALL_BY_INSTANCE_ID_AGG = """
    SELECT
        t1.classification_number,
        t1.classification_type,
        json_agg(json_build_object(
            'instanceId', t1.instance_id,
            'shared', t1.shared,
            'tenantId', t1.tenant_id
        )) AS instances
    FROM %1$s t1
    INNER JOIN %1$s t2 ON t1.classification_number = t2.classification_number
                                        AND t1.classification_type = t2.classification_type
                                        AND t2.instance_id IN (%2$s)
    GROUP BY t1.classification_number, t1.classification_type;
    """;
  private static final String INSERT_SQL = """
    INSERT INTO %s (classification_type, classification_number, tenant_id, instance_id, shared)
    VALUES (?, ?, ?, ?, ?)
    ON CONFLICT (classification_type, classification_number, tenant_id, instance_id)
    DO UPDATE SET shared = EXCLUDED.shared;
    """;
  private static final String DELETE_SQL = """
    DELETE FROM %s
    WHERE classification_type = ? AND classification_number = ? AND tenant_id = ? AND instance_id = ?;
    """;
  private static final int BATCH_SIZE = 100;
  private static final TypeReference<List<InstanceSubResource>> VALUE_TYPE_REF = new TypeReference<>() { };

  private final FolioExecutionContext context;
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public void saveAll(List<InstanceClassificationEntity> classifications) {
    log.debug("saveAll::instance classifications [entities: {}]", classifications);

    if (classifications == null || classifications.isEmpty()) {
      return;
    }

    var uniqueEntities = classifications.stream().distinct().toList();

    jdbcTemplate.batchUpdate(
      INSERT_SQL.formatted(getTableName()),
      uniqueEntities,
      BATCH_SIZE,
      (PreparedStatement ps, InstanceClassificationEntity item) -> {
        var id = item.id();
        ps.setString(1, itemTypeToDatabaseValue(id));
        ps.setString(2, id.number());
        ps.setString(3, id.tenantId());
        ps.setString(4, id.instanceId());
        ps.setBoolean(5, item.shared());
      });
  }

  @Override
  public void deleteAll(List<InstanceClassificationEntity> classifications) {
    log.debug("deleteAll::instance classifications [entities: {}]", classifications);

    if (classifications == null || classifications.isEmpty()) {
      return;
    }

    jdbcTemplate.batchUpdate(
      DELETE_SQL.formatted(getTableName()),
      classifications,
      BATCH_SIZE,
      (PreparedStatement ps, InstanceClassificationEntity item) -> {
        var id = item.id();
        ps.setString(1, itemTypeToDatabaseValue(id));
        ps.setString(2, id.number());
        ps.setString(3, id.tenantId());
        ps.setString(4, id.instanceId());
      });

  }

  @Override
  public List<InstanceClassificationEntity> findAll() {
    log.debug("findAll::instance classifications");
    return jdbcTemplate.query(SELECT_ALL_SQL.formatted(getTableName()), instanceClassificationRowMapper());
  }

  @Override
  public List<InstanceClassificationEntityAgg> findAllByInstanceIds(List<String> instanceIds) {
    log.debug("findAllByInstanceIds::instance classifications [instanceIds: {}]", instanceIds);
    return jdbcTemplate.query(
      SELECT_ALL_BY_INSTANCE_ID_AGG.formatted(getTableName(), getQuestionMarkPlaceholder(instanceIds.size())),
      instanceClassificationAggRowMapper(),
      instanceIds.toArray());
  }

  @NotNull
  private RowMapper<InstanceClassificationEntity> instanceClassificationRowMapper() {
    return (rs, rowNum) -> {
      var builder = InstanceClassificationEntity.Id.builder();
      var typeVal = rs.getString(CLASSIFICATION_TYPE_COLUMN);
      builder.type(CLASSIFICATION_TYPE_DEFAULT.equals(typeVal) ? null : typeVal);
      builder.number(rs.getString(CLASSIFICATION_NUMBER_COLUMN));
      builder.instanceId(rs.getString(INSTANCE_ID_COLUMN));
      builder.tenantId(rs.getString(TENANT_ID_COLUMN));
      var shared = rs.getBoolean(SHARED_COLUMN);
      return new InstanceClassificationEntity(builder.build(), shared);
    };
  }

  @NotNull
  private RowMapper<InstanceClassificationEntityAgg> instanceClassificationAggRowMapper() {
    return (rs, rowNum) -> {
      var typeVal = rs.getString(CLASSIFICATION_TYPE_COLUMN);
      var type = CLASSIFICATION_TYPE_DEFAULT.equals(typeVal) ? null : typeVal;
      var number = rs.getString(CLASSIFICATION_NUMBER_COLUMN);
      var instancesJson = rs.getString("instances");
      List<InstanceSubResource> instanceSubResources;
      try {
        instanceSubResources = objectMapper.readValue(instancesJson, VALUE_TYPE_REF);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException(e);
      }
      return new InstanceClassificationEntityAgg(type, number, instanceSubResources);
    };
  }

  private String getTableName() {
    return JdbcUtils.getFullTableName(context, INSTANCE_CLASSIFICATION_TABLE_NAME);
  }

  private String itemTypeToDatabaseValue(InstanceClassificationEntity.Id id) {
    return id.type() == null ? CLASSIFICATION_TYPE_DEFAULT : id.type();
  }
}
