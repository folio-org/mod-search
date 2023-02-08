package org.folio.search.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CommonUtils {

  /**
   * Returns "0" if given list is empty or null.
   *
   * @param input list of object
   * @return string of list size when items more than 3 - otherwise all items.
   */
  public static String listToLogParamMsg(List<?> input) {
    if (input == null || input.size() == 0) return "0";

    return input.size() < 3 ? input.toString() :
      "size of list " + input.size();
  }

  /**
   * Method is the same with a method above. Main difference is hiding big object by editing bool param.
   *
   * @param input list of object
   * @param hideObject boolean for hiding details of list
   * @return string of list size when items more than 3 - otherwise all items.
   */
  public static String listToLogParamMsg(List<?> input, boolean hideObject) {

    if (input == null || input.size() == 0) return "0";

    return hideObject ?
      "size of list " + input.size() : input.toString();
  }
}
