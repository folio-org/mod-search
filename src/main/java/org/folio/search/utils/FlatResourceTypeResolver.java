package org.folio.search.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FlatResourceTypeResolver {

  public static String resolve(String topic) {
    if (topic.endsWith("inventory.instance")) {
      return "instance";
    }
    if (topic.endsWith("inventory.holdings-record")) {
      return "holding";
    }
    if (topic.endsWith("inventory.item") || topic.endsWith("inventory.bound-with")) {
      return "item";
    }
    log.error("resolve:: unrecognized topic [topic: {}]", topic);
    throw new IllegalArgumentException("Unsupported flat resource topic: " + topic);
  }
}
