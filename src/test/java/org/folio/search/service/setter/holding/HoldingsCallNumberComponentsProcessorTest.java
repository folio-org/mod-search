package org.folio.search.service.setter.holding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class HoldingsCallNumberComponentsProcessorTest {
  private final HoldingsCallNumberComponentsProcessor processor = new HoldingsCallNumberComponentsProcessor();

  @Test
  void canGetFieldValue_multipleHoldings() {
    var holdings = List.of(holdingWithCallNumber("prefix1", "cn1", "suffix1"),
      holdingWithCallNumber("prefix2", "cn2", "suffix2"));

    assertThat(processor.getFieldValue(new Instance().holdings(holdings)))
      .containsExactlyInAnyOrder("prefix1 cn1 suffix1", "prefix2 cn2 suffix2");
  }

  @Test
  void canGetFieldValue_someComponentsAreNulls() {
    var holdings = List.of(
      holdingWithCallNumber(null, "cn1", "suffix1"),
      holdingWithCallNumber("prefix2", "cn2", null),
      holdingWithCallNumber(null, "cn3", null));

    assertThat(processor.getFieldValue(new Instance().holdings(holdings)))
      .containsExactlyInAnyOrder("cn1 suffix1", "prefix2 cn2", "cn3");
  }

  @Test
  void shouldReturnEmptySetWhenNoHoldings() {
    assertThat(processor.getFieldValue(new Instance())).isEmpty();
  }

  @Test
  void shouldReturnEmptySetWhenHoldingsHasNoCallNumber() {
    var holdings = List.of(new Holding(), new Holding());
    assertThat(processor.getFieldValue(new Instance().holdings(holdings))).isEmpty();
  }

  private Holding holdingWithCallNumber(String prefix, String cn, String suffix) {
    return new Holding().callNumberPrefix(prefix).callNumber(cn).callNumberSuffix(suffix);
  }
}
