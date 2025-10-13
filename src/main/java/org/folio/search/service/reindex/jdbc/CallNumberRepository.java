package org.folio.search.service.reindex.jdbc;

import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.service.reindex.ReindexConstants.CALL_NUMBER_TABLE;
import static org.folio.search.service.reindex.ReindexConstants.INSTANCE_CALL_NUMBER_TABLE;
import static org.folio.search.service.reindex.ReindexConstants.STAGING_CALL_NUMBER_TABLE;
import static org.folio.search.service.reindex.ReindexConstants.STAGING_INSTANCE_CALL_NUMBER_TABLE;
import static org.folio.search.utils.CallNumberUtils.calculateFullCallNumber;
import static org.folio.search.utils.JdbcUtils.getFullTableName;
import static org.folio.search.utils.JdbcUtils.getParamPlaceholderForUuid;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_BROWSING_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_PREFIX_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_SUFFIX_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_TYPE_ID_FIELD;
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
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.entity.ChildResourceEntityBatch;
import org.folio.search.model.types.ReindexEntityType;
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
public class CallNumberRepository extends UploadRangeRepository implements InstanceChildResourceRepository {

  private static final String SELECT_QUERY = """
    SELECT c.*,
           json_agg(
                   json_build_object(
                           'instanceId', sub.instance_ids,
                           'tenantId', sub.tenant_id,
                           'shared', sub.shared,
                           'locationId', sub.location_id
                   )
           ) AS instances
    FROM (SELECT ins.call_number_id,
                 ins.tenant_id,
                 i.shared,
                 ins.location_id,
                 array_agg(DISTINCT i.id) AS instance_ids
          FROM %1$s.instance_call_number ins
          INNER JOIN %1$s.instance i ON i.id = ins.instance_id
          WHERE %2$s
          GROUP BY ins.call_number_id, ins.tenant_id, i.shared, ins.location_id) sub
          JOIN %1$s.call_number c ON c.id = sub.call_number_id
    WHERE %3$s
    GROUP BY c.id;
    """;

  private static final String DELETE_QUERY = """
    WITH deleted_ids as (
        DELETE
        FROM %1$s.instance_call_number
        WHERE item_id IN (%2$s) %3$s
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
            last_updated_date
        FROM %1$s.call_number
        WHERE %2$s
        ORDER BY %3$s
        %4$s
    )
    SELECT
        c.id,
        c.call_number,
        c.call_number_prefix,
        c.call_number_suffix,
        c.call_number_type_id,
        c.last_updated_date,
        json_agg(
            CASE
                WHEN sub.instance_ids IS NULL THEN NULL
                ELSE json_build_object(
                     'instanceId', sub.instance_ids,
                     'tenantId', sub.tenant_id,
                     'shared', sub.shared,
                     'locationId', sub.location_id
                )
            END
        ) AS instances
    FROM cte c
    LEFT JOIN (
        SELECT
            cte.id,
            ins.tenant_id,
            i.shared,
            ins.location_id,
            array_agg(DISTINCT i.id) AS instance_ids
        FROM %1$s.instance_call_number ins
        INNER JOIN cte ON ins.call_number_id = cte.id
        INNER JOIN %1$s.instance i ON i.id = ins.instance_id
        GROUP BY
            cte.id,
            ins.tenant_id,
            i.shared,
            ins.location_id
    ) sub ON c.id = sub.id
    GROUP BY
        c.id,
        c.call_number,
        c.call_number_prefix,
        c.call_number_suffix,
        c.call_number_type_id,
        c.last_updated_date
    ORDER BY %5$s;
    """;

  private static final String INSERT_ENTITIES_SQL = """
    INSERT INTO %s (
        id,
        call_number,
        call_number_prefix,
        call_number_suffix,
        call_number_type_id
    ) VALUES (?, ?, ?, ?, ?)
    ON CONFLICT (id) DO UPDATE SET last_updated_date = CURRENT_TIMESTAMP;
    """;

