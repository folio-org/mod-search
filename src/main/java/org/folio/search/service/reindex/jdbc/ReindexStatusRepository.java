package org.folio.search.service.reindex.jdbc;

import static org.folio.search.model.reindex.ReindexStatusEntity.END_TIME_MERGE_COLUMN;
import static org.folio.search.model.reindex.ReindexStatusEntity.END_TIME_UPLOAD_COLUMN;
import static org.folio.search.model.reindex.ReindexStatusEntity.PROCESSED_MERGE_RANGES_COLUMN;
import static org.folio.search.model.reindex.ReindexStatusEntity.PROCESSED_UPLOAD_RANGES_COLUMN;
import static org.folio.search.model.reindex.ReindexStatusEntity.REINDEX_ID_COLUMN;
import static org.folio.search.model.reindex.ReindexStatusEntity.REINDEX_STATUS_TABLE;
import static org.folio.search.model.reindex.ReindexStatusEntity.START_TIME_MERGE_COLUMN;
import static org.folio.search.model.reindex.ReindexStatusEntity.START_TIME_UPLOAD_COLUMN;
import static org.folio.search.model.reindex.ReindexStatusEntity.STATUS_COLUMN;
import static org.folio.search.model.reindex.ReindexStatusEntity.TOTAL_MERGE_RANGES_COLUMN;
import static org.folio.search.model.reindex.ReindexStatusEntity.TOTAL_UPLOAD_RANGES_COLUMN;
import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

  private static final String SELECT_REINDEX_STATUS_BY_REINDEX_ID_SQL = "SELECT * FROM %s WHERE reindex_id = ?;";
  private static final String UPDATE_REINDEX_STATUS_SQL = """
    UPDATE %s
    SET status = ?, %s = ?
    WHERE reindex_id = ? AND entity_type = ?;
    """;
  private static final String UPDATE_REINDEX_COUNTS_SQL = """
    UPDATE %s
    SET processed_merge_ranges = processed_merge_ranges + ?,
    processed_upload_ranges = processed_upload_ranges + ?
    WHERE reindex_id = ? AND entity_type = ?;
    """;

  private final FolioExecutionContext context;
  private final JdbcTemplate jdbcTemplate;


  public List<ReindexStatusEntity> getReindexStatuses(UUID reindexId) {
    var fullTableName = getFullTableName(context, REINDEX_STATUS_TABLE);
    var sql = SELECT_REINDEX_STATUS_BY_REINDEX_ID_SQL.formatted(fullTableName);
    return jdbcTemplate.query(sql, reindexStatusRowMapper(), reindexId);
  }

  public void setReindexUploadFailed(UUID reindexId, ReindexEntityType entityType) {
    var fullTableName = getFullTableName(context, REINDEX_STATUS_TABLE);
    var sql = UPDATE_REINDEX_STATUS_SQL.formatted(fullTableName, END_TIME_UPLOAD_COLUMN);

    jdbcTemplate.update(sql, ReindexStatus.UPLOAD_FAILED.name(), Timestamp.from(Instant.now()), reindexId,
      entityType.name());
  }

  public void addReindexCounts(UUID reindexId, ReindexEntityType entityType, int processedMergeRanges,
                               int processedUploadRanges) {
    var fullTableName = getFullTableName(context, REINDEX_STATUS_TABLE);
    var sql = UPDATE_REINDEX_COUNTS_SQL.formatted(fullTableName);

    jdbcTemplate.update(sql, processedMergeRanges, processedUploadRanges, reindexId, entityType.name());
  }

  private RowMapper<ReindexStatusEntity> reindexStatusRowMapper() {
    return (rs, rowNum) -> {
      var reindexStatus = new ReindexStatusEntity(
        UUID.fromString(rs.getString(REINDEX_ID_COLUMN)),
        ReindexEntityType.valueOf(rs.getString(ReindexStatusEntity.ENTITY_TYPE_COLUMN))
      );
      reindexStatus.setStatus(ReindexStatus.valueOf(rs.getString(STATUS_COLUMN)));
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
