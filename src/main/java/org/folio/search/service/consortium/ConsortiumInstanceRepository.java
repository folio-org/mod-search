package org.folio.search.service.consortium;

import static org.folio.search.service.consortium.ConsortiumSearchQueryBuilder.CONSORTIUM_TABLES;
import static org.folio.search.utils.JdbcUtils.getFullTableName;
import static org.folio.search.utils.JdbcUtils.getParamPlaceholder;

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
import org.folio.search.domain.dto.ConsortiumHolding;
import org.folio.search.domain.dto.ConsortiumItem;
import org.folio.search.model.types.ResourceType;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
@RequiredArgsConstructor
public class ConsortiumInstanceRepository {

  private static final String SELECT_BY_ID_SQL = "SELECT * FROM %s WHERE instance_id IN (%s)";
  private static final String DELETE_BY_TENANT_AND_ID_SQL = "DELETE FROM %s WHERE tenant_id = ? AND instance_id = ?;";
  private static final String DELETE_ALL_SQL = "TRUNCATE TABLE %s;";
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
      SELECT_BY_ID_SQL.formatted(getTableName(), getParamPlaceholder(instanceIds.size())),
      (rs, rowNum) -> toConsortiumInstance(rs),
      instanceIds.toArray());
  }

  public void save(List<ConsortiumInstance> instances) {
    log.debug("save::consortium instances [number: {}]", instances.size());
    jdbcTemplate.batchUpdate(
      UPSERT_SQL.formatted(getTableName()),
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
      DELETE_BY_TENANT_AND_ID_SQL.formatted(getTableName()),
      instanceIds,
      100,
      (PreparedStatement ps, ConsortiumInstanceId id) -> {
        ps.setString(1, id.tenantId());
        ps.setString(2, id.instanceId());
      }
    );
  }

  public List<ConsortiumHolding> fetchHoldings(ConsortiumSearchQueryBuilder searchQueryBuilder) {
    return jdbcTemplate.query(searchQueryBuilder.buildSelectQuery(context),
      (rs, rowNum) -> new ConsortiumHolding()
        .id(rs.getString("id"))
        .hrid(rs.getString("hrid"))
        .tenantId(rs.getString("tenantId"))
        .instanceId(rs.getString("instanceId"))
        .callNumberPrefix(rs.getString("callNumberPrefix"))
        .callNumber(rs.getString("callNumber"))
        .callNumberSuffix(rs.getString("callNumberSuffix"))
        .copyNumber(rs.getString("copyNumber"))
        .permanentLocationId(rs.getString("permanentLocationId"))
        .discoverySuppress(rs.getBoolean("discoverySuppress")),
      searchQueryBuilder.getQueryArguments()
    );
  }

  public Integer count(ConsortiumSearchQueryBuilder searchQueryBuilder) {
    return jdbcTemplate.queryForObject(searchQueryBuilder.buildCountQuery(context),
      Integer.class, searchQueryBuilder.getQueryArguments());
  }

  public List<ConsortiumItem> fetchItems(ConsortiumSearchQueryBuilder searchQueryBuilder) {
    return jdbcTemplate.query(searchQueryBuilder.buildSelectQuery(context),
      (rs, rowNum) -> new ConsortiumItem()
        .id(rs.getString("id"))
        .hrid(rs.getString("hrid"))
        .tenantId(rs.getString("tenantId"))
        .instanceId(rs.getString("instanceId"))
        .holdingsRecordId(rs.getString("holdingsRecordId"))
        .barcode(rs.getString("barcode")),
      searchQueryBuilder.getQueryArguments()
    );
  }

  public void deleteAll() {
    log.debug("deleteAll::consortium instances");
    jdbcTemplate.execute(DELETE_ALL_SQL.formatted(getTableName()));
  }

  private ConsortiumInstance toConsortiumInstance(ResultSet rs) throws SQLException {
    var id = new ConsortiumInstanceId(rs.getString(TENANT_ID_COLUMN), rs.getString(INSTANCE_ID_COLUMN));
    return new ConsortiumInstance(id, rs.getString(JSON_COLUMN));
  }

  private String getTableName() {
    return getFullTableName(context, CONSORTIUM_TABLES.get(ResourceType.INSTANCE));
  }
}
