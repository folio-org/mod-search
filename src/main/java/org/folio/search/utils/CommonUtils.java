package org.folio.search.utils;

import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CommonUtils {
  private static final String SIZE_OF_LIST = "size of list ";

  /**
   * Returns "0" if given list is empty or null.
   *
   * @param input list of object
   * @return string of list size when items more than 3 - otherwise all items.
   */
  public static String listToLogMsg(List<?> input) {
    if (input == null || input.size() == 0) {
      return SIZE_OF_LIST + 0;
    }

    return input.size() < 3
      ? input.toString()
      : SIZE_OF_LIST + input.size();
  }

  /**
   * Method is the same with a method above. Main difference is hiding big object by editing bool param.
   *
   * @param input      list of object
   * @param hideObject boolean for hiding details of list
   * @return string of list size when items more than 3 - otherwise all items.
   */
  public static String listToLogMsg(List<?> input, boolean hideObject) {

    if (input == null || input.size() == 0) {
      return SIZE_OF_LIST + 0;
    }

    return hideObject
      ? SIZE_OF_LIST + input.size()
      : input.toString();
  }

  /**
   * Returns "0" if given collection is empty or null.
   *
   * @param input Collection of object
   * @return string of list size when items more than 3 - otherwise all items.
   */
  public static String collectionToLogMsg(Collection<?> input) {
    if (input == null || input.size() == 0) {
      return SIZE_OF_LIST + 0;
    }
    return input.size() < 3
      ? input.toString()
      : SIZE_OF_LIST + input.size();
  }
}
