package org.folio.search.service.reindex.jdbc;

import static org.folio.search.model.reindex.ReindexStatusEntity.END_TIME_MERGE_COLUMN;
import static org.folio.search.model.reindex.ReindexStatusEntity.END_TIME_UPLOAD_COLUMN;
import static org.folio.search.model.reindex.ReindexStatusEntity.PROCESSED_MERGE_RANGES_COLUMN;
import static org.folio.search.model.reindex.ReindexStatusEntity.PROCESSED_UPLOAD_RANGES_COLUMN;
import static org.folio.search.model.reindex.ReindexStatusEntity.START_TIME_MERGE_COLUMN;
import static org.folio.search.model.reindex.ReindexStatusEntity.START_TIME_UPLOAD_COLUMN;
import static org.folio.search.model.reindex.ReindexStatusEntity.STATUS_COLUMN;
import static org.folio.search.model.reindex.ReindexStatusEntity.TOTAL_MERGE_RANGES_COLUMN;
import static org.folio.search.model.reindex.ReindexStatusEntity.TOTAL_UPLOAD_RANGES_COLUMN;
import static org.folio.search.service.reindex.ReindexConstants.REINDEX_STATUS_TABLE;
import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.reindex.ReindexStatusEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexStatus;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReindexStatusRepository {

  private static final String INSERT_REINDEX_STATUS_SQL = """
      INSERT INTO %s (entity_type, status, total_merge_ranges, processed_merge_ranges, total_upload_ranges, processed_upload_ranges, start_time_merge, end_time_merge, start_time_upload, end_time_upload)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
    """;

  private static final String UPDATE_SQL = """
    UPDATE %s
    SET %s
    WHERE entity_type = ?;
    """;

  private static final String UPDATE_FOR_ENTITIES_SQL = """
    UPDATE %s
    SET %s
    WHERE entity_type in (%s);
    """;

  private static final String SELECT_REINDEX_STATUS_BY_REINDEX_ID_SQL = "SELECT * FROM %s;";

  private static final String UPDATE_REINDEX_COUNTS_SQL = """
    UPDATE %s
    SET processed_merge_ranges = processed_merge_ranges + ?,
    processed_upload_ranges = processed_upload_ranges + ?
    WHERE entity_type = ?;
    """;

  private final FolioExecutionContext context;
  private final JdbcTemplate jdbcTemplate;


  public List<ReindexStatusEntity> getReindexStatuses() {
    var fullTableName = getFullTableName(context, REINDEX_STATUS_TABLE);
    var sql = SELECT_REINDEX_STATUS_BY_REINDEX_ID_SQL.formatted(fullTableName);
    return jdbcTemplate.query(sql, reindexStatusRowMapper());
  }

  public void setReindexUploadFailed(ReindexEntityType entityType) {
    var fullTableName = getFullTableName(context, REINDEX_STATUS_TABLE);
    var sql = UPDATE_SQL.formatted(
      fullTableName, "%s = ?, %s = ?".formatted(STATUS_COLUMN, END_TIME_UPLOAD_COLUMN));

    jdbcTemplate.update(sql, ReindexStatus.UPLOAD_FAILED.name(), Timestamp.from(Instant.now()), entityType.name());
  }

  public void addReindexCounts(ReindexEntityType entityType, int processedMergeRanges,
                               int processedUploadRanges) {
    var fullTableName = getFullTableName(context, REINDEX_STATUS_TABLE);
    var sql = UPDATE_REINDEX_COUNTS_SQL.formatted(fullTableName);

    jdbcTemplate.update(sql, processedMergeRanges, processedUploadRanges, entityType.name());
  }

  public void truncate() {
    var fullTableName = getFullTableName(context, REINDEX_STATUS_TABLE);
    String sql = "TRUNCATE TABLE %s;".formatted(fullTableName);
    jdbcTemplate.execute(sql);
  }

  public void setMergeReindexStarted(ReindexEntityType entityType, int totalMergeRanges) {
    var fullTableName = getFullTableName(context, REINDEX_STATUS_TABLE);
    var sql = UPDATE_SQL.formatted(
      fullTableName, "%s = ?, %s = ?".formatted(TOTAL_MERGE_RANGES_COLUMN, START_TIME_MERGE_COLUMN));

    jdbcTemplate.update(sql, totalMergeRanges, Timestamp.from(Instant.now()), entityType.name());
  }

  public void setMergeReindexFailed(List<ReindexEntityType> entityTypes) {
    var inTypes = entityTypes.stream()
      .map(entityType -> "'%s'".formatted(entityType.name()))
      .collect(Collectors.joining(","));
    var fullTableName = getFullTableName(context, REINDEX_STATUS_TABLE);
    var sql = UPDATE_FOR_ENTITIES_SQL.formatted(
      fullTableName, "%s = ?, %s = ?".formatted(STATUS_COLUMN, END_TIME_MERGE_COLUMN), inTypes);

    jdbcTemplate.update(sql, ReindexStatus.MERGE_FAILED.name(), Timestamp.from(Instant.now()));
  }

  public void saveReindexStatusRecords(List<ReindexStatusEntity> statusRecords) {
    var fullTableName = getFullTableName(context, REINDEX_STATUS_TABLE);
    jdbcTemplate.batchUpdate(INSERT_REINDEX_STATUS_SQL.formatted(fullTableName), statusRecords, 10,
      (statement, entity) -> {
        statement.setString(1, entity.getEntityType().name());
        statement.setString(2, entity.getStatus().name());
        statement.setInt(3, entity.getTotalMergeRanges());
        statement.setInt(4, entity.getProcessedMergeRanges());
        statement.setInt(5, entity.getTotalUploadRanges());
        statement.setInt(6, entity.getProcessedUploadRanges());
        statement.setTimestamp(7, entity.getStartTimeMerge());
        statement.setTimestamp(8, entity.getEndTimeMerge());
        statement.setTimestamp(9, entity.getStartTimeUpload());
        statement.setTimestamp(10, entity.getEndTimeUpload());
      });
  }

  private RowMapper<ReindexStatusEntity> reindexStatusRowMapper() {
    return (rs, rowNum) -> {
      var reindexStatus = new ReindexStatusEntity(
        ReindexEntityType.valueOf(rs.getString(ReindexStatusEntity.ENTITY_TYPE_COLUMN)),
        ReindexStatus.valueOf(rs.getString(STATUS_COLUMN))
      );
      reindexStatus.setTotalMergeRanges(rs.getInt(TOTAL_MERGE_RANGES_COLUMN));
      reindexStatus.setProcessedMergeRanges(rs.getInt(PROCESSED_MERGE_RANGES_COLUMN));
      reindexStatus.setTotalUploadRanges(rs.getInt(TOTAL_UPLOAD_RANGES_COLUMN));
      reindexStatus.setProcessedUploadRanges(rs.getInt(PROCESSED_UPLOAD_RANGES_COLUMN));
      reindexStatus.setStartTimeMerge(rs.getTimestamp(START_TIME_MERGE_COLUMN));
      reindexStatus.setEndTimeMerge(rs.getTimestamp(END_TIME_MERGE_COLUMN));
      reindexStatus.setStartTimeUpload(rs.getTimestamp(START_TIME_UPLOAD_COLUMN));
      reindexStatus.setEndTimeUpload(rs.getTimestamp(END_TIME_UPLOAD_COLUMN));
      return reindexStatus;
    };
  }
}
