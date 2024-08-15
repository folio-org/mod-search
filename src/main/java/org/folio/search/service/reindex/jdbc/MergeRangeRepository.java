package org.folio.search.service.reindex.jdbc;

import static org.folio.search.service.reindex.ReindexConstants.MERGE_RANGE_TABLE;
import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.util.List;
import java.util.UUID;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.InventoryRecordType;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

public abstract class MergeRangeRepository extends ReindexJdbcRepository {

  private static final String INSERT_MERGE_RANGE_SQL = """
      INSERT INTO %s (id, entity_type, tenant_id, lower, upper, created_at, finished_at)
      VALUES (?, ?, ?, ?, ?, ?, ?);
    """;

  private static final String SELECT_MERGE_RANGES_BY_ENTITY_TYPE = "SELECT * FROM %s WHERE entity_type = ?;";

  private static final int BATCH_OPERATION_SIZE = 100;

  protected MergeRangeRepository(JdbcTemplate jdbcTemplate,
                                 JsonConverter jsonConverter,
                                 FolioExecutionContext context) {
    super(jdbcTemplate, jsonConverter, context);
  }

  public void truncateMergeRangeTable() {
    String sql = TRUNCATE_TABLE_SQL.formatted(getFullTableName(context, MERGE_RANGE_TABLE));
    jdbcTemplate.execute(sql);
  }

  @Transactional
  public void saveMergeRanges(List<MergeRangeEntity> mergeRanges) {
    var fullTableName = getFullTableName(context, MERGE_RANGE_TABLE);
    jdbcTemplate.batchUpdate(INSERT_MERGE_RANGE_SQL.formatted(fullTableName), mergeRanges, BATCH_OPERATION_SIZE,
      (statement, entity) -> {
        statement.setObject(1, entity.getId());
        statement.setString(2, entity.getEntityType().name());
        statement.setString(3, entity.getTenantId());
        statement.setObject(4, entity.getLowerId());
        statement.setObject(5, entity.getUpperId());
        statement.setTimestamp(6, entity.getCreatedAt());
        statement.setTimestamp(7, entity.getFinishedAt());
      });
  }

  public List<MergeRangeEntity> getMergeRanges() {
    var fullTableName = getFullTableName(context, MERGE_RANGE_TABLE);
    var sql = SELECT_MERGE_RANGES_BY_ENTITY_TYPE.formatted(fullTableName);
    return jdbcTemplate.query(sql, mergeRangeEntityRowMapper(), entityType().getType());
  }

  public abstract ReindexEntityType entityType();

  private RowMapper<MergeRangeEntity> mergeRangeEntityRowMapper() {
    return (rs, rowNum) ->
      new MergeRangeEntity(
        rs.getObject(MergeRangeEntity.ID_COLUMN, UUID.class),
        InventoryRecordType.valueOf(rs.getString(MergeRangeEntity.ENTITY_TYPE_COLUMN)),
        rs.getString(MergeRangeEntity.TENANT_ID_COLUMN),
        rs.getObject(MergeRangeEntity.RANGE_LOWER_COLUMN, UUID.class),
        rs.getObject(MergeRangeEntity.RANGE_UPPER_COLUMN, UUID.class),
        rs.getTimestamp(MergeRangeEntity.CREATED_AT_COLUMN)
      );
  }
}
