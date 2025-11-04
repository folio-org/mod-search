package org.folio.search.utils;

import lombok.experimental.UtilityClass;

/**
 * Utility class for escaping and unescaping special characters in strings, such as backslashes.
 */
@UtilityClass
public class StringEscaper {

  private static final String BACKSLASH_CHARACTER = "\\";
  private static final String BACKSLASH_ESCAPE_CHARACTER = "\u0001";

  /**
   * Escapes backslashes in the input string by replacing them with a reserved control character.
   * Backslashes followed by double quotes are only preserved when they appear inside quoted strings
   * (i.e., when they are used to escape quotes within a quoted context).
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

    StringBuilder result = new StringBuilder(input.length());
    boolean insideQuotes = false;
    int length = input.length();

    for (int i = 0; i < length; i++) {
      char current = input.charAt(i);

      if (current == '\\') {
        boolean hasNext = i + 1 < length;
        boolean nextIsQuote = hasNext && input.charAt(i + 1) == '"';

        if (nextIsQuote && insideQuotes) {
          // Keep \" as-is when inside quotes - this is an escaped quote, not a quote boundary
          result.append(current).append('"');
          i++; // Skip the next character (the quote)
        } else {
          // Replace backslash with escape character
          result.append(BACKSLASH_ESCAPE_CHARACTER);
        }
      } else {
        if (current == '"') {
          insideQuotes = !insideQuotes;
        }
        result.append(current);
      }
    }

    return result.toString();
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
