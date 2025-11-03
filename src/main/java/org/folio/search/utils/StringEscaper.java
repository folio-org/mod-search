package org.folio.search.utils;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Utility class for escaping and unescaping special characters in strings, such as backslashes.
 */
@UtilityClass
public class StringEscaper {

  private static final String BACKSLASH_CHARACTER = "\\";
  private static final String BACKSLASH_ESCAPE_CHARACTER = "\u0001";
  private static final Pattern BACKSLASH_PATTERN = Pattern.compile("\\\\");

  /**
   * Escapes backslashes in the input string by replacing them with a reserved control character.
   *
   * @param input the string to escape, may be null
   * @return the escaped string, or null if input is null
   */
  public static String escape(String input) {
    if (input == null) {
      return null;
    }
    if (input.contains(BACKSLASH_ESCAPE_CHARACTER)) {
      throw new IllegalArgumentException("Input contains reserved control character \\u0001");
    }
    return BACKSLASH_PATTERN.matcher(input).replaceAll(BACKSLASH_ESCAPE_CHARACTER);
  }

  /**
   * Unescapes backslashes by replacing the reserved control character back to backslashes.
   *
   * @param input the string to unescape, may be null
   * @return the unescaped string, or null if input is null
   */
  public static String unescape(String input) {
    if (input == null) {
      return null;
    }
    return input.replace(BACKSLASH_ESCAPE_CHARACTER, BACKSLASH_CHARACTER);
  }
}
