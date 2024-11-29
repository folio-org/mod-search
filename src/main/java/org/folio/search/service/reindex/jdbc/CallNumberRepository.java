package org.folio.search.service.reindex.jdbc;

import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.service.reindex.ReindexConstants.CALL_NUMBER_TABLE;
import static org.folio.search.service.reindex.ReindexConstants.INSTANCE_CALL_NUMBER_TABLE;
import static org.folio.search.utils.JdbcUtils.getFullTableName;
import static org.folio.search.utils.JdbcUtils.getParamPlaceholderForUuid;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.entity.ChildResourceEntityBatch;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
public class CallNumberRepository extends UploadRangeRepository implements InstanceChildResourceRepository {

  private static final String DELETE_QUERY = """
    DELETE
    FROM %s
    WHERE instance_id IN (%s);
    """;

  private static final String INSERT_ENTITIES_SQL = """
    INSERT INTO %s (
        id,
        call_number,
        call_number_prefix,
        call_number_suffix,
        call_number_type_id,
        volume,
        enumeration,
        chronology,
        copy_number
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT DO NOTHING;
    """;

  private static final String INSERT_RELATIONS_SQL = """
    INSERT INTO %s (
        call_number_id,
        item_id,
        instance_id,
        tenant_id,
        location_id
    ) VALUES (?, ?::uuid, ?::uuid, ?, ?::uuid);
    """;

  protected CallNumberRepository(JdbcTemplate jdbcTemplate,
                                 JsonConverter jsonConverter,
                                 FolioExecutionContext context,
                                 ReindexConfigurationProperties reindexConfig) {
    super(jdbcTemplate, jsonConverter, context, reindexConfig);
  }

  @Override
  public void deleteByInstanceIds(List<String> instanceIds) {
    var sql = DELETE_QUERY.formatted(getFullTableName(context, INSTANCE_CALL_NUMBER_TABLE),
      getParamPlaceholderForUuid(instanceIds.size()));
    jdbcTemplate.update(sql, instanceIds.toArray());
  }

  @Override
  public void saveAll(ChildResourceEntityBatch entityBatch) {
    saveResourceEntities(entityBatch);
    saveRelationshipEntities(entityBatch);
  }

  @Override
  public ReindexEntityType entityType() {
    return ReindexEntityType.CALL_NUMBER;
  }

  @Override
  protected String entityTable() {
    return CALL_NUMBER_TABLE;
  }

  @Override
  protected Optional<String> subEntityTable() {
    return Optional.of(INSTANCE_CALL_NUMBER_TABLE);
  }

  @Override
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return null;
  }

  private void saveResourceEntities(ChildResourceEntityBatch entityBatch) {
    var callNumberTable = getFullTableName(context, entityTable());
    var callNumberSql = INSERT_ENTITIES_SQL.formatted(callNumberTable);

    try {
      jdbcTemplate.batchUpdate(callNumberSql, entityBatch.resourceEntities(), BATCH_OPERATION_SIZE,
        (statement, entity) -> {
          statement.setString(1, getId(entity));
          statement.setString(2, getCallNumber(entity));
          statement.setString(3, getPrefix(entity));
          statement.setString(4, getSuffix(entity));
          statement.setString(5, getTypeId(entity));
          statement.setString(6, getVolume(entity));
          statement.setString(7, getEnumeration(entity));
          statement.setString(8, getChronology(entity));
          statement.setString(9, getCopyNumber(entity));
        });
    } catch (DataAccessException e) {
      log.warn("saveAll::Failed to save entities batch. Starting processing one-by-one", e);
      for (var entity : entityBatch.resourceEntities()) {
        jdbcTemplate.update(callNumberSql,
          getId(entity), getCallNumber(entity), getPrefix(entity), getSuffix(entity), getTypeId(entity),
          getVolume(entity), getEnumeration(entity), getChronology(entity), getCopyNumber(entity));
      }
    }
  }

  private void saveRelationshipEntities(ChildResourceEntityBatch entityBatch) {
    var instanceCallNumberTable = getFullTableName(context, INSTANCE_CALL_NUMBER_TABLE);
    var instanceCallNumberSql = INSERT_RELATIONS_SQL.formatted(instanceCallNumberTable);

    try {
      jdbcTemplate.batchUpdate(instanceCallNumberSql, entityBatch.relationshipEntities(), BATCH_OPERATION_SIZE,
        (statement, entity) -> {
          statement.setString(1, getCallNumberId(entity));
          statement.setString(2, getItemId(entity));
          statement.setString(3, getInstanceId(entity));
          statement.setString(4, getTenantId(entity));
          statement.setString(5, getLocationId(entity));
        });
    } catch (DataAccessException e) {
      log.warn("saveAll::Failed to save relations batch. Starting processing one-by-one", e);
      for (var entityRelation : entityBatch.relationshipEntities()) {
        jdbcTemplate.update(instanceCallNumberSql, getCallNumberId(entityRelation), getItemId(entityRelation),
          getInstanceId(entityRelation), getTenantId(entityRelation), getLocationId(entityRelation));
      }
    }
  }

  private String getCallNumber(Map<String, Object> callNumberComponents) {
    return getString(callNumberComponents, "callNumber");
  }

  private String getCallNumberId(Map<String, Object> callNumberComponents) {
    return getString(callNumberComponents, "callNumberId");
  }

  private String getLocationId(Map<String, Object> item) {
    return getString(item, "locationId");
  }

  private String getTenantId(Map<String, Object> item) {
    return getString(item, "tenantId");
  }

  private String getInstanceId(Map<String, Object> item) {
    return getString(item, "instanceId");
  }

  private String getItemId(Map<String, Object> item) {
    return getString(item, "itemId");
  }

  private String getId(Map<String, Object> item) {
    return getString(item, "id");
  }

  private String getCopyNumber(Map<String, Object> item) {
    return getString(item, "copyNumber");
  }

  private String getEnumeration(Map<String, Object> item) {
    return getString(item, "enumeration");
  }

  private String getChronology(Map<String, Object> item) {
    return getString(item, "chronology");
  }

  private String getVolume(Map<String, Object> item) {
    return getString(item, "volume");
  }

  private String getTypeId(Map<String, Object> callNumberComponents) {
    return getString(callNumberComponents, "callNumberTypeId");
  }

  private String getSuffix(Map<String, Object> callNumberComponents) {
    return getString(callNumberComponents, "callNumberSuffix");
  }

  private String getPrefix(Map<String, Object> callNumberComponents) {
    return getString(callNumberComponents, "callNumberPrefix");
  }
}
