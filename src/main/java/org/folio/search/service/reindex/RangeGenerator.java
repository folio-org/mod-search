package org.folio.search.service.reindex;

import static java.lang.String.format;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class RangeGenerator {

  public static List<Range> createUuidRanges(int count) {
    if (count <= 0) {
      return List.of();
    }

    List<Range> ranges = new ArrayList<>();
    var max = upperUuidBound();
    var partitionCount = new BigInteger(String.valueOf(count));
    var step = max.divide(partitionCount);

    var cur = lowerUuidBound();
    var prev = cur;
    for (int i = 0; i < partitionCount.intValue(); i++) {
      cur = cur.add(step);
      ranges.add(new Range(
        fromBigint(prev),
        fromBigint(cur)
      ));
      prev = cur;
    }

    return ranges;
  }

  /**
   * Generates a list of range objects representing sequences from "00...0" to "ff...f" for each possible range.
   *
   * @param length The number of hex characters in each string
   * @return A list of range objects.
   */
  public static List<Range> createHexRanges(int length) {
    if (length < 1) {
      throw new IllegalArgumentException("Length must be at least 1.");
    }

    // Calculate the number of elements (2^length*4 since each hex digit represents 4 bits)
    int totalElements = (int) Math.pow(2, length * 4.0) - 1;

    List<Range> ranges = new ArrayList<>();
    String formatString = "%0" + length + "x"; // dynamic format for leading zeros

    // Loop from 0 to the maximum possible value with 'length' digits in hexadecimal
    for (int i = 0; i < totalElements; i++) {
      String lowerBound = format(formatString, i);
      String upperBound = format(formatString, i + 1);
      ranges.add(new Range(lowerBound, upperBound));
    }

    String lowerBound = format(formatString, totalElements);
    String upperBound = StringUtils.repeat('x', length);
    ranges.add(new Range(lowerBound, upperBound));

    return ranges;
  }

  public static Range emptyUuidRange() {
    return new Range(fromBigint(lowerUuidBound()), fromBigint(upperUuidBound()));
  }

  private static BigInteger lowerUuidBound() {
    return BigInteger.valueOf(0);
  }

  private static BigInteger upperUuidBound() {
    return new BigInteger("ffffffffffffffffffffffffffffffff", 16);
  }

  private static String fromBigint(BigInteger bigint) {
    return format("%032X", bigint);
  }

  public record Range(String lowerBound, String upperBound) { }
}
