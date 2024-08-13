package org.folio.search.client;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TotalRecordsType {

  EXACT("exact"),
  ESTIMATED("estimated"),
  NONE("none"),
  AUTO("auto");

  private final String value;
}
