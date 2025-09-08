package org.folio.search.service.reindex.jdbc;

import static org.folio.search.service.reindex.ReindexConstants.MERGE_RANGE_TABLE;
import static org.folio.search.utils.JdbcUtils.getFullTableName;
import static org.folio.search.utils.JdbcUtils.getUuidArrayParam;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexRangeStatus;
import org.folio.search.utils.JdbcUtils;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

public abstract class MergeRangeRepository extends ReindexJdbcRepository {

  private static final String DELETE_SQL = """
    DELETE FROM %s WHERE id = ANY (?);
    """;

  private static final String DELETE_SQL_FOR_TENANT = """
    DELETE FROM %s WHERE id = ANY (?) AND tenant_id = ?;
    """;

  private static final String SOFT_DELETE_SQL = """
    UPDATE %s SET is_deleted = true, last_updated_date = CURRENT_TIMESTAMP WHERE id = ANY (?);
    """;

  private static final String SOFT_DELETE_SQL_FOR_TENANT = """
    UPDATE %s SET is_deleted = true, last_updated_date = CURRENT_TIMESTAMP WHERE id = ANY (?) AND tenant_id = ?;
    """;
  private static final String INSERT_MERGE_RANGE_SQL = """
      INSERT INTO %s (id, entity_type, tenant_id, lower, upper, created_at, finished_at)
      VALUES (?, ?, ?, ?, ?, ?, ?);
    """;

  private static final String SELECT_MERGE_RANGES_BY_ENTITY_TYPE = "SELECT * FROM %s WHERE entity_type = ?;";

  private static final String SELECT_FAILED_MERGE_RANGES = "SELECT * FROM %s WHERE status = 'FAIL';";

  private final boolean instanceChildrenIndexEnabled;

  protected MergeRangeRepository(JdbcTemplate jdbcTemplate,
                                 JsonConverter jsonConverter,
                                 FolioExecutionContext context,
                                 SearchConfigurationProperties searchConfigurationProperties) {
    super(jdbcTemplate, jsonConverter, context);
    this.instanceChildrenIndexEnabled = Optional.ofNullable(
        searchConfigurationProperties.getIndexing().getInstanceChildrenIndexEnabled())
      .orElse(false);
  }

  @Transactional
  public void saveMergeRanges(List<MergeRangeEntity> mergeRanges) {
    var fullTableName = getFullTableName(context, MERGE_RANGE_TABLE);
    jdbcTemplate.batchUpdate(INSERT_MERGE_RANGE_SQL.formatted(fullTableName), mergeRanges, BATCH_OPERATION_SIZE,
      (statement, entity) -> {
        statement.setObject(1, entity.getId());
        statement.setString(2, entity.getEntityType().getType());
        statement.setString(3, entity.getTenantId());
        statement.setString(4, entity.getLowerId());
        statement.setString(5, entity.getUpperId());
        statement.setTimestamp(6, entity.getCreatedAt());
        statement.setTimestamp(7, entity.getFinishedAt());
      });
  }

  public List<MergeRangeEntity> getMergeRanges() {
    var fullTableName = getFullTableName(context, MERGE_RANGE_TABLE);
    var sql = SELECT_MERGE_RANGES_BY_ENTITY_TYPE.formatted(fullTableName);
    return jdbcTemplate.query(sql, mergeRangeEntityRowMapper(), entityType().getType());
  }

  public List<MergeRangeEntity> getFailedMergeRanges() {
    var fullTableName = getFullTableName(context, MERGE_RANGE_TABLE);
    var sql = SELECT_FAILED_MERGE_RANGES.formatted(fullTableName);
    return jdbcTemplate.query(sql, mergeRangeEntityRowMapper());
  }

  public void truncateMergeRanges() {
    JdbcUtils.truncateTable(MERGE_RANGE_TABLE, jdbcTemplate, context);
  }

  @Override
  protected String rangeTable() {
    return MERGE_RANGE_TABLE;
  }

  public abstract void saveEntities(String tenantId, List<Map<String, Object>> entities);

  public void deleteEntitiesForTenant(List<String> ids, String tenantId) {
    var hard = !instanceChildrenIndexEnabled;
    deleteEntitiesForTenant(ids, tenantId, hard);
  }

  public void deleteEntitiesForTenant(List<String> ids, String tenantId, boolean hard) {
    var fullTableName = getFullTableName(context, entityTable());
    var query = hard ? DELETE_SQL_FOR_TENANT : SOFT_DELETE_SQL_FOR_TENANT;
    var sql = query.formatted(fullTableName);

    jdbcTemplate.update(sql, statement -> {
      statement.setArray(1, getUuidArrayParam(ids, statement));
      statement.setString(2, tenantId);
    });
  }

  public void deleteEntities(List<String> ids) {
    var hard = !instanceChildrenIndexEnabled;
    deleteEntities(ids, hard);
  }

  public void deleteEntities(List<String> ids, boolean hard) {
    var fullTableName = getFullTableName(context, entityTable());
    var query = hard ? DELETE_SQL : SOFT_DELETE_SQL;
    var sql = query.formatted(fullTableName);

    jdbcTemplate.update(sql, statement -> statement.setArray(1, getUuidArrayParam(ids, statement)));
  }

  public void updateBoundWith(String tenantId, String id, boolean bound) {

  }

  private RowMapper<MergeRangeEntity> mergeRangeEntityRowMapper() {
    return (rs, rowNum) -> {
      var mergeRange = new MergeRangeEntity(
        rs.getObject(MergeRangeEntity.ID_COLUMN, UUID.class),
        ReindexEntityType.fromValue(rs.getString(MergeRangeEntity.ENTITY_TYPE_COLUMN)),
        rs.getString(MergeRangeEntity.TENANT_ID_COLUMN),
        rs.getString(MergeRangeEntity.RANGE_LOWER_COLUMN),
        rs.getString(MergeRangeEntity.RANGE_UPPER_COLUMN),
        rs.getTimestamp(MergeRangeEntity.CREATED_AT_COLUMN),
        ReindexRangeStatus.valueOfNullable(rs.getString(MergeRangeEntity.STATUS_COLUMN)),
        rs.getString(MergeRangeEntity.FAIL_CAUSE_COLUMN)
      );
      mergeRange.setFinishedAt(rs.getTimestamp(MergeRangeEntity.FINISHED_AT_COLUMN));
      return mergeRange;
    };
  }
}
