package org.folio.search.model.types;

import lombok.Getter;

@Getter
public enum ReindexStatus {

  MERGE_IN_PROGRESS("Merge In Progress"),
  MERGE_COMPLETED("Merge Completed"),
  UPLOAD_IN_PROGRESS("Upload In Progress"),
  UPLOAD_COMPLETED("Upload Completed"),
  UPLOAD_FAILED("Upload Failed");

  private final String value;

  ReindexStatus(String value) {
    this.value = value;
  }
}
