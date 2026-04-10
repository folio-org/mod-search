package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.reindex.StreamingReindexStatusEntity;
import org.folio.search.model.types.StreamingReindexStatus;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
public class StreamingReindexStatusRepository {

  public static final String TABLE_NAME = "streaming_reindex_status";

  private static final String INSERT_SQL = """
    INSERT INTO %s (id, tenant_id, family_id, resource_type, status, records_processed, started_at, job_id)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?);
    """;

  private static final String SELECT_BY_JOB_AND_RESOURCE_SQL = """
    SELECT * FROM %s WHERE job_id = ? AND resource_type = ?;
    """;

  private static final String SELECT_BY_JOB_SQL = "SELECT * FROM %s WHERE job_id = ?;";

  private static final String SELECT_BY_FAMILY_SQL = """
    SELECT * FROM %s WHERE family_id = ? ORDER BY started_at, id;
    """;

  private static final String UPDATE_STATUS_SQL = """
    UPDATE %s SET status = ?, error_message = ?, completed_at = ?
    WHERE job_id = ? AND status NOT IN ('COMPLETED', 'FAILED');
    """;

  private static final String UPDATE_RESOURCE_STATUS_SQL = """
    UPDATE %s SET status = ?, error_message = ?, completed_at = ? WHERE id = ?;
    """;

  private static final String UPDATE_RECORDS_PROCESSED_SQL = """
    UPDATE %s SET records_processed = ? WHERE id = ?;
    """;

  private static final String DELETE_BY_FAMILY_ID_SQL = "DELETE FROM %s WHERE family_id = ?;";

  private static final String UPDATE_FAILED_BATCHES_SQL = """
    UPDATE %s SET failed_batches = ? WHERE id = ?;
    """;

  private final JdbcTemplate jdbcTemplate;
  private final FolioExecutionContext context;

  public StreamingReindexStatusRepository(JdbcTemplate jdbcTemplate, FolioExecutionContext context) {
    this.jdbcTemplate = jdbcTemplate;
    this.context = context;
  }

  public void create(StreamingReindexStatusEntity entity) {
    var sql = INSERT_SQL.formatted(getFullTableName(context, TABLE_NAME));
    jdbcTemplate.update(sql,
      entity.getId(),
      entity.getTenantId(),
      entity.getFamilyId(),
      entity.getResourceType(),
      entity.getStatus(),
      entity.getRecordsProcessed(),
      entity.getStartedAt(),
      entity.getJobId());
  }

  public Optional<StreamingReindexStatusEntity> findByJobIdAndResourceType(UUID jobId, String resourceType) {
    var sql = SELECT_BY_JOB_AND_RESOURCE_SQL.formatted(getFullTableName(context, TABLE_NAME));
    var results = jdbcTemplate.query(sql, entityRowMapper(), jobId, resourceType);
    return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
  }

  public List<StreamingReindexStatusEntity> findByJobId(UUID jobId) {
    var sql = SELECT_BY_JOB_SQL.formatted(getFullTableName(context, TABLE_NAME));
    return jdbcTemplate.query(sql, entityRowMapper(), jobId);
  }

  public List<StreamingReindexStatusEntity> findByFamilyId(UUID familyId) {
    var sql = SELECT_BY_FAMILY_SQL.formatted(getFullTableName(context, TABLE_NAME));
    return jdbcTemplate.query(sql, entityRowMapper(), familyId);
  }

  public void updateStatus(UUID jobId, String status, String errorMessage) {
    var sql = UPDATE_STATUS_SQL.formatted(getFullTableName(context, TABLE_NAME));
    var completedAt = isCompletedStatus(status) ? Timestamp.from(Instant.now()) : null;
    jdbcTemplate.update(sql, status, errorMessage, completedAt, jobId);
  }

  public void updateResourceStatus(UUID id, String status, String errorMessage) {
    var sql = UPDATE_RESOURCE_STATUS_SQL.formatted(getFullTableName(context, TABLE_NAME));
    var completedAt = isCompletedStatus(status) ? Timestamp.from(Instant.now()) : null;
    jdbcTemplate.update(sql, status, errorMessage, completedAt, id);
  }

  public void updateRecordsProcessed(UUID id, long recordsProcessed) {
    var sql = UPDATE_RECORDS_PROCESSED_SQL.formatted(getFullTableName(context, TABLE_NAME));
    jdbcTemplate.update(sql, recordsProcessed, id);
  }

  public void deleteByFamilyId(UUID familyId) {
    var sql = DELETE_BY_FAMILY_ID_SQL.formatted(getFullTableName(context, TABLE_NAME));
    jdbcTemplate.update(sql, familyId);
  }

  public void updateFailedBatches(UUID id, long failedBatches) {
    var sql = UPDATE_FAILED_BATCHES_SQL.formatted(getFullTableName(context, TABLE_NAME));
    jdbcTemplate.update(sql, failedBatches, id);
  }

  private static boolean isCompletedStatus(String status) {
    try {
      return StreamingReindexStatus.valueOf(status).isTerminal();
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private RowMapper<StreamingReindexStatusEntity> entityRowMapper() {
    return (rs, rowNum) -> {
      var entity = new StreamingReindexStatusEntity(
        rs.getObject(StreamingReindexStatusEntity.ID_COLUMN, UUID.class),
        rs.getString(StreamingReindexStatusEntity.TENANT_ID_COLUMN),
        rs.getObject(StreamingReindexStatusEntity.FAMILY_ID_COLUMN, UUID.class),
        rs.getString(StreamingReindexStatusEntity.RESOURCE_TYPE_COLUMN),
        rs.getString(StreamingReindexStatusEntity.STATUS_COLUMN),
        rs.getLong(StreamingReindexStatusEntity.RECORDS_PROCESSED_COLUMN),
        rs.getTimestamp(StreamingReindexStatusEntity.STARTED_AT_COLUMN),
        rs.getTimestamp(StreamingReindexStatusEntity.COMPLETED_AT_COLUMN),
        rs.getString(StreamingReindexStatusEntity.ERROR_MESSAGE_COLUMN)
      );
      entity.setJobId(rs.getObject("job_id", UUID.class));
      entity.setFailedBatches(rs.getLong(StreamingReindexStatusEntity.FAILED_BATCHES_COLUMN));
      return entity;
    };
  }
}
