package org.folio.search.utils;

import static java.util.stream.Collectors.joining;

import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class CallNumberUtils {

  private static final Pattern NORMALIZE_REGEX = Pattern.compile("[^a-z0-9]");

  public static String calculateFullCallNumber(String callNumber, String suffix) {
    if (StringUtils.isBlank(callNumber)) {
      throw new IllegalArgumentException("Call number is required to calculate full call number.");
    }
    return Stream.of(callNumber, suffix)
      .filter(StringUtils::isNotBlank)
      .map(StringUtils::trim)
      .collect(joining(" "));
  }

  /**
   * Creates normalized call number for passed call number parts (prefix, call number and suffix).
   *
   * @param callNumberValues array with full call number parts (prefix, call number and suffix)
   * @return created normalized call number as {@link String} value
   */
  public static String normalizeCallNumberComponents(String... callNumberValues) {
    return Stream.of(callNumberValues)
      .map(s -> RegExUtils.removeAll(StringUtils.lowerCase(s), NORMALIZE_REGEX))
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.joining(""));
  }

  /**
   * Creates call number for passed prefix, call number and suffix.
   *
   * @param prefix     call number prefix
   * @param callNumber call number value
   * @param suffix     call number suffix
   * @return created effective call number as {@link String} value
   */
  public static String getEffectiveCallNumber(String prefix, String callNumber, String suffix) {
    return Stream.of(prefix, callNumber, suffix)
      .map(StringUtils::trim)
      .filter(StringUtils::isNotBlank)
      .collect(joining(" "));
  }

}