  private static final String INSERT_STAGING_ENTITIES_SQL = """
    INSERT INTO %s (
        id,
        call_number,
        call_number_prefix,
        call_number_suffix,
        call_number_type_id,
        inserted_at
    ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP);
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

  private static final String INSERT_STAGING_RELATIONS_SQL = """
    INSERT INTO %s (
        call_number_id,
        item_id,
        instance_id,
        tenant_id,
        location_id,
        inserted_at
    ) VALUES (?, ?::uuid, ?::uuid, ?, ?::uuid, CURRENT_TIMESTAMP);
    """;

  private static final String ID_RANGE_INS_WHERE_CLAUSE = "ins.call_number_id >= ? AND ins.call_number_id <= ?";
  private static final String ID_RANGE_CLAS_WHERE_CLAUSE = "c.id >= ? AND c.id <= ?";

  protected CallNumberRepository(JdbcTemplate jdbcTemplate, JsonConverter jsonConverter, FolioExecutionContext context,
                                 ReindexConfigurationProperties reindexConfig) {
    super(jdbcTemplate, jsonConverter, context, reindexConfig);
  }

  @Override
  public void deleteByInstanceIds(List<String> itemIds, String tenantId) {
    var sql = DELETE_QUERY.formatted(JdbcUtils.getSchemaName(context), getParamPlaceholderForUuid(itemIds.size()),
      tenantId == null ? "" : "AND tenant_id = ?");
    var params = tenantId == null
      ? itemIds.toArray()
      : Stream.of(itemIds, List.of(tenantId)).flatMap(List::stream).toArray();
    jdbcTemplate.update(sql, params);
  }

  @Override
  public void saveAll(ChildResourceEntityBatch entityBatch) {
    // Use staging tables only for member tenant specific full reindex
    if (ReindexContext.isReindexMode() && ReindexContext.isMemberTenantReindex()) {
      saveResourceEntitiesToStaging(entityBatch);
      saveRelationshipEntitiesToStaging(entityBatch);
    } else {
      saveResourceEntities(entityBatch);
      saveRelationshipEntities(entityBatch);
    }
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
  protected Optional<String> stagingEntityTable() {
    return Optional.of(STAGING_CALL_NUMBER_TABLE);
  }

  @Override
  protected Optional<String> subEntityStagingTable() {
    return Optional.of(STAGING_INSTANCE_CALL_NUMBER_TABLE);
  }

  @Override
  protected boolean supportsTenantSpecificDeletion() {
    // Call number table doesn't have tenant_id column - it's shared across tenants
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
      var maps = jsonConverter.fromJsonToListOfMaps(getInstances(rs)).stream().filter(Objects::nonNull).toList();
      if (!maps.isEmpty()) {
        callNumberMap.put(SUB_RESOURCE_INSTANCES_FIELD, maps);
      }
      callNumberMap.put(LAST_UPDATED_DATE_FIELD, rs.getTimestamp("last_updated_date"));
      return callNumberMap;
    };
  }

  private Map<String, Object> getCallNumberMap(ResultSet rs) throws SQLException {
    var callNumber = getCallNumber(rs);
    var callNumberSuffix = getCallNumberSuffix(rs);

    Map<String, Object> callNumberMap = new HashMap<>();
    callNumberMap.put(ID_FIELD, getId(rs));
    callNumberMap.put(CALL_NUMBER_BROWSING_FIELD, calculateFullCallNumber(callNumber, callNumberSuffix));
    callNumberMap.put(CALL_NUMBER_FIELD, callNumber);
    callNumberMap.put(CALL_NUMBER_PREFIX_FIELD, getCallNumberPrefix(rs));
    callNumberMap.put(CALL_NUMBER_SUFFIX_FIELD, callNumberSuffix);
    callNumberMap.put(CALL_NUMBER_TYPE_ID_FIELD, getCallNumberTypeId(rs));
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
        });
    } catch (DataAccessException e) {
      log.warn("saveAll::Failed to save entities batch. Starting processing one-by-one", e);
      for (var entity : entityBatch.resourceEntities()) {
        jdbcTemplate.update(callNumberSql, getId(entity), getCallNumber(entity), getPrefix(entity), getSuffix(entity),
          getTypeId(entity));
      }
    }
  }

  private void saveResourceEntitiesToStaging(ChildResourceEntityBatch entityBatch) {
    var stagingCallNumberTable = getFullTableName(context, STAGING_CALL_NUMBER_TABLE);
    var stagingCallNumberSql = INSERT_STAGING_ENTITIES_SQL.formatted(stagingCallNumberTable);

    try {
      jdbcTemplate.batchUpdate(stagingCallNumberSql, entityBatch.resourceEntities(), BATCH_OPERATION_SIZE,
        (statement, entity) -> {
          statement.setString(1, getId(entity));
          statement.setString(2, getCallNumber(entity));
          statement.setString(3, getPrefix(entity));
          statement.setString(4, getSuffix(entity));
          statement.setString(5, getTypeId(entity));
        });
    } catch (DataAccessException e) {
      log.warn("saveResourceEntitiesToStaging::Failed to save entities batch. Processing one-by-one", e);
      for (var entity : entityBatch.resourceEntities()) {
        try {
          jdbcTemplate.update(stagingCallNumberSql, getId(entity), getCallNumber(entity), getPrefix(entity),
            getSuffix(entity), getTypeId(entity));
        } catch (DataAccessException ex) {
          log.debug("Failed to save staging call number entity {}: {}", getId(entity), ex.getMessage());
        }
      }
    }
    log.debug("Saved {} call number entities to staging table", entityBatch.resourceEntities().size());
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
      log.warn("saveRelationshipEntities::Failed to save relations batch. Processing one-by-one", e);
      for (var entityRelation : entityBatch.relationshipEntities()) {
        try {
          jdbcTemplate.update(instanceCallNumberSql, getCallNumberId(entityRelation), getItemId(entityRelation),
            getInstanceId(entityRelation), getTenantId(entityRelation), getLocationId(entityRelation));
        } catch (DataAccessException ex) {
          log.debug("Failed to save call number relationship for {}: {}",
            getCallNumberId(entityRelation), ex.getMessage());
        }
      }
    }
  }

  private void saveRelationshipEntitiesToStaging(ChildResourceEntityBatch entityBatch) {
    var stagingInstanceCallNumberTable = getFullTableName(context, STAGING_INSTANCE_CALL_NUMBER_TABLE);
    var stagingInstanceCallNumberSql = INSERT_STAGING_RELATIONS_SQL.formatted(stagingInstanceCallNumberTable);

    try {
      jdbcTemplate.batchUpdate(stagingInstanceCallNumberSql, entityBatch.relationshipEntities(), BATCH_OPERATION_SIZE,
        (statement, entity) -> {
          statement.setString(1, getCallNumberId(entity));
          statement.setString(2, getItemId(entity));
          statement.setString(3, getInstanceId(entity));
          statement.setString(4, getTenantId(entity));
          statement.setString(5, getLocationId(entity));
        });
    } catch (DataAccessException e) {
      log.warn("saveRelationshipEntitiesToStaging::Failed to save staging relations batch. Processing one-by-one", e);
      for (var entityRelation : entityBatch.relationshipEntities()) {
        try {
          jdbcTemplate.update(stagingInstanceCallNumberSql, getCallNumberId(entityRelation), getItemId(entityRelation),
            getInstanceId(entityRelation), getTenantId(entityRelation), getLocationId(entityRelation));
        } catch (DataAccessException ex) {
          log.debug("Failed to save staging call number relationship for {}: {}",
            getCallNumberId(entityRelation), ex.getMessage());
        }
      }
    }
    log.debug("Saved {} call number relationships to staging table", entityBatch.relationshipEntities().size());
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

  @Override
  public List<Map<String, Object>> fetchByIdRangeWithTimestamp(String lower, String upper, Timestamp timestamp) {
    var sql = SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context),
      ID_RANGE_INS_WHERE_CLAUSE,
      ID_RANGE_CLAS_WHERE_CLAUSE + " AND c.last_updated_date > ?");
    return jdbcTemplate.query(sql, rowToMapMapper(), lower, upper, lower, upper, timestamp);
  }

  private String getId(Map<String, Object> item) {
    return getString(item, "id");
  }

  private String getId(ResultSet rs) throws SQLException {
    return rs.getString("id");
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
