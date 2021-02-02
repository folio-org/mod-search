package org.folio.search.model.types;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorType {

  INTERNAL("-1"),
  UNKNOWN("-4");

  @JsonValue
  private final String value;
}
