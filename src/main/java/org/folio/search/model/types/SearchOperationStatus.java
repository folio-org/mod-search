package org.folio.search.model.types;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SearchOperationStatus {

  SUCCESS("success"),
  ERROR("error");

  @JsonValue
  private final String value;
}
