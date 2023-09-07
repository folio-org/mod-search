package org.folio.search.service.consortium;

import static java.util.Collections.nCopies;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
@RequiredArgsConstructor
public class ConsortiumInstanceRepository {

  private static final String CONSORTIUM_INSTANCE_TABLE_NAME = "consortium_instance";
  private static final String SELECT_BY_ID_SQL = "SELECT * FROM %s WHERE instance_id IN (%s)";
  private static final String DELETE_BY_TENANT_AND_ID_SQL = "DELETE FROM %s WHERE tenant_id = ? AND instance_id = ?;";
  private static final String UPSERT_SQL = """
      INSERT INTO %s (tenant_id, instance_id, json, created_date, updated_date)
      VALUES (?, ?, ?::json, ?, ?)
      ON CONFLICT (tenant_id, instance_id)
      DO UPDATE SET json = EXCLUDED.json, updated_date = EXCLUDED.updated_date;
    """;
  private static final String TENANT_ID_COLUMN = "tenant_id";
  private static final String INSTANCE_ID_COLUMN = "instance_id";
  private static final String JSON_COLUMN = "json";

  private final JdbcTemplate jdbcTemplate;
  private final FolioExecutionContext context;

  public List<ConsortiumInstance> fetch(List<String> instanceIds) {
    log.debug("fetch::consortium instances by [ids: {}]", instanceIds);
    return jdbcTemplate.query(
      SELECT_BY_ID_SQL.formatted(getFullTableName(), String.join(",", nCopies(instanceIds.size(), "?"))),
      (rs, rowNum) -> toConsortiumInstance(rs),
      instanceIds.toArray());
  }

  public void save(List<ConsortiumInstance> instances) {
    log.debug("save::consortium instances [number: {}]", instances.size());
    jdbcTemplate.batchUpdate(
      UPSERT_SQL.formatted(getFullTableName()),
      instances,
      100,
      (PreparedStatement ps, ConsortiumInstance item) -> {
        ps.setString(1, item.id().tenantId());
        ps.setString(2, item.id().instanceId());
        ps.setString(3, item.instance());
        ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now(ZoneId.systemDefault())));
        ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now(ZoneId.systemDefault())));
      });
  }

  public void delete(Set<ConsortiumInstanceId> instanceIds) {
    log.debug("delete::consortium instances [tenant-instanceIds: {}]", instanceIds);
    jdbcTemplate.batchUpdate(
      DELETE_BY_TENANT_AND_ID_SQL.formatted(getFullTableName()),
      instanceIds,
      100,
      (PreparedStatement ps, ConsortiumInstanceId id) -> {
        ps.setString(1, id.tenantId());
        ps.setString(2, id.instanceId());
      }
    );
  }

  private ConsortiumInstance toConsortiumInstance(ResultSet rs) throws SQLException {
    var id = new ConsortiumInstanceId(rs.getString(TENANT_ID_COLUMN), rs.getString(INSTANCE_ID_COLUMN));
    return new ConsortiumInstance(id, rs.getString(JSON_COLUMN));
  }

  private String getFullTableName() {
    var dbSchemaName = context.getFolioModuleMetadata().getDBSchemaName(context.getTenantId());
    return dbSchemaName + "." + CONSORTIUM_INSTANCE_TABLE_NAME;
  }
}
