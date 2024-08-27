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
public class ClassificationRepository extends UploadRangeRepository {

  protected ClassificationRepository(JdbcTemplate jdbcTemplate,
                                     JsonConverter jsonConverter,
                                     FolioExecutionContext context,
                                     ReindexConfigurationProperties reindexConfig) {
    super(jdbcTemplate, jsonConverter, context, reindexConfig);
  }

  @Override
  public ReindexEntityType entityType() {
    return ReindexEntityType.CLASSIFICATION;
  }

  @Override
  protected RowMapper<Map<String, Object>> rowToMapMapper() {
    return (rs, rowNum) -> {
      Map<String, Object> classification = new HashMap<>();
      classification.put("id", rs.getString("id"));
      classification.put("number", rs.getString("number"));
      classification.put("typeId", rs.getString("type_id"));

      var maps = jsonConverter.fromJsonToListOfMaps(rs.getString("instances"));
      classification.put("instances", maps);

      return classification;
    };
  }

  @Override
  protected String entityTable() {
    return ReindexConstants.CLASSIFICATION_TABLE;
  }
}
