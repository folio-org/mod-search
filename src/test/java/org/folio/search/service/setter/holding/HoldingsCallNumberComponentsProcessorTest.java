package org.folio.search.service.setter.holding;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;

import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.Holding;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class HoldingsCallNumberComponentsProcessorTest {
  private final JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);
  private final HoldingsCallNumberComponentsProcessor processor =
    new HoldingsCallNumberComponentsProcessor(jsonConverter);

  @Test
  void canGetFieldValue_multipleHoldings() {
    var holdings = List.of(holdingWithCallNumber("prefix1", "cn1", "suffix1"),
      holdingWithCallNumber("prefix2", "cn2", "suffix2"));

    assertThat(processor.getFieldValue(Map.of("holdings", holdings)))
      .containsExactlyInAnyOrder("prefix1 cn1 suffix1", "prefix2 cn2 suffix2");
  }

  @Test
  void canGetFieldValue_someComponentsAreNulls() {
    var holdings = List.of(
      holdingWithCallNumber(null, "cn1", "suffix1"),
      holdingWithCallNumber("prefix2", "cn2", null),
      holdingWithCallNumber(null, "cn3", null));

    assertThat(processor.getFieldValue(Map.of("holdings", holdings)))
      .containsExactlyInAnyOrder("cn1 suffix1", "prefix2 cn2", "cn3");
  }

  @Test
  void shouldReturnEmptySetWhenNoHoldings() {
    assertThat(processor.getFieldValue(emptyMap())).isEmpty();
  }

  @Test
  void shouldReturnEmptySetWhenHoldingsHasNoCallNumber() {
    var holdings = List.of(new Holding(), new Holding());

    assertThat(processor.getFieldValue(Map.of("holdings", holdings))).isEmpty();
  }

  private Holding holdingWithCallNumber(String prefix, String cn, String suffix) {
    return new Holding().callNumberPrefix(prefix).callNumber(cn).callNumberSuffix(suffix);
  }
}
