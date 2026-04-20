package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getFullTableName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.reindex.IndexFamilyEntity;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.search.model.types.QueryVersion;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
public class IndexFamilyRepository {

  public static final String INDEX_FAMILY_TABLE = "index_family";

  private static final String INSERT_SQL = """
    INSERT INTO %s (id, generation, index_name, status, created_at, activated_at, retired_at, query_version)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?);
    """;

  private static final String SELECT_BY_ID_SQL = "SELECT * FROM %s WHERE id = ?;";

  private static final String SELECT_BY_STATUS_AND_VERSION_SQL =
    "SELECT * FROM %s WHERE status = ? AND query_version = ?;";

  private static final String SELECT_BY_VERSION_SQL =
    "SELECT * FROM %s WHERE query_version = ? ORDER BY generation, created_at, id;";

  private static final String SELECT_ACTIVE_BY_VERSION_SQL =
    "SELECT * FROM %s WHERE status = 'ACTIVE' AND query_version = ?;";

  private static final String SELECT_ALL_SQL =
    "SELECT * FROM %s ORDER BY generation;";

  private static final String LOCK_BY_VERSION_SQL =
    "SELECT id FROM %s WHERE query_version = ? FOR UPDATE;";

  private static final String UPDATE_STATUS_SQL = """
    UPDATE %s
    SET status = ?,
        activated_at = CASE
          WHEN ? = 'ACTIVE' THEN COALESCE(activated_at, CURRENT_TIMESTAMP)
          ELSE activated_at
        END,
        retired_at = CASE
          WHEN ? = 'RETIRED' THEN COALESCE(retired_at, CURRENT_TIMESTAMP)
          ELSE retired_at
        END
    WHERE id = ?;
    """;

  private static final String UPDATE_REPRESENTATION_SQL = """
    UPDATE %s
    SET index_name = ?,
        status = ?,
        activated_at = CASE
          WHEN ? = 'ACTIVE' THEN COALESCE(activated_at, CURRENT_TIMESTAMP)
          ELSE activated_at
        END,
        retired_at = CASE
          WHEN ? = 'RETIRED' THEN COALESCE(retired_at, CURRENT_TIMESTAMP)
          WHEN ? IN ('ACTIVE', 'BUILDING') THEN NULL
          ELSE retired_at
        END
    WHERE id = ?;
    """;

  private static final String DELETE_BY_ID_SQL = "DELETE FROM %s WHERE id = ?;";

  private static final String SELECT_MAX_GENERATION_SQL =
    "SELECT COALESCE(MAX(generation), -1) FROM %s WHERE query_version = ?;";

  private final JdbcTemplate jdbcTemplate;
  private final FolioExecutionContext context;

  public IndexFamilyRepository(JdbcTemplate jdbcTemplate, FolioExecutionContext context) {
    this.jdbcTemplate = jdbcTemplate;
    this.context = context;
  }

  public void create(IndexFamilyEntity entity) {
    var sql = INSERT_SQL.formatted(getFullTableName(context, INDEX_FAMILY_TABLE));
    jdbcTemplate.update(sql,
      entity.getId(),
      entity.getGeneration(),
      entity.getIndexName(),
      entity.getStatus().name(),
      entity.getCreatedAt(),
      entity.getActivatedAt(),
      entity.getRetiredAt(),
      entity.getQueryVersion().getValue());
  }

  public Optional<IndexFamilyEntity> findById(UUID id) {
    var sql = SELECT_BY_ID_SQL.formatted(getFullTableName(context, INDEX_FAMILY_TABLE));
    var results = jdbcTemplate.query(sql, entityRowMapper(), id);
    return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
  }

  public List<IndexFamilyEntity> findByStatusAndVersion(IndexFamilyStatus status, QueryVersion version) {
    var sql = SELECT_BY_STATUS_AND_VERSION_SQL.formatted(
      getFullTableName(context, INDEX_FAMILY_TABLE));
    return jdbcTemplate.query(sql, entityRowMapper(), status.name(), version.getValue());
  }

  public List<IndexFamilyEntity> findByVersion(QueryVersion version) {
    var sql = SELECT_BY_VERSION_SQL.formatted(getFullTableName(context, INDEX_FAMILY_TABLE));
    return jdbcTemplate.query(sql, entityRowMapper(), version.getValue());
  }

  public Optional<IndexFamilyEntity> findActiveByVersion(QueryVersion version) {
    var sql = SELECT_ACTIVE_BY_VERSION_SQL.formatted(
      getFullTableName(context, INDEX_FAMILY_TABLE));
    var results = jdbcTemplate.query(sql, entityRowMapper(), version.getValue());
    return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
  }

  public List<IndexFamilyEntity> findAll() {
    var sql = SELECT_ALL_SQL.formatted(getFullTableName(context, INDEX_FAMILY_TABLE));
    return jdbcTemplate.query(sql, entityRowMapper());
  }

  public void lockByVersion(QueryVersion version) {
    var sql = LOCK_BY_VERSION_SQL.formatted(getFullTableName(context, INDEX_FAMILY_TABLE));
    jdbcTemplate.queryForList(sql, UUID.class, version.getValue());
  }

  public void updateStatus(UUID id, IndexFamilyStatus status) {
    var sql = UPDATE_STATUS_SQL.formatted(getFullTableName(context, INDEX_FAMILY_TABLE));
    jdbcTemplate.update(sql, status.name(), status.name(), status.name(), id);
  }

  public void updateRepresentation(UUID id, String indexName, IndexFamilyStatus status) {
    var sql = UPDATE_REPRESENTATION_SQL.formatted(getFullTableName(context, INDEX_FAMILY_TABLE));
    jdbcTemplate.update(sql, indexName, status.name(), status.name(), status.name(), status.name(), id);
  }

  public void deleteById(UUID id) {
    var sql = DELETE_BY_ID_SQL.formatted(getFullTableName(context, INDEX_FAMILY_TABLE));
    jdbcTemplate.update(sql, id);
  }

  public int getNextGeneration(QueryVersion version) {
    var sql = SELECT_MAX_GENERATION_SQL.formatted(getFullTableName(context, INDEX_FAMILY_TABLE));
    var maxGeneration = jdbcTemplate.queryForObject(sql, Integer.class, version.getValue());
    return (maxGeneration != null ? maxGeneration : -1) + 1;
  }

  private RowMapper<IndexFamilyEntity> entityRowMapper() {
    return (rs, rowNum) -> new IndexFamilyEntity(
      rs.getObject(IndexFamilyEntity.ID_COLUMN, UUID.class),
      rs.getInt(IndexFamilyEntity.GENERATION_COLUMN),
      rs.getString(IndexFamilyEntity.INDEX_NAME_COLUMN),
      IndexFamilyStatus.fromValue(rs.getString(IndexFamilyEntity.STATUS_COLUMN)),
      rs.getTimestamp(IndexFamilyEntity.CREATED_AT_COLUMN),
      rs.getTimestamp(IndexFamilyEntity.ACTIVATED_AT_COLUMN),
      rs.getTimestamp(IndexFamilyEntity.RETIRED_AT_COLUMN),
      QueryVersion.fromString(rs.getString(IndexFamilyEntity.QUERY_VERSION_COLUMN))
    );
  }
}
