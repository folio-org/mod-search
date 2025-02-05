package org.folio.search.utils;

import java.util.Collection;
import java.util.StringJoiner;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LogUtils {
  private static final String SIZE_OF_LIST = "size of list ";

  /**
   * Returns "0" if given list is empty or null.
   *
   * @param input      list of object
   * @param hideObject boolean for hiding details of list
   * @return string of list size when items more than 3 - otherwise all items.
   */
  public static String collectionToLogMsg(Collection<?> input, boolean hideObject) {

    if (input == null || input.isEmpty()) {
      return SIZE_OF_LIST + 0;
    }

    return hideObject
      ? SIZE_OF_LIST + input.size()
      : collectionToLogMsg(input);
  }

  /**
   * Returns "0" if given collection is empty or null.
   *
   * @param input Collection of object
   * @return string of list size when items more than 3 - otherwise all items.
   */
  public static String collectionToLogMsg(Collection<?> input) {
    if (input == null || input.isEmpty()) {
      return SIZE_OF_LIST + 0;
    }
    return input.toString();
  }

  /**
   * Collects and formats the exception messages from the given exception and its causes.
   *
   * @param ex the exception from which to collect exception messages
   * @return a formatted string containing the Class name and exception messages
   */
  public static String collectExceptionMsg(Throwable ex) {
    var messages = new StringJoiner(System.lineSeparator()).add("");

    while (ex != null) {
      messages.add(String.format("%s: %s", ex.getClass().getName(), ex.getMessage()));
      ex = ex.getCause();
    }
    return messages.toString();
  }
}
