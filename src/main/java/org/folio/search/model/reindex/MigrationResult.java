package org.folio.search.model.reindex;

import lombok.Data;

@Data
public class MigrationResult {

  private long duration;
  private long totalInstances;
  private long totalHoldings;
  private long totalItems;
  private long totalRelationships;
}
