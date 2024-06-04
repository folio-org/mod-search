package org.folio.search.repository.classification;

import static org.folio.search.utils.JdbcUtils.getGroupedParamPlaceholder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.utils.JdbcUtils;
import org.folio.spring.FolioExecutionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
@RequiredArgsConstructor
public class InstanceClassificationJdbcRepository implements InstanceClassificationRepository {

  private static final String INSTANCE_CLASSIFICATION_TABLE_NAME = "instance_classification";
  private static final String CLASSIFICATION_TYPE_COLUMN = "classification_type_id";
  private static final String CLASSIFICATION_NUMBER_COLUMN = "classification_number";
  private static final String TENANT_ID_COLUMN = "tenant_id";
  private static final String INSTANCE_ID_COLUMN = "instance_id";
  private static final String SHARED_COLUMN = "shared";
  private static final String CLASSIFICATION_TYPE_DEFAULT = "<null>";

  private static final String SELECT_ALL_SQL = "SELECT * FROM %s;";
  private static final String SELECT_ALL_BY_INSTANCE_ID_AGG = """
    SELECT
        classification_number,
        classification_type_id,
        json_agg(json_build_object(
            'instanceId', instance_id,
            'shared', shared,
            'tenantId', tenant_id
        )) AS instances
    FROM %s
    WHERE (classification_number, classification_type_id) IN (%s)
    GROUP BY classification_number, classification_type_id;
    """;
  private static final String INSERT_SQL = """
    INSERT INTO %s (classification_type_id, classification_number, tenant_id, instance_id, shared)
    VALUES (?, ?, ?, ?, ?)
    ON CONFLICT (classification_type_id, classification_number, tenant_id, instance_id)
    DO UPDATE SET shared = EXCLUDED.shared;
    """;
  private static final String DELETE_SQL = """
    DELETE FROM %s
    WHERE classification_type_id = ? AND classification_number = ? AND tenant_id = ? AND instance_id = ?;
    """;
  private static final int BATCH_SIZE = 100;
  private static final TypeReference<LinkedHashSet<InstanceSubResource>> VALUE_TYPE_REF = new TypeReference<>() { };

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
        ps.setString(1, classificationTypeToDatabaseValue(id));
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
        ps.setString(1, classificationTypeToDatabaseValue(id));
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
  public List<InstanceClassificationEntityAgg> fetchAggregatedByClassifications(
    List<InstanceClassificationEntity> classifications) {
    log.debug("fetchAggregatedByClassifications::instance classifications [entities: {}]", classifications);
    if (CollectionUtils.isEmpty(classifications)) {
      return Collections.emptyList();
    }
    return jdbcTemplate.query(
      SELECT_ALL_BY_INSTANCE_ID_AGG.formatted(getTableName(), getGroupedParamPlaceholder(classifications.size(), 2)),
      instanceClassificationAggRowMapper(), getArgsForAggregatedByClassifications(classifications));
  }

  @NotNull
  private Object[] getArgsForAggregatedByClassifications(List<InstanceClassificationEntity> classifications) {
    var args = new Object[classifications.size() * 2];
    int index = 0;
    for (var classification : classifications) {
      args[index++] = classification.number();
      args[index++] = classification.typeId();
    }
    return args;
  }

  @NotNull
  private RowMapper<InstanceClassificationEntity> instanceClassificationRowMapper() {
    return (rs, rowNum) -> {
      var builder = InstanceClassificationEntity.Id.builder();
      var typeVal = rs.getString(CLASSIFICATION_TYPE_COLUMN);
      builder.typeId(databaseValueToClassificationType(typeVal));
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
      var typeId = databaseValueToClassificationType(typeVal);
      var number = rs.getString(CLASSIFICATION_NUMBER_COLUMN);
      var instancesJson = rs.getString("instances");
      Set<InstanceSubResource> instanceSubResources;
      try {
        instanceSubResources = objectMapper.readValue(instancesJson, VALUE_TYPE_REF);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException(e);
      }
      return new InstanceClassificationEntityAgg(typeId, number, instanceSubResources);
    };
  }

  private String getTableName() {
    return JdbcUtils.getFullTableName(context, INSTANCE_CLASSIFICATION_TABLE_NAME);
  }

  private String classificationTypeToDatabaseValue(InstanceClassificationEntity.Id id) {
    return id.typeId() == null ? CLASSIFICATION_TYPE_DEFAULT : id.typeId();
  }

  @Nullable
  private static String databaseValueToClassificationType(String typeVal) {
    return CLASSIFICATION_TYPE_DEFAULT.equals(typeVal) ? null : typeVal;
  }
}
