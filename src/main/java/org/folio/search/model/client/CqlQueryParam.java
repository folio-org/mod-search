package org.folio.search.model.client;

import java.util.Locale;
import lombok.Getter;

@Getter
public enum CqlQueryParam {

  ID("id"),
  NAME("name"),
  SOURCE("source"),
  HOLDINGS_ID("holdings.id");

  /**
   * Request URI for feign client.
   */
  private final String cqlParam;

  CqlQueryParam(String cqlParam) {
    this.cqlParam = cqlParam;
  }

  @Override
  public String toString() {
    return name().toLowerCase(Locale.ROOT);
  }
}
