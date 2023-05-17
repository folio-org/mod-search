package org.folio.search.model.types;

import lombok.Getter;

@Getter
public enum CallNumberTypeSource {
  SYSTEM("system"),
  LOCAL("local");

  private final String source;

  CallNumberTypeSource(String source) {
    this.source = source;
  }
}
