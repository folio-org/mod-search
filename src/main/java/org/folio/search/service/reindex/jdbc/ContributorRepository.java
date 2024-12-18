package org.folio.search.service.reindex.jdbc;

import static org.folio.search.utils.JdbcUtils.getParamPlaceholder;
import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.CONTRIBUTOR_TYPE_FIELD;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.entity.InstanceContributorEntityAgg;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.utils.JdbcUtils;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
public class ContributorRepository extends UploadRangeRepository implements InstanceChildResourceRepository {

  // TODO: override shared based on tenant id
  private static final String SELECT_QUERY = """
    SELECT
        c.id,
        c.name,
        c.name_type_id,
        c.authority_id,
        json_agg(
            json_build_object(
                'count', sub.instance_count,
                'typeId', sub.type_ids,
                'shared', false,
                'tenantId', sub.tenant_id
            )
        ) AS instances
    FROM
      (
        SELECT
            ins.contributor_id,
            ins.tenant_id,
            sum(ins.count) as instance_count,
            array_agg(DISTINCT ins.type_id) FILTER (WHERE ins.type_id <> '') as type_ids
        FROM  %1$s.contributor_count ins
        WHERE
          %2$s
        GROUP BY
            ins.contributor_id,
            ins.tenant_id
        ) sub
    JOIN
        %1$s.contributor c ON c.id = sub.contributor_id
    WHERE
        %3$s
    GROUP BY
        c.id;
    """;

  private static final String UPDATE_COUNTS_QUERY = """
    UPDATE %s.contributor_count set count = count - 1
    WHERE contributor_id = ?
      and type_id = ?
      and tenant_id = ?
      and count > 0
    """;
  private static final String INSERT_ENTITIES_SQL = """
      INSERT INTO %s.contributor (id, name, name_type_id, authority_id)
      VALUES (?, ?, ?, ?)
      ON CONFLICT DO NOTHING;
    """;
  private static final String INSERT_RELATIONS_SQL = """
      INSERT INTO %s.contributor_count (contributor_id, type_id, tenant_id, count)
      VALUES ( ?, ?, ?, 1)
      ON CONFLICT (contributor_id, type_id, tenant_id) DO UPDATE SET count = %s.contributor_count.count +1;
    """;

  private static final String ID_RANGE_INS_WHERE_CLAUSE = "ins.contributor_id >= ? AND ins.contributor_id <= ?";
  private static final String ID_RANGE_CONTR_WHERE_CLAUSE = "c.id >= ? AND c.id <= ?";
  private static final String IDS_INS_WHERE_CLAUSE = "ins.contributor_id IN (%1$s)";
  private static final String IDS_CONTR_WHERE_CLAUSE = "c.id IN (%1$s)";

  protected ContributorRepository(JdbcTemplate jdbcTemplate, JsonConverter jsonConverter,
                                  FolioExecutionContext context,
                                  ReindexConfigurationProperties reindexConfig) {
    super(jdbcTemplate, jsonConverter, context, reindexConfig);
  }

  @Override
  public ReindexEntityType entityType() {
    return ReindexEntityType.CONTRIBUTOR;
  }

  @Override
  protected String entityTable() {
    return ReindexConstants.CONTRIBUTOR_TABLE;
  }

  @Override
  protected Optional<String> subEntityTable() {
    return Optional.of(ReindexConstants.INSTANCE_CONTRIBUTOR_TABLE);
  }

