package org.folio.search.utils;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Utility class for escaping and unescaping special characters in strings, such as backslashes.
 */
@UtilityClass
public class StringEscaper {

  public static final Pattern ESCAPE_PATTERN = Pattern.compile("\\\\(?!\")");
  public static final String REPLACEMENT = "\u0001";

  /**
   * Escapes backslashes in the input string by replacing them with \u0001,
   * except when the backslash is followed by a double quote.
   *
   * @param input the string to escape, may be null
   * @return the escaped string, or null if input is null
   */
  public static String escape(String input) {
    if (input == null) {
      return null;
    }
    if (input.contains(REPLACEMENT)) {
      throw new IllegalArgumentException("Input contains reserved control character \\u0001");
    }
    return ESCAPE_PATTERN.matcher(input).replaceAll(REPLACEMENT);
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
    return input.replace(REPLACEMENT, "\\");
  }
}
