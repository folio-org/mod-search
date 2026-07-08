package org.folio.search.model.reconciliation;

import java.util.Map;

public record ReconciliationReport(
  String tenantId,
  Status overallStatus,
  Map<String, IndexComparison> opensearch,
  Map<String, TableComparison> postgres
) {

  public enum Status { MATCH, MISMATCH, ERROR }

  public record IndexComparison(
    String baselineIndex,
    String currentIndex,
    long baselineCount,
    long currentCount,
    Status status,
    String error
  ) {}

  public record TableComparison(
    String baselineSchema,
    String currentSchema,
    long baselineCount,
    long currentCount,
    Status status,
    String error
  ) {}
}
