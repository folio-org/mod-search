package org.folio.search;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

public class SampleTest {

  @Test
  void name() {
    var str = "ABCD";
    var collect = IntStream.range(0, 50)
      .mapToObj(e -> str.charAt(ThreadLocalRandom.current().nextInt(str.length())))
      .map(character -> List.of(character.toString()))
      .collect(Collectors.toList());
    System.out.println(collect);
  }
}
