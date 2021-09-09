package org.folio.search.service.setter.holding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class HoldingsNormalizedCallNumbersProcessorTest {
  private final HoldingsNormalizedCallNumbersProcessor processor = new HoldingsNormalizedCallNumbersProcessor();

  @Test
  void canGetFieldValue_multipleHoldings() {
    var holdings = List.of(holdingWithCallNumber("Rare Books", "S537.N56 C82", "++"),
      holdingWithCallNumber("Oversize", "ABC123.1 .R15 2018", "Curriculum Materials Collection"));

    assertThat(processor.getFieldValue(new Instance().holdings(holdings)))
      .containsExactlyInAnyOrder("rarebookss537n56c82", "s537n56c82",
        "oversizeabc1231r152018curriculummaterialscollection", "abc1231r152018curriculummaterialscollection");
  }

  @Test
  void canGetFieldValue_someComponentsAreNulls() {
    var holdings = List.of(
      holdingWithCallNumber(null, "cn1", "suffix1"),
      holdingWithCallNumber("prefix2", "cn2", null),
      holdingWithCallNumber(null, "cn3", null));

    assertThat(processor.getFieldValue(new Instance().holdings(holdings)))
      .containsExactlyInAnyOrder("cn1suffix1", "prefix2cn2", "cn2", "cn3");
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
