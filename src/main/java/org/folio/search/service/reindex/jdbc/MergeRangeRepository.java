package org.folio.search.service.reindex.jdbc;

import static org.folio.search.service.reindex.ReindexConstants.MERGE_RANGE_TABLE;
import static org.folio.search.utils.JdbcUtils.getFullTableName;
import static org.folio.search.utils.JdbcUtils.getParamPlaceholderForUuid;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.utils.JdbcUtils;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

public abstract class MergeRangeRepository extends ReindexJdbcRepository {

  protected static final String DELETE_SQL = """
    DELETE FROM %s WHERE id IN (%s);
    """;
  private static final String INSERT_MERGE_RANGE_SQL = """
      INSERT INTO %s (id, entity_type, tenant_id, lower, upper, created_at, finished_at)
      VALUES (?, ?, ?, ?, ?, ?, ?);
    """;

  private static final String SELECT_MERGE_RANGES_BY_ENTITY_TYPE = "SELECT * FROM %s WHERE entity_type = ?;";

  protected MergeRangeRepository(JdbcTemplate jdbcTemplate,
                                 JsonConverter jsonConverter,
                                 FolioExecutionContext context) {
    super(jdbcTemplate, jsonConverter, context);
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

  public void truncateMergeRanges() {
    JdbcUtils.truncateTable(MERGE_RANGE_TABLE, jdbcTemplate, context);
  }

  @Override
  protected String rangeTable() {
    return MERGE_RANGE_TABLE;
  }

  public abstract void saveEntities(String tenantId, List<Map<String, Object>> entities);

  public void deleteEntities(List<String> ids) {
    var fullTableName = getFullTableName(context, entityTable());
    var sql = DELETE_SQL.formatted(fullTableName, getParamPlaceholderForUuid(ids.size()));

    jdbcTemplate.update(sql, ids.toArray());
  }

  private RowMapper<MergeRangeEntity> mergeRangeEntityRowMapper() {
    return (rs, rowNum) -> {
      var mergeRange = new MergeRangeEntity(
        rs.getObject(MergeRangeEntity.ID_COLUMN, UUID.class),
        ReindexEntityType.fromValue(rs.getString(MergeRangeEntity.ENTITY_TYPE_COLUMN)),
        rs.getString(MergeRangeEntity.TENANT_ID_COLUMN),
        rs.getString(MergeRangeEntity.RANGE_LOWER_COLUMN),
        rs.getString(MergeRangeEntity.RANGE_UPPER_COLUMN),
        rs.getTimestamp(MergeRangeEntity.CREATED_AT_COLUMN)
      );
      mergeRange.setFinishedAt(rs.getTimestamp(MergeRangeEntity.FINISHED_AT_COLUMN));
      return mergeRange;
    };
  }
}
