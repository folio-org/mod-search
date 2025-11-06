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

    var result = new StringBuilder(input.length());
    boolean insideQuotes = false;
    int length = input.length();
    int i = 0;

    while (i < length) {
      char current = input.charAt(i);

      if (current == '\\') {
        // Count consecutive backslashes and skip their processing by outer loop
        int backslashCount = 0;
        while (i < length && input.charAt(i) == '\\') {
          backslashCount++;
          i++;
        }

        // Check if backslash sequence is followed by a quote
        boolean followedByQuote = i < length && input.charAt(i) == '"';

        if (followedByQuote && insideQuotes) {
          // If odd number of backslashes: last one escapes the quote, preserve \"
          // If even number: all pair up (\\), don't preserve the quote
          if (backslashCount % 2 == 1) {
            // Odd: replace all but the last backslash, keep last one + quote
            result.append(BACKSLASH_ESCAPE_CHARACTER.repeat(backslashCount - 1));
            result.append('\\').append('"');
            i++; // Skip past the quote
          } else {
            // Even: replace all backslashes (quote is not consumed, will be processed next iteration)
            result.append(BACKSLASH_ESCAPE_CHARACTER.repeat(backslashCount));
          }
        } else {
          // Not followed by quote inside quotes: replace all backslashes
          result.append(BACKSLASH_ESCAPE_CHARACTER.repeat(backslashCount));
        }
      } else {
        if (current == '"') {
          insideQuotes = !insideQuotes;
        }
        result.append(current);
        i++;
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
