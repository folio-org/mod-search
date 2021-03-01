package org.folio.search.service.setter.instance;

import static org.folio.isbn.IsbnUtil.convertTo13DigitNumber;
import static org.folio.isbn.IsbnUtil.isValid10DigitNumber;
import static org.folio.isbn.IsbnUtil.isValid13DigitNumber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Component;

@Component
public class IsbnProcessor extends AbstractIdentifierProcessor {

  static final String ISBN_IDENTIFIER_TYPE_ID = "8261054f-be78-422d-bd51-4ed9f33c3422";
  static final String INVALID_ISBN_IDENTIFIER_TYPE_ID = "fcca2643-406a-482a-b760-7a7f8aec640e";

  private static final String SEP = "(?:-|\\s)";
  private static final String GROUP = "(\\d{1,5})";
  private static final String PUBLISHER = "(\\d{1,7})";
  private static final String TITLE = "(\\d{1,6})";

  private static final Pattern ISBN10_REGEX = Pattern.compile(
    "(?:(\\d{9}[0-9X])|(?:" + GROUP + SEP + PUBLISHER + SEP + TITLE + SEP + "([0-9X])))");
  private static final Pattern ISBN13_REGEX = Pattern.compile(
    "(978|979)(?:(\\d{10})|(?:" + SEP + GROUP + SEP + PUBLISHER + SEP + TITLE + SEP + "([0-9])))");

  private final Set<String> isbnIdentifierTypeIds =
    Set.of(ISBN_IDENTIFIER_TYPE_ID, INVALID_ISBN_IDENTIFIER_TYPE_ID);

  /**
   * Used by dependency injection.
   *
   * @param jsonConverter {@link JsonConverter} bean
   */
  public IsbnProcessor(JsonConverter jsonConverter) {
    super(jsonConverter);
  }

  @Override
  public List<String> getFieldValue(Map<String, Object> eventBody) {
    var isbnIdentifiers = new LinkedHashSet<String>();

    for (var identifier : getInstanceIdentifiers(eventBody)) {
      var normalizedValue = normalizeIsbn(identifier.getValue(), IsbnProcessor::tryToNormalizeIsbn);
      isbnIdentifiers.addAll(normalizedValue);
    }

    return new ArrayList<>(isbnIdentifiers);
  }

  @Override
  protected Set<String> getIdentifierTypeIds() {
    return isbnIdentifierTypeIds;
  }

  private static List<String> normalizeIsbn(String value, Function<String, List<String>> fallbackFunction) {
    var isbnValue = value.trim();
    if (StringUtils.isEmpty(isbnValue)) {
      return Collections.emptyList();
    }
    if (isValid13DigitNumber(isbnValue)) {
      return List.of(normalizeIsbnValue(isbnValue));
    }
    if (isValid10DigitNumber(isbnValue)) {
      return List.of(normalizeIsbnValue(isbnValue), normalizeIsbnValue(convertTo13DigitNumber(isbnValue)));
    }
    return fallbackFunction.apply(isbnValue);
  }

  private static List<String> tryToNormalizeIsbn(String value) {
    var isbn13Matcher = ISBN13_REGEX.matcher(value);
    String isbnValue;
    if (isbn13Matcher.find() && isValid13DigitNumber(isbnValue = isbn13Matcher.group(0))) {
      return getNormalizedIsbnValue(isbn13Matcher, isbnValue);
    }

    var isbn10Matcher = ISBN10_REGEX.matcher(value);
    if (isbn10Matcher.find() && isValid10DigitNumber(isbnValue = isbn10Matcher.group(0))) {
      return getNormalizedIsbnValue(isbn10Matcher, isbnValue, convertTo13DigitNumber(isbnValue));
    }

    return List.of(replaceCharactersBetweenDigits(value));
  }

  private static List<String> getNormalizedIsbnValue(Matcher isbnRegexMatcher, String... isbnValues) {
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