  public List<InstanceContributorEntityAgg> fetchByIds(List<String> ids) {
    if (CollectionUtils.isEmpty(ids)) {
      return Collections.emptyList();
    }
    var sql = SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context),
      IDS_INS_WHERE_CLAUSE.formatted(getParamPlaceholder(ids.size())),
      IDS_CONTR_WHERE_CLAUSE.formatted(getParamPlaceholder(ids.size()))
    );
    return jdbcTemplate.query(sql, instanceAggRowMapper(), ListUtils.union(ids, ids).toArray());
  }

  @Override
  public List<Map<String, Object>> fetchByIdRange(String lower, String upper) {
    var sql = getFetchBySql();
    return jdbcTemplate.query(sql, rowToMapMapper(), lower, upper, lower, upper);
  }

  @Override
  protected String getFetchBySql() {
    return SELECT_QUERY.formatted(JdbcUtils.getSchemaName(context),
      ID_RANGE_INS_WHERE_CLAUSE, ID_RANGE_CONTR_WHERE_CLAUSE);
  }

  @Override
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return (rs, rowNum) -> {
      Map<String, Object> contributor = new HashMap<>();
      contributor.put("id", getId(rs));
      contributor.put("name", getName(rs));
      contributor.put("contributorNameTypeId", getNameTypeId(rs));
      contributor.put(AUTHORITY_ID_FIELD, getAuthorityId(rs));

      var maps = jsonConverter.fromJsonToListOfMaps(getInstances(rs));
      contributor.put("instances", maps);

      return contributor;
    };
  }

  @Override
  public void updateCounts(List<Map<String, Object>> relations) {
    var sql = UPDATE_COUNTS_QUERY.formatted(JdbcUtils.getSchemaName(context));
    try {
      jdbcTemplate.batchUpdate(sql, relations, BATCH_OPERATION_SIZE,
        (statement, relation) -> {
          statement.setString(1, (String) relation.get("contributorId"));
          statement.setString(2, (String) relation.get(CONTRIBUTOR_TYPE_FIELD));
          statement.setString(3, (String) relation.get("tenantId"));
        });
    } catch (DataAccessException e) {
      log.warn("updateCounts::Failed to save relations batch. Starting processing one-by-one", e);
      for (var relation : relations) {
        jdbcTemplate.update(sql, relation.get("contributorId"),
          relation.get(CONTRIBUTOR_TYPE_FIELD), relation.get("tenantId"));
      }
    }
  }

  @Override
  public void saveAll(Set<Map<String, Object>> entities, List<Map<String, Object>> entityRelations) {
    String schemaName = JdbcUtils.getSchemaName(context);
    var entitiesSql = INSERT_ENTITIES_SQL.formatted(schemaName);
    try {
      jdbcTemplate.batchUpdate(entitiesSql, entities, BATCH_OPERATION_SIZE,
        (statement, entity) -> {
          statement.setString(1, (String) entity.get("id"));
          statement.setString(2, (String) entity.get("name"));
          statement.setObject(3, entity.get("nameTypeId"));
          statement.setObject(4, entity.get(AUTHORITY_ID_FIELD));
        });
    } catch (DataAccessException e) {
      log.warn("saveAll::Failed to save entities batch. Starting processing one-by-one", e);
      for (var entity : entities) {
        jdbcTemplate.update(entitiesSql,
          entity.get("id"), entity.get("name"), entity.get("nameTypeId"), entity.get(AUTHORITY_ID_FIELD));
      }
    }

    var relationsSql = INSERT_RELATIONS_SQL.formatted(schemaName, schemaName);

    for (var entityRelation : entityRelations) {
      jdbcTemplate.update(relationsSql, entityRelation.get("contributorId"),
        entityRelation.get(CONTRIBUTOR_TYPE_FIELD), entityRelation.get("tenantId"));
    }
  }

  private RowMapper<InstanceContributorEntityAgg> instanceAggRowMapper() {
    return (rs, rowNum) -> new InstanceContributorEntityAgg(
      getId(rs),
      getName(rs),
      getNameTypeId(rs),
      getAuthorityId(rs),
      parseInstanceSubResources(getInstances(rs))
    );
  }

  private String getId(ResultSet rs) throws SQLException {
    return rs.getString("id");
  }

  private String getName(ResultSet rs) throws SQLException {
    return rs.getString("name");
  }

  private String getNameTypeId(ResultSet rs) throws SQLException {
    return rs.getString("name_type_id");
  }

  private String getAuthorityId(ResultSet rs) throws SQLException {
    return rs.getString("authority_id");
  }

  private String getInstances(ResultSet rs) throws SQLException {
    return rs.getString("instances");
  }

}
