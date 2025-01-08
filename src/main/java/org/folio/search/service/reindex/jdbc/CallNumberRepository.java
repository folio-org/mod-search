package org.folio.search.service.reindex.jdbc;

import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.CHRONOLOGY_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.ENUMERATION_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.VOLUME_FIELD;
import static org.folio.search.service.reindex.ReindexConstants.CALL_NUMBER_TABLE;
import static org.folio.search.service.reindex.ReindexConstants.INSTANCE_CALL_NUMBER_TABLE;
import static org.folio.search.utils.CallNumberUtils.calculateFullCallNumber;
import static org.folio.search.utils.JdbcUtils.getFullTableName;
import static org.folio.search.utils.JdbcUtils.getParamPlaceholderForUuid;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_BROWSING_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_PREFIX_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_SUFFIX_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_TYPE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.COPY_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
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
import org.folio.search.service.consortium.ConsortiumTenantProvider;
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
    WITH deleted_ids as (
        DELETE
        FROM %1$s.instance_call_number
        WHERE instance_id IN (%2$s)
        RETURNING call_number_id
    )
    UPDATE %1$s.call_number
    SET last_updated_date = CURRENT_TIMESTAMP
    WHERE id IN (SELECT * FROM deleted_ids);
    """;

  private static final String SELECT_BY_UPDATED_QUERY = """
    WITH cte AS (
        SELECT
            id,
            call_number,
            call_number_prefix,
            call_number_suffix,
            call_number_type_id,
            volume,
            enumeration,
            chronology,
            copy_number,
            last_updated_date
        FROM %1$s.call_number
        WHERE last_updated_date > ?
        ORDER BY last_updated_date
    )
    SELECT
        c.id,
        c.call_number,
        c.call_number_prefix,
        c.call_number_suffix,
        c.call_number_type_id,
        c.volume,
        c.enumeration,
        c.chronology,
        c.copy_number,
        c.last_updated_date,
        json_agg(
            CASE
                WHEN sub.instance_count IS NULL THEN NULL
                ELSE json_build_object(
                     'count', sub.instance_count,
                     'tenantId', sub.tenant_id,
                     'locationId', sub.location_ids
                )
            END
        ) AS instances
    FROM cte c
    LEFT JOIN (
        SELECT
            cte.id,
            ins.tenant_id,
            array_agg(DISTINCT ins.location_id) FILTER (WHERE ins.location_id IS NOT NULL) AS location_ids,
            count(DISTINCT ins.instance_id) AS instance_count
        FROM %1$s.instance_call_number ins
        INNER JOIN cte ON ins.call_number_id = cte.id
        GROUP BY
            cte.id,
            ins.tenant_id
    ) sub ON c.id = sub.id
    GROUP BY
        c.id,
        c.call_number,
        c.call_number_prefix,
        c.call_number_suffix,
        c.call_number_type_id,
        c.volume,
        c.enumeration,
        c.chronology,
        c.copy_number,
        c.last_updated_date
    ORDER BY
        last_updated_date ASC;
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
    ON CONFLICT (id) DO UPDATE SET last_updated_date = CURRENT_TIMESTAMP;
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

  private final ConsortiumTenantProvider tenantProvider;

  protected CallNumberRepository(JdbcTemplate jdbcTemplate, JsonConverter jsonConverter, FolioExecutionContext context,
                                 ReindexConfigurationProperties reindexConfig,
                                 ConsortiumTenantProvider consortiumTenantProvider) {
    super(jdbcTemplate, jsonConverter, context, reindexConfig);
    this.tenantProvider = consortiumTenantProvider;
  }

  @Override
  public void deleteByInstanceIds(List<String> instanceIds) {
    var sql = DELETE_QUERY.formatted(JdbcUtils.getSchemaName(context), getParamPlaceholderForUuid(instanceIds.size()));
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
  public List<Map<String, Object>> fetchByIdRange(String lower, String upper) {
    var sql = getFetchBySql();
    return jdbcTemplate.query(sql, rowToMapMapper(), lower, upper, lower, upper);
  }

  @Override
  public SubResourceResult fetchByTimestamp(String tenant, Timestamp timestamp) {
    var sql = SELECT_BY_UPDATED_QUERY.formatted(JdbcUtils.getSchemaName(tenant, context.getFolioModuleMetadata()));
    var records = jdbcTemplate.query(sql, rowToMapMapper2(), timestamp);
    var lastUpdateDate = records.isEmpty() ? null : records.get(records.size() - 1).get(LAST_UPDATED_DATE_FIELD);
    return new SubResourceResult(records, (Timestamp) lastUpdateDate);
  }

  @Override
  protected String getFetchBySql() {
    return SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context), ID_RANGE_INS_WHERE_CLAUSE,
      ID_RANGE_CLAS_WHERE_CLAUSE);
  }

  @Override
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return (rs, rowNum) -> getCallNumberMap(rs);
  }

  protected RowMapper<Map<String, Object>> rowToMapMapper2() {
    return (rs, rowNum) -> {
      var callNumberMap = getCallNumberMap(rs);
      var subResourcesInstances = getSubResourcesInstances(rs);
      if (!subResourcesInstances.isEmpty()) {
        callNumberMap.put(SUB_RESOURCE_INSTANCES_FIELD, subResourcesInstances);
      }
      callNumberMap.put(LAST_UPDATED_DATE_FIELD, rs.getTimestamp("last_updated_date"));
      return callNumberMap;
    };
  }

  private List<Map<String, Object>> getSubResourcesInstances(ResultSet rs) throws SQLException {
    var subResources = parseInstanceSubResources(getInstances(rs));
    subResources.forEach(subResource ->
      subResource.setShared(tenantProvider.isCentralTenant(subResource.getTenantId())));
    var subResourcesJson = jsonConverter.toJson(subResources);
    return jsonConverter.fromJsonToListOfMaps(subResourcesJson).stream().filter(Objects::nonNull).toList();
  }

  private Map<String, Object> getCallNumberMap(ResultSet rs) throws SQLException {
    var callNumber = getCallNumber(rs);
    var callNumberSuffix = getCallNumberSuffix(rs);
    var volume = getVolume(rs);
    var enumeration = getEnumeration(rs);
    var chronology = getChronology(rs);
    var copyNumber = getCopyNumber(rs);

    Map<String, Object> callNumberMap = new HashMap<>();
    callNumberMap.put(ID_FIELD, getId(rs));
    callNumberMap.put(CALL_NUMBER_BROWSING_FIELD, calculateFullCallNumber(
      callNumber, volume, enumeration, chronology, copyNumber, callNumberSuffix));
    callNumberMap.put(CALL_NUMBER_FIELD, callNumber);
    callNumberMap.put(CALL_NUMBER_PREFIX_FIELD, getCallNumberPrefix(rs));
    callNumberMap.put(CALL_NUMBER_SUFFIX_FIELD, callNumberSuffix);
    callNumberMap.put(CALL_NUMBER_TYPE_ID_FIELD, getCallNumberTypeId(rs));
    callNumberMap.put(VOLUME_FIELD, volume);
    callNumberMap.put(ENUMERATION_FIELD, enumeration);
    callNumberMap.put(CHRONOLOGY_FIELD, chronology);
    callNumberMap.put(COPY_NUMBER_FIELD, copyNumber);
    var subResources = jsonConverter.toJson(parseInstanceSubResources(getInstances(rs)));
    var maps = jsonConverter.fromJsonToListOfMaps(subResources).stream().filter(Objects::nonNull).toList();
    if (!maps.isEmpty()) {
      callNumberMap.put(SUB_RESOURCE_INSTANCES_FIELD, maps);
    }
    return callNumberMap;
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
