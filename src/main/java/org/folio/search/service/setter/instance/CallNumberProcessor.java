package org.folio.search.service.setter.instance;

import static java.lang.Long.parseLong;
import static java.lang.Math.pow;
import static org.folio.search.utils.CollectionUtils.toLinkedHashSet;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.Set;
import java.util.function.ToLongFunction;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class CallNumberProcessor implements FieldProcessor<Instance, Set<Long>> {

  private static final long DEFAULT_VALUE = 0;
  private static final int CUTTER_1_PRECISION = 3;
  private static final int CUTTER_2_PRECISION = 3;
  private static final int LETTER_ORDERS_COUNT = 2;
  private static final int CUTTER_1_OFFSET = LETTER_ORDERS_COUNT + CUTTER_1_PRECISION;
  private static final int CUTTER_2_OFFSET = LETTER_ORDERS_COUNT + CUTTER_2_PRECISION;

  private static final int CLASSIFICATION_PRECISION = 5;
  private static final int CLASSIFICATION_ORDERS_COUNT = 1 + CLASSIFICATION_PRECISION;

  private static final long FIRST_CUTTER_OFFSET = (long) pow(10, CUTTER_1_OFFSET + (double) CUTTER_2_OFFSET);
  private static final long SECOND_CUTTER_OFFSET = (long) pow(10, CUTTER_2_OFFSET);
  private static final long CLASSIFICATION_OFFSET =
    (long) pow(10, CLASSIFICATION_ORDERS_COUNT + CUTTER_1_OFFSET + (double) CUTTER_2_OFFSET);

  @Override
  public Set<Long> getFieldValue(Instance instance) {
    return toStreamSafe(instance.getItems())
      .map(Item::getEffectiveShelvingOrder)
      .filter(StringUtils::isNotBlank)
      .map(this::getCallNumberAsLong)
      .filter(value -> value != 0L)
      .collect(toLinkedHashSet());
  }

  /**
   * Converts passed effective shelving order as long value.
   *
   * <p><b>Conversion algorithm:</b>
   * <p>Number format is 000_000000_00000_0000 as {@code double}</p>
   *   <ul>
   *     <li>three first numbers are responsible for general letters: {@code A=1, AA=2, Z=626, ZZ=702}</li>
   *     <li>next six numbers are responsible for classification value:
   *     {@code 11=110000, 11.1=110001, 512345.6=512345}. Values longer that 6 characters will be truncated</li>
   *     <li>next five numbers are responsible for first cutter, first 2 number of it responsible for letter,
   *     three for the numeric part, {@code A111=01111, Z999=26999}, all numeric values longer than 3 will
   *     be truncated</li>
   *     <li>the last four numbers are responsible for the second cutter. It's processed with the same
   *     approach as the first cutter, but precision is lower. If it's missing - it will be populated
   *     with four zeroes: {@code 0000}</li>
   *   </ul>
   * </p>
   *
   * @param value - effective shelving order as {@link String} object
   * @return effective shelving value as {@link Double} value
   */
  public long getCallNumberAsLong(String value) {
    var tokens = value.split("\\s+");
    return getTokenAsNumber(tokens, 0, t -> getGeneralClassAsNumber(value, t) * CLASSIFICATION_OFFSET)
      + getTokenAsNumber(tokens, 1, t -> getClassificationNumberAsNumber(value, t) * FIRST_CUTTER_OFFSET)
      + getTokenAsNumber(tokens, 2, t -> getCutterNumberAsNumber(value, t, CUTTER_1_PRECISION) * SECOND_CUTTER_OFFSET)
      + getTokenAsNumber(tokens, 3, t -> getCutterNumberAsNumber(value, t, CUTTER_2_PRECISION));
  }

  private static long getGeneralClassAsNumber(String shelvingOrder, String general) {
    if (!general.matches("[A-Z]{1,2}")) {
      return logAndReturnDefaultValue(shelvingOrder, "general class");
    }
    var firstCharValue = getCharAsNumber(general.charAt(0));
    var charValue = firstCharValue * 27 - 26;
    return general.length() == 2 ? charValue + getCharAsNumber(general.charAt(1)) : charValue;
  }

  private static long getClassificationNumberAsNumber(String shelvingOrder, String classificationNumber) {
    if (!classificationNumber.matches("\\d+(\\.\\d+)?")) {
      return logAndReturnDefaultValue(shelvingOrder, "classification value");
    }

    var tokens = classificationNumber.split("\\.");
    var classification = tokens[0];
    var result = getStringValueAsNumber(shelvingOrder, classification, CLASSIFICATION_PRECISION) * 10;

    if (classification.length() > CLASSIFICATION_PRECISION) {
      result += parseLong(classification.substring(CLASSIFICATION_PRECISION, CLASSIFICATION_PRECISION + 1));
    }

    if (tokens.length == 2 && classification.length() <= CLASSIFICATION_PRECISION) {
      result += parseLong(tokens[1].substring(0, 1));
    }

    return result;
  }

  private static long getCutterNumberAsNumber(String shelvingOrder, String cutter, int numPrecision) {
    if (!cutter.matches("[A-Z]\\d+")) {
      return logAndReturnDefaultValue(shelvingOrder, "cutter");
    }
    return getCharAsNumber(cutter.charAt(0)) * (long) pow(10, numPrecision)
      + getStringValueAsNumber(shelvingOrder, cutter.substring(1), numPrecision);
  }

  private static long getTokenAsNumber(String[] tokens, int index, ToLongFunction<String> func) {
    return tokens.length > index ? func.applyAsLong(tokens[index]) : DEFAULT_VALUE;
  }

  private static long getStringValueAsNumber(String order, String value, int limit) {
    var stringLength = value.length();
    var lengthDiff = limit - stringLength;
    if (lengthDiff < 0) {
      log.debug("Passed string is exceeded maximum limit, first {} values will be used [value: '{}']", limit, order);
      return parseLong(value.substring(0, limit));
    }

    return parseLong(value) * (long) pow(10, lengthDiff);
  }

  private static long logAndReturnDefaultValue(String shelvingOrder, String type) {
    log.debug("Invalid {} in effectiveShelvingOrder: '{}'", type, shelvingOrder);
    return DEFAULT_VALUE;
  }

  private static long getCharAsNumber(char ch) {
    return (long) ch - 64;
  }
}
