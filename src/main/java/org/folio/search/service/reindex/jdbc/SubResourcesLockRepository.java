package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getSchemaName;

import java.sql.Timestamp;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.spring.FolioModuleMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SubResourcesLockRepository {

  private static final String LOCK_SUB_RESOURCE_SQL = """
    UPDATE %s.sub_resources_lock
    SET locked_flag = TRUE
    WHERE entity_type = ? AND locked_flag = FALSE
    RETURNING last_updated_date
    """;

  private static final String UNLOCK_SUB_RESOURCE_SQL = """
    UPDATE %s.sub_resources_lock
    SET locked_flag = FALSE, last_updated_date = ?
    WHERE entity_type = ?
    """;

  private final JdbcTemplate jdbcTemplate;
  private final FolioModuleMetadata moduleMetadata;

  public Optional<Timestamp> lockSubResource(ReindexEntityType entityType, String tenantId) {
    var formattedSql = formatSqlWithSchema(LOCK_SUB_RESOURCE_SQL, tenantId);
    return jdbcTemplate.query(
      formattedSql,
      rs -> rs.next() ? Optional.of(rs.getTimestamp(1)) : Optional.empty(),
      entityType.getType()
    );
  }

  public void unlockSubResource(ReindexEntityType entityType, Timestamp lastUpdatedDate, String tenantId) {

    var formattedSql = formatSqlWithSchema(UNLOCK_SUB_RESOURCE_SQL, tenantId);
    jdbcTemplate.update(formattedSql, lastUpdatedDate, entityType.getType());
  }

  private String formatSqlWithSchema(String sqlTemplate, String tenantId) {
    return sqlTemplate.formatted(getSchemaName(tenantId, moduleMetadata));
  }
}
