package org.folio.search.service.reindex.jdbc;

import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.service.reindex.ReindexConstants.CALL_NUMBER_TABLE;
import static org.folio.search.service.reindex.ReindexConstants.INSTANCE_CALL_NUMBER_TABLE;
import static org.folio.search.utils.CallNumberUtils.calculateFullCallNumber;
import static org.folio.search.utils.JdbcUtils.getFullTableName;
import static org.folio.search.utils.JdbcUtils.getParamPlaceholder;
import static org.folio.search.utils.JdbcUtils.getParamPlaceholderForUuid;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.entity.ChildResourceEntityBatch;
import org.folio.search.model.entity.InstanceCallNumberEntityAgg;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.utils.JdbcUtils;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
public class CallNumberRepository extends UploadRangeRepository implements InstanceChildResourceRepository {

  private static final String SELECT_QUERY = """
    SELECT c.*,
           json_agg(
                   json_build_object(
                           'count', sub.instance_count,
                           'tenantId', sub.tenant_id,
                           'locationId', sub.location_ids
                   )
           ) AS instances
    FROM (SELECT ins.call_number_id,
                 ins.tenant_id,
                 array_agg(DISTINCT ins.location_id) FILTER (WHERE ins.location_id IS NOT NULL) AS location_ids,
                 count(DISTINCT ins.instance_id)     AS instance_count
          FROM %1$s.instance_call_number ins
          WHERE %2$s
          GROUP BY ins.call_number_id, ins.tenant_id) sub
          JOIN %1$s.call_number c ON c.id = sub.call_number_id
    WHERE %3$s
    GROUP BY c.id;
    """;

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
    ) VALUES (?, ?::uuid, ?::uuid, ?, ?::uuid)
    ON CONFLICT DO NOTHING;
    """;

  private static final String ID_RANGE_INS_WHERE_CLAUSE = "ins.call_number_id >= ? AND ins.call_number_id <= ?";
  private static final String ID_RANGE_CLAS_WHERE_CLAUSE = "c.id >= ? AND c.id <= ?";
  private static final String IDS_INS_WHERE_CLAUSE = "ins.call_number_id IN (%1$s)";
  private static final String IDS_CLAS_WHERE_CLAUSE = "c.id IN (%1$s)";

  protected CallNumberRepository(JdbcTemplate jdbcTemplate, JsonConverter jsonConverter, FolioExecutionContext context,
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

  public List<InstanceCallNumberEntityAgg> fetchByIds(List<String> ids) {
    if (CollectionUtils.isEmpty(ids)) {
      return Collections.emptyList();
    }
    var sql = SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context),
      IDS_INS_WHERE_CLAUSE.formatted(getParamPlaceholder(ids.size())),
      IDS_CLAS_WHERE_CLAUSE.formatted(getParamPlaceholder(ids.size())));
    return jdbcTemplate.query(sql, instanceCallNumberAggRowMapper(), ListUtils.union(ids, ids).toArray());
  }

  @Override
  public List<Map<String, Object>> fetchByIdRange(String lower, String upper) {
    var sql = getFetchBySql();
    return jdbcTemplate.query(sql, instanceCallNumberAggRowMapper(), lower, upper, lower, upper)
      .stream()
      .map(jsonConverter::convertToMap)
      .toList();
  }

  @Override
  protected String getFetchBySql() {
    return SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context), ID_RANGE_INS_WHERE_CLAUSE,
      ID_RANGE_CLAS_WHERE_CLAUSE);
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
        jdbcTemplate.update(callNumberSql, getId(entity), getCallNumber(entity), getPrefix(entity), getSuffix(entity),
          getTypeId(entity), getVolume(entity), getEnumeration(entity), getChronology(entity), getCopyNumber(entity));
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

  private RowMapper<InstanceCallNumberEntityAgg> instanceCallNumberAggRowMapper() {
    return (rs, rowNum) -> {
      var callNumber = getCallNumber(rs);
      var callNumberSuffix = getCallNumberSuffix(rs);
      var volume = getVolume(rs);
      var enumeration = getEnumeration(rs);
      var chronology = getChronology(rs);
      var copyNumber = getCopyNumber(rs);
      return new InstanceCallNumberEntityAgg(getId(rs),
        calculateFullCallNumber(callNumber, volume, enumeration, chronology, copyNumber, callNumberSuffix),
        callNumber, getCallNumberPrefix(rs), callNumberSuffix, getCallNumberTypeId(rs), volume, enumeration, chronology,
        copyNumber, parseInstanceSubResources(getInstances(rs)));
    };
  }

  private String getCallNumberSuffix(ResultSet rs) throws SQLException {
    return rs.getString("call_number_suffix");
  }

  private String getCallNumberPrefix(ResultSet rs) throws SQLException {
    return rs.getString("call_number_prefix");
  }

  private String getCallNumberTypeId(ResultSet rs) throws SQLException {
    return rs.getString("call_number_type_id");
  }

  private String getInstances(ResultSet rs) throws SQLException {
    return rs.getString("instances");
  }

  private String getCallNumber(Map<String, Object> callNumberComponents) {
    return getString(callNumberComponents, "callNumber");
  }

  private String getCallNumber(ResultSet rs) throws SQLException {
    return rs.getString("call_number");
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

  private String getId(ResultSet rs) throws SQLException {
    return rs.getString("id");
  }

  private String getCopyNumber(Map<String, Object> item) {
    return getString(item, "copyNumber");
  }

  private String getCopyNumber(ResultSet rs) throws SQLException {
    return rs.getString("copy_number");
  }

  private String getEnumeration(Map<String, Object> item) {
    return getString(item, "enumeration");
  }

  private String getEnumeration(ResultSet rs) throws SQLException {
    return rs.getString("enumeration");
  }

  private String getChronology(Map<String, Object> item) {
    return getString(item, "chronology");
  }

  private String getChronology(ResultSet rs) throws SQLException {
    return rs.getString("chronology");
  }

  private String getVolume(Map<String, Object> item) {
    return getString(item, "volume");
  }

  private String getVolume(ResultSet rs) throws SQLException {
    return rs.getString("volume");
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
