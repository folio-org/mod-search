package org.folio.search.service.setter.item;

import static org.folio.search.service.setter.item.ItemEffectiveShelvingOrderProcessor.getIntValue;
import static org.folio.search.service.setter.item.ItemEffectiveShelvingOrderProcessor.normalizeValue;
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
public class ItemCallNumberProcessor implements FieldProcessor<Instance, Set<Long>> {

  private static final int MAX_CHARS = 10;

  @Override
  public Set<Long> getFieldValue(Instance instance) {
    return toStreamSafe(instance.getItems())
      .map(Item::getEffectiveShelvingOrder)
      .filter(StringUtils::isNotBlank)
      .map(this::getCallNumberAsLong)
      .filter(value -> value > 0)
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
   *   <li>Each character has own unique int value ({@code ' '=0}, {@code '.'=6},
   *   {@code '/'=7}, {@code '0'=8}, {@code '9'-17}, {@code 'A'=23}, {@code 'Z'=48})</li>
   *   <li>each char numeric value multiplied by 52<sup>(10-{charPosition})</sup> (
   *   52 - is the maximum base to not exceed long max value - 2<sup>63</sup>-1)</li>
   *   <li>all received values are summed to the result value</li>
   * </ul>
   * </p>
   *
   * @param callNumber - effective shelving order value from query or from instance item to process
   * @return numeric representation of given call-number value
   */
  public Long getCallNumberAsLong(String callNumber) {
    var normalizedCallNumber = normalizeValue(callNumber);
    var cleanCallNumber = normalizedCallNumber.substring(0, Math.min(MAX_CHARS, normalizedCallNumber.length()));
    long result = 0L;
    for (int i = 0; i < cleanCallNumber.length(); i++) {
      var characterValue = getIntValue(cleanCallNumber.charAt(i), 0);
      result += characterValue * (long) Math.pow(52, (double) MAX_CHARS - i);
    }
    return result;
  }
}
