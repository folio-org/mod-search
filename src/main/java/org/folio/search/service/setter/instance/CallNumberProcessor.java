package org.folio.search.service.setter.instance;

import static java.util.Locale.ROOT;
import static org.folio.search.utils.CollectionUtils.toLinkedHashSet;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class CallNumberProcessor implements FieldProcessor<Instance, Set<Long>> {

  public static final int MAX_CHARS = 10;
  public static final int ASCII_A = 'A';
  public static final int ASCII_1 = '1';
  public static final int ASCII_9 = '9';
  public static final int ASCII_SPACE = ' ';
  public static final int ASCII_DOT = '.';
  public static final int ASCII_SLASH = '/';

  @Override
  public Set<Long> getFieldValue(Instance instance) {
    return toStreamSafe(instance.getItems())
      .map(Item::getEffectiveShelvingOrder)
      .filter(StringUtils::isNotBlank)
      .map(this::getCallNumberAsLong)
      .sorted()
      .collect(toLinkedHashSet());
  }

  /**
   * Converts incoming call-number into long value.
   *
   * <p>
   * This algorithm takes first 10 character from call-number and converts them into long value using following
   * approach:
   * <ul>
   *   <li>Each character has own unique int value ({@code ' '=1}, {@code '.'=2},
   *   {@code '/'=3}, {@code '0'=4}, {@code '9'-13}, {@code 'A'=14}, {@code 'Z'=39})</li>
   *   <li>each char numeric value multiplied by {@code 39^(10-characterPosition}</li>
   *   <li>all received values are summed to the result value</li>
   * </ul>
   * </p>
   */
  public Long getCallNumberAsLong(String callNumber) {
    var cleanCallNumber = callNumber.toUpperCase(ROOT).replaceAll("[^A-Z0-9. /]", " ").replaceAll("\\s+", " ");
    cleanCallNumber = cleanCallNumber.substring(0, Math.min(MAX_CHARS, cleanCallNumber.length()));
    var result = 0L;
    for (int i = 0; i < cleanCallNumber.length(); i++) {
      result += getCharValue(cleanCallNumber.charAt(i)) * ((long) Math.pow(39, MAX_CHARS - i));
    }
    return result;
  }

  private static long getCharValue(char c) {
    switch (c) {
      case ASCII_SPACE:
        return 1;
      case ASCII_DOT:
        return 2;
      case ASCII_SLASH:
        return 3;
      default:
        var i = c <= ASCII_9 ? c - ASCII_1 + 5 : c - ASCII_A + 14;
        System.out.println(i);
        return i;
    }
  }
}
