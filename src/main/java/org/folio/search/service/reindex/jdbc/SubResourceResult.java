package org.folio.search.service.reindex.jdbc;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

public record SubResourceResult(List<Map<String, Object>> records, Timestamp lastUpdateDate) {

  public boolean hasRecords() {
    return !records.isEmpty();
  }
}
