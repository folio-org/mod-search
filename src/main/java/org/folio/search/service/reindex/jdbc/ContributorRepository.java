package org.folio.search.service.reindex.jdbc;

import java.util.HashMap;
import java.util.Map;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ContributorRepository extends UploadRangeRepository {

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
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return (rs, rowNum) -> {
      Map<String, Object> contributor = new HashMap<>();
      contributor.put("id", rs.getString("id"));
      contributor.put("name", rs.getString("name"));
      contributor.put("contributorNameTypeId", rs.getString("contributor_name_type_id"));
      contributor.put("authorityId", rs.getString("authority_id"));

      var maps = jsonConverter.fromJsonToListOfMaps(rs.getString("instances"));
      contributor.put("instances", maps);

      return contributor;
    };
  }

  @Override
  protected String entityTable() {
    return ReindexConstants.CONTRIBUTOR_TABLE;
  }
}
