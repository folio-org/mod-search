package org.folio.search.model.types;

public enum StreamingReindexStatus {
  PENDING,
  IN_PROGRESS,
  COMPLETED,
  STREAMED,
  FAILED;

  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED || this == STREAMED;
  }
}
