package org.folio.search.service.reindex.jdbc;

import static org.folio.search.model.reindex.UploadRangeEntity.CREATED_AT_COLUMN;
import static org.folio.search.model.reindex.UploadRangeEntity.ENTITY_TYPE_COLUMN;
import static org.folio.search.model.reindex.UploadRangeEntity.FAIL_CAUSE_COLUMN;
import static org.folio.search.model.reindex.UploadRangeEntity.FINISHED_AT_COLUMN;
import static org.folio.search.model.reindex.UploadRangeEntity.ID_COLUMN;
import static org.folio.search.model.reindex.UploadRangeEntity.LOWER_BOUND_COLUMN;
import static org.folio.search.model.reindex.UploadRangeEntity.STATUS_COLUMN;
import static org.folio.search.model.reindex.UploadRangeEntity.UPPER_BOUND_COLUMN;
import static org.folio.search.service.reindex.ReindexConstants.UPLOAD_RANGE_TABLE;
import static org.folio.search.utils.JdbcUtils.getFullTableName;

import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.model.reindex.UploadRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexRangeStatus;
import org.folio.search.service.reindex.RangeGenerator;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public abstract class UploadRangeRepository extends ReindexJdbcRepository {

  protected static final String SELECT_RECORD_SQL = "SELECT * from %s WHERE id >= ? AND id <= ?;";
  protected static final String LAST_UPDATED_DATE_FIELD = "lastUpdatedDate";
  private static final String UPSERT_UPLOAD_RANGE_SQL = """
      INSERT INTO %s (id, entity_type, lower, upper, created_at, finished_at)
      VALUES (?, ?, ?, ?, ?, ?)
      ON CONFLICT (id)
      DO UPDATE SET finished_at = EXCLUDED.finished_at;
    """;

  private static final String SELECT_UPLOAD_RANGE_BY_ENTITY_TYPE_SQL = "SELECT * FROM %s WHERE entity_type = ?;";
  private static final TypeReference<LinkedHashSet<InstanceSubResource>> VALUE_TYPE_REF = new TypeReference<>() { };

  protected final ReindexConfigurationProperties reindexConfig;

  protected UploadRangeRepository(JdbcTemplate jdbcTemplate,
                                  JsonConverter jsonConverter,
                                  FolioExecutionContext context,
                                  ReindexConfigurationProperties reindexConfig) {
    super(jdbcTemplate, jsonConverter, context);
    this.reindexConfig = reindexConfig;
  }

  public List<UploadRangeEntity> getUploadRanges(boolean populateIfNotExist) {
    var fullTableName = getFullTableName(context, UPLOAD_RANGE_TABLE);
    var sql = SELECT_UPLOAD_RANGE_BY_ENTITY_TYPE_SQL.formatted(fullTableName);
    var uploadRanges = jdbcTemplate.query(sql, uploadRangeRowMapper(), entityType().getType());

    return populateIfNotExist && uploadRanges.isEmpty()
           ? prepareAndSaveUploadRanges()
           : uploadRanges;
  }

  public List<Map<String, Object>> fetchByIdRange(String lower, String upper) {
    var sql = getFetchBySql();
    return jdbcTemplate.query(sql, rowToMapMapper(), lower, upper);
  }

  public abstract SubResourceResult fetchByTimestamp(String tenant, Timestamp timestamp, int limit);

  protected String getFetchBySql() {
    return SELECT_RECORD_SQL.formatted(getFullTableName(context, entityTable()));
  }

  @Override
  protected String rangeTable() {
    return UPLOAD_RANGE_TABLE;
  }

  protected abstract RowMapper<Map<String, Object>> rowToMapMapper();

  protected Set<InstanceSubResource> parseInstanceSubResources(String instancesJson) {
    try {
      return jsonConverter.fromJson(instancesJson, VALUE_TYPE_REF);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  protected List<RangeGenerator.Range> createRanges() {
    var uploadRangeLevel = reindexConfig.getUploadRangeLevel();
    return RangeGenerator.createHexRanges(uploadRangeLevel);
  }

  private RowMapper<UploadRangeEntity> uploadRangeRowMapper() {
    return (rs, rowNum) -> {
      var uploadRange = new UploadRangeEntity(
        UUID.fromString(rs.getString(ID_COLUMN)),
        ReindexEntityType.fromValue(rs.getString(ENTITY_TYPE_COLUMN)),
        rs.getString(LOWER_BOUND_COLUMN),
        rs.getString(UPPER_BOUND_COLUMN),
        rs.getTimestamp(CREATED_AT_COLUMN),
        ReindexRangeStatus.valueOfNullable(rs.getString(STATUS_COLUMN)),
        rs.getString(FAIL_CAUSE_COLUMN)
      );
      uploadRange.setFinishedAt(rs.getTimestamp(FINISHED_AT_COLUMN));
      return uploadRange;
    };
  }

  private List<UploadRangeEntity> prepareAndSaveUploadRanges() {
    var ranges = createRanges()
      .stream()
      .map(range -> new UploadRangeEntity(UUID.randomUUID(), entityType(), range.lowerBound(), range.upperBound(),
        Timestamp.from(Instant.now()), null, null))
      .toList();

    upsertUploadRanges(ranges);

    return ranges;
  }

  private void upsertUploadRanges(List<UploadRangeEntity> uploadRanges) {
    var fullTableName = getFullTableName(context, UPLOAD_RANGE_TABLE);
    jdbcTemplate.batchUpdate(UPSERT_UPLOAD_RANGE_SQL.formatted(fullTableName), uploadRanges, BATCH_OPERATION_SIZE,
      (statement, entity) -> {
        statement.setObject(1, entity.getId());
        statement.setString(2, entity.getEntityType().getType());
        statement.setString(3, entity.getLower());
        statement.setString(4, entity.getUpper());
        statement.setTimestamp(5, entity.getCreatedAt());
        statement.setTimestamp(6, entity.getFinishedAt());
      });
  }
}
