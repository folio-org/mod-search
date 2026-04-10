package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.reindex.StreamingReindexStatusEntity;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
public class StreamingReindexStatusRepository {

  public static final String TABLE_NAME = "streaming_reindex_status";

  private static final String INSERT_SQL = """
    INSERT INTO %s (id, family_id, job_id, resource_type, status)
    VALUES (?, ?, ?, ?, ?);
    """;

  private static final String SELECT_BY_JOB_AND_RESOURCE_SQL =
    "SELECT * FROM %s WHERE job_id = ? AND resource_type = ?;";

  private static final String SELECT_BY_FAMILY_SQL =
    "SELECT * FROM %s WHERE family_id = ? ORDER BY id;";

  private static final String UPDATE_JOB_STATUS_SQL = """
    UPDATE %s SET status = ?
    WHERE job_id = ? AND status NOT IN ('COMPLETED', 'FAILED');
    """;

  private static final String UPDATE_RESOURCE_STATUS_SQL = "UPDATE %s SET status = ? WHERE id = ?;";

  private static final String DELETE_BY_FAMILY_ID_SQL = "DELETE FROM %s WHERE family_id = ?;";

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
      entity.getFamilyId(),
      entity.getJobId(),
      entity.getResourceType(),
      entity.getStatus());
  }

  public Optional<StreamingReindexStatusEntity> findByJobIdAndResourceType(UUID jobId, String resourceType) {
    var sql = SELECT_BY_JOB_AND_RESOURCE_SQL.formatted(getFullTableName(context, TABLE_NAME));
    var results = jdbcTemplate.query(sql, entityRowMapper(), jobId, resourceType);
    return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
  }

  public List<StreamingReindexStatusEntity> findByFamilyId(UUID familyId) {
    var sql = SELECT_BY_FAMILY_SQL.formatted(getFullTableName(context, TABLE_NAME));
    return jdbcTemplate.query(sql, entityRowMapper(), familyId);
  }

  public void updateStatus(UUID jobId, String status) {
    var sql = UPDATE_JOB_STATUS_SQL.formatted(getFullTableName(context, TABLE_NAME));
    jdbcTemplate.update(sql, status, jobId);
  }

  public void updateResourceStatus(UUID id, String status) {
    var sql = UPDATE_RESOURCE_STATUS_SQL.formatted(getFullTableName(context, TABLE_NAME));
    jdbcTemplate.update(sql, status, id);
  }

  public void deleteByFamilyId(UUID familyId) {
    var sql = DELETE_BY_FAMILY_ID_SQL.formatted(getFullTableName(context, TABLE_NAME));
    jdbcTemplate.update(sql, familyId);
  }

  private RowMapper<StreamingReindexStatusEntity> entityRowMapper() {
    return (rs, rowNum) -> new StreamingReindexStatusEntity(
      rs.getObject(StreamingReindexStatusEntity.ID_COLUMN, UUID.class),
      rs.getObject(StreamingReindexStatusEntity.FAMILY_ID_COLUMN, UUID.class),
      rs.getObject(StreamingReindexStatusEntity.JOB_ID_COLUMN, UUID.class),
      rs.getString(StreamingReindexStatusEntity.RESOURCE_TYPE_COLUMN),
      rs.getString(StreamingReindexStatusEntity.STATUS_COLUMN)
    );
  }
}
