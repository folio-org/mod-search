package org.folio.search.model.client;

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

import org.apache.commons.lang3.StringUtils;

public record CqlQuery(String query) {

  @Override
  public String toString() {
    return query;
  }

  public static CqlQuery exactMatchAny(CqlQueryParam param, Iterable<String> values) {
    var valuesConcatenated = stream(values.spliterator(), false)
      .filter(StringUtils::isNotBlank)
      .map(value -> "\"" + value + "\"")
      .collect(joining(" or "));

    return fromTemplate("%s==(%s)", param.getCqlParam(), valuesConcatenated);
  }

  private static CqlQuery fromTemplate(String format, Object... args) {
    return new CqlQuery(String.format(format, args));
  }

}
