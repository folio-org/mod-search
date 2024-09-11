package org.folio.search.service.reindex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RangeGenerator {

  public static List<Range> createRanges(int count) {
    if (count <= 0) {
      return List.of();
    }

    List<Range> ranges = new ArrayList<>();
    var max = upperBound();
    var partitionCount = new BigInteger(String.valueOf(count));
    var step = max.divide(partitionCount);

    var cur = lowerBound();
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

  private static BigInteger lowerBound() {
    return BigInteger.valueOf(0);
  }

  private static BigInteger upperBound() {
    return new BigInteger("ffffffffffffffffffffffffffffffff", 16);
  }

  public static Range emptyRange() {
    return new Range(fromBigint(lowerBound()), fromBigint(upperBound()));
  }

  private static UUID fromBigint(BigInteger bigint) {
    var str = String.format("%032X", bigint);
    var uuidStr = str.substring(0, 8) + "-"
      + str.substring(8, 12) + "-"
      + str.substring(12, 16) + "-"
      + str.substring(16, 20) + "-"
      + str.substring(20, 32);

    return UUID.fromString(uuidStr);
  }

  public record Range(UUID lowerBound, UUID upperBound) {}
}
