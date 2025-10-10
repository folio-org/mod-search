package org.folio.search.utils;

import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * Utility class for escaping and unescaping special characters in strings, such as backslashes.
 */
@UtilityClass
public class StringEscaper {

  private static final Map<String, String> ESCAPE_MAP = Map.of(
    "\\", "\u0001"
  );

  /**
   * Escapes backslashes in the input string by replacing them with \u0001.
   *
   * @param input the string to escape, may be null
   * @return the escaped string, or null if input is null
   */
  public static String escape(String input) {
    if (input == null) {
      return null;
    }
    if (input.contains("\u0001")) {
      throw new IllegalArgumentException("Input contains reserved control character \\u0001");
    }
    String result = input;
    for (var entry : ESCAPE_MAP.entrySet()) {
      result = result.replace(entry.getKey(), entry.getValue());
    }
    return result;
  }

  /**
   * Unescapes backslashes by replacing \u0001 back to backslashes.
   *
   * @param input the string to unescape, may be null
   * @return the unescaped string, or null if input is null
   */
  public static String unescape(String input) {
    if (input == null) {
      return null;
    }
    String result = input;
    for (var entry : ESCAPE_MAP.entrySet()) {
      result = result.replace(entry.getValue(), entry.getKey());
    }
    return result;
  }
}
