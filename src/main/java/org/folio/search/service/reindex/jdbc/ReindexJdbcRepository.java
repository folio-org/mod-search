package org.folio.search.service.reindex.jdbc;

import static org.folio.search.model.reindex.UploadRangeEntity.CREATED_AT_COLUMN;
import static org.folio.search.model.reindex.UploadRangeEntity.ENTITY_TYPE_COLUMN;
import static org.folio.search.model.reindex.UploadRangeEntity.FINISHED_AT_COLUMN;
import static org.folio.search.model.reindex.UploadRangeEntity.RANGE_LIMIT_COLUMN;
import static org.folio.search.model.reindex.UploadRangeEntity.RANGE_OFFSET_COLUMN;
import static org.folio.search.model.reindex.UploadRangeEntity.UPLOAD_RANGE_TABLE;
import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.reindex.UploadRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public abstract class ReindexJdbcRepository {

  private static final String UPSERT_UPLOAD_RANGE_SQL = """
      INSERT INTO %s (entity_type, range_limit, range_offset, created_at, finished_at)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT (entity_type, range_limit, range_offset)
      DO UPDATE SET finished_at = EXCLUDED.finished_at;
    """;

  private static final String SELECT_UPLOAD_RANGE_BY_ENTITY_TYPE_SQL = "select * from %s where entity_type = ?;";
  private static final String COUNT_SQL = "select count(*) from %s;";

  private final JdbcTemplate jdbcTemplate;
  private final FolioExecutionContext context;
  private final ReindexConfigurationProperties reindexConfig;

  protected ReindexJdbcRepository(JdbcTemplate jdbcTemplate,
                                  FolioExecutionContext context,
                                  ReindexConfigurationProperties reindexConfig) {
    this.jdbcTemplate = jdbcTemplate;
    this.context = context;
    this.reindexConfig = reindexConfig;
  }

  public List<UploadRangeEntity> getUploadRanges(boolean populateIfNotExist) {
    var fullTableName = getFullTableName(context, UPLOAD_RANGE_TABLE);
    var sql = SELECT_UPLOAD_RANGE_BY_ENTITY_TYPE_SQL.formatted(fullTableName);
    var uploadRanges = jdbcTemplate.query(sql, uploadRangeRowMapper(), entityType().name());

    return populateIfNotExist && uploadRanges.isEmpty()
           ? prepareAndSaveUploadRanges()
           : uploadRanges;
  }

  public void upsertUploadRanges(List<UploadRangeEntity> uploadRanges) {
    var fullTableName = getFullTableName(context, UPLOAD_RANGE_TABLE);
    jdbcTemplate.batchUpdate(UPSERT_UPLOAD_RANGE_SQL.formatted(fullTableName), uploadRanges, 100,
      (statement, entity) -> {
        statement.setString(1, entity.getEntityType().name());
        statement.setInt(2, entity.getLimit());
        statement.setInt(3, entity.getOffset());
        statement.setTimestamp(4, entity.getCreatedAt());
        statement.setTimestamp(5, entity.getFinishedAt());
      });
  }

  public Integer countEntities() {
    var fullTableName = getFullTableName(context, entityTable());
    var sql = COUNT_SQL.formatted(fullTableName);
    return jdbcTemplate.queryForObject(sql, Integer.class);
  }

  public abstract ReindexEntityType entityType();

  protected abstract String entityTable();

  private RowMapper<UploadRangeEntity> uploadRangeRowMapper() {
    return (rs, rowNum) -> {
      var uploadRange = new UploadRangeEntity(
        ReindexEntityType.valueOf(rs.getString(ENTITY_TYPE_COLUMN)),
        rs.getInt(RANGE_LIMIT_COLUMN),
        rs.getInt(RANGE_OFFSET_COLUMN),
        rs.getTimestamp(CREATED_AT_COLUMN)
      );
      uploadRange.setFinishedAt(rs.getTimestamp(FINISHED_AT_COLUMN));
      return uploadRange;
    };
  }

  private List<UploadRangeEntity> prepareAndSaveUploadRanges() {
    List<UploadRangeEntity> ranges = new ArrayList<>();
    var totalRecords = countEntities();
    var rangeSize = reindexConfig.getUploadRangeSize();
    int pages = (int) Math.ceil((double) totalRecords / rangeSize);
    for (int i = 0; i < pages; i++) {
      int offset = i * rangeSize;
      int limit = Math.min(rangeSize, totalRecords - offset);
      ranges.add(new UploadRangeEntity(entityType(), limit, offset, Timestamp.from(Instant.now())));
    }

    upsertUploadRanges(ranges);

    return ranges;
  }
}
