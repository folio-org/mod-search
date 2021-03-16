package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.folio.isbn.IsbnUtil.convertTo13DigitNumber;
import static org.folio.isbn.IsbnUtil.isValid10DigitNumber;
import static org.folio.isbn.IsbnUtil.isValid13DigitNumber;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.repository.cache.InstanceIdentifierTypeCache;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Component;

/**
 * Identifier field processor, which extracts valid ISBN values from raw string.
 *
 * <p><a href="http://en.wikipedia.org/wiki/ISBN">Wikipedia - International Standard Book Number (ISBN)</a></p>
 */
@Component
public class IsbnProcessor extends AbstractIdentifierProcessor {

  private static final String SEP = "(?:[-\\s])";
  private static final String GROUP_1 = "(\\d{1,5})";
  private static final String GROUP_2 = "(\\d{1,7})";
  private static final String GROUP_3 = "(\\d{1,6})";

  /**
   * ISBN-10 consists of 4 groups of numbers separated by either dashes (-) or spaces.  The first group is 1-5
   * characters, second 1-7, third 1-6, and fourth is 1 digit or an X.
   */
  private static final Pattern ISBN10_REGEX = Pattern.compile(
    "^(?:(\\d{9}[0-9X])|(?:" + GROUP_1 + SEP + GROUP_2 + SEP + GROUP_3 + SEP + "([0-9X])))");

  /**
   * ISBN-13 consists of 5 groups of numbers separated by either dashes (-) or spaces.  The first group is 978 or 979,
   * the second group is 1-5 characters, third 1-7, fourth 1-6, and fifth is 1 digit.
   */
  private static final Pattern ISBN13_REGEX = Pattern.compile(
    "^(978|979)(?:(\\d{10})|(?:" + SEP + GROUP_1 + SEP + GROUP_2 + SEP + GROUP_3 + SEP + "([0-9])))");

  private static final List<String> IDENTIFIER_NAMES = List.of("ISBN", "Invalid ISBN");

  /**
   * Used by dependency injection.
   *
   * @param jsonConverter {@link JsonConverter} bean
   */
  public IsbnProcessor(JsonConverter jsonConverter, InstanceIdentifierTypeCache cache) {
    super(jsonConverter, cache);
  }

  @Override
  public List<String> getFieldValue(Map<String, Object> eventBody) {
    var isbnIdentifiers = new LinkedHashSet<String>();

    for (var identifier : getInstanceIdentifiers(eventBody)) {
      isbnIdentifiers.addAll(normalizeIsbn(identifier.getValue()));
    }

    return new ArrayList<>(isbnIdentifiers);
  }

  @Override
  protected List<String> getIdentifierNames() {
    return IDENTIFIER_NAMES;
  }

  /**
   * Returns normalized isbn value.
   *
   * @param value value to process as {@link String}
   * @return normalized isbn value
   */
  public List<String> normalizeIsbn(String value) {
    String isbnValue = StringUtils.trim(value).replaceAll("\\s+", " ");
    if (StringUtils.isEmpty(isbnValue)) {
      return emptyList();
    }
    var isbn13Matcher = ISBN13_REGEX.matcher(isbnValue);
    if (isbn13Matcher.find() && isValid13DigitNumber(isbn13Matcher.group(0))) {
      return getNormalizedIsbnValue(isbn13Matcher, singletonList(isbn13Matcher.group(0)));
    }

    var isbn10Matcher = ISBN10_REGEX.matcher(isbnValue);
    if (isbn10Matcher.find() && isValid10DigitNumber(isbn10Matcher.group(0))) {
      var isbn10Value = isbn10Matcher.group(0);
      return getNormalizedIsbnValue(isbn10Matcher, List.of(isbn10Value, convertTo13DigitNumber(isbn10Value)));
    }

    return List.of(replaceCharactersBetweenDigits(isbnValue));
  }

  private static List<String> getNormalizedIsbnValue(Matcher isbnRegexMatcher, List<String> isbnValues) {
    var normalizedIsbnTokens = new ArrayList<String>();
    for (String isbnValue : isbnValues) {
      normalizedIsbnTokens.add(normalizeIsbnValue(isbnValue));
    }
    var isbnQualifierValue = isbnRegexMatcher.replaceFirst("").trim();
    if (StringUtils.isNotBlank(isbnQualifierValue)) {
      normalizedIsbnTokens.add(isbnQualifierValue);
    }
    return normalizedIsbnTokens;
  }

  /**
   * Normalizes of invalid isbn value to prevent creation of tokens which can contain partial value.
   *
   * @param value value to clean up.
   * @return string value where all space and hyphen characters between digits are removed
   */
  private static String replaceCharactersBetweenDigits(String value) {
    var resultBuilder = new StringBuilder(value.length());
    resultBuilder.append(value.charAt(0));
    for (int i = 1; i < value.length() - 1; i++) {
      var current = value.charAt(i);
      if (!(isBetweenNumberCharacters(value.charAt(i - 1), value.charAt(i + 1)) && isHyphenOrSpace(current))) {
        resultBuilder.append(current);
      }
    }
    resultBuilder.append(value.charAt(value.length() - 1));
    return resultBuilder.toString();
  }

  private static boolean isHyphenOrSpace(char current) {
    return current == ' ' || current == '-';
  }

  private static boolean isBetweenNumberCharacters(char c1, char c2) {
    return CharUtils.isAsciiNumeric(c1) && CharUtils.isAsciiNumeric(c2);
  }

  private static String normalizeIsbnValue(String value) {
    return value.replace("-", "").replace(" ", "");
  }
}
