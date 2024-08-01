package org.folio.search.service.reindex.jdbc;

import java.util.HashMap;
import java.util.Map;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ClassificationJdbcRepository extends ReindexJdbcRepository {

  protected ClassificationJdbcRepository(JdbcTemplate jdbcTemplate, JsonConverter jsonConverter,
                                         FolioExecutionContext context,
                                         ReindexConfigurationProperties reindexConfigurationProperties) {
    super(jdbcTemplate, jsonConverter, context, reindexConfigurationProperties);
  }

  @Override
  public ReindexEntityType entityType() {
    return ReindexEntityType.CLASSIFICATION;
  }

  @Override
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return (rs, rowNum) -> {
      Map<String, Object> subject = new HashMap<>();
      subject.put("id", rs.getString("id"));
      subject.put("number", rs.getString("number"));
      subject.put("typeId", rs.getString("type_id"));

      var maps = jsonConverter.fromJsonToListOfMaps(rs.getString("instances"));
      subject.put("instances", maps);

      return subject;
    };
  }

  @Override
  protected String entityTable() {
    return "classification";
  }
}
