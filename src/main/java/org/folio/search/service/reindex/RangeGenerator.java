package org.folio.search.service.reindex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RangeGenerator {

  public static List<Range> createRanges(int count) {
    List<Range> ranges = new ArrayList<>();
    var max = new BigInteger("ffffffffffffffffffffffffffffffff", 16);
    var partitionCount = new BigInteger(String.valueOf(count));
    var step = max.divide(partitionCount);

    var cur = BigInteger.valueOf(0);
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
