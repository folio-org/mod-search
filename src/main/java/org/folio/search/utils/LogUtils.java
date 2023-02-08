package org.folio.search.utils;

import java.util.Collection;
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
    return input.size() < 3
      ? input.toString()
      : SIZE_OF_LIST + input.size();
  }
}
