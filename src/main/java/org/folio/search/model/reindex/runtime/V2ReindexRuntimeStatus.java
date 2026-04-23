package org.folio.search.model.reindex.runtime;

public enum V2ReindexRuntimeStatus {
  PENDING,
  IN_PROGRESS,
  COMPLETED,
  FAILED;

  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED;
  }
}
