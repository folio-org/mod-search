package org.folio.search.client.cql;

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
@EqualsAndHashCode
public final class CqlQuery {
  private final String query;

  private static CqlQuery fromTemplate(String format, Object ... args) {
    return new CqlQuery(String.format(format, args));
  }

  public static CqlQuery exactMatchAny(String index, Iterable<String> values) {
    var valuesConcatenated = stream(values.spliterator(), false)
      .filter(StringUtils::isNotBlank)
      .map(value -> "\"" + value + "\"")
      .collect(joining(" or "));

    return fromTemplate("%s==(%s)", index, valuesConcatenated);
  }

  @Override
  public String toString() {
    return query;
  }
}
