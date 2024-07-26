package org.folio.search.service.reindex.jdbc;

import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ClassificationJdbcRepository extends ReindexJdbcRepository {

  protected ClassificationJdbcRepository(JdbcTemplate jdbcTemplate, FolioExecutionContext context,
                                         ReindexConfigurationProperties reindexConfigurationProperties) {
    super(jdbcTemplate, context, reindexConfigurationProperties);
  }

  @Override
  public ReindexEntityType entityType() {
    return ReindexEntityType.CLASSIFICATION;
  }

  @Override
  protected String entityTable() {
    return "classification";
  }
}
