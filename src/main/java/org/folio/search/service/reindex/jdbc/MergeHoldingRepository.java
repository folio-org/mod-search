package org.folio.search.service.reindex.jdbc;

import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.ReindexConstants;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MergeHoldingRepository extends MergeRangeRepository {

  protected MergeHoldingRepository(JdbcTemplate jdbcTemplate,
                                   JsonConverter jsonConverter,
                                   FolioExecutionContext context) {
    super(jdbcTemplate, jsonConverter, context);
  }

  @Override
  public ReindexEntityType entityType() {
    return null;
  }

  @Override
  protected String entityTable() {
    return ReindexConstants.HOLDING_TABLE;
  }
}
