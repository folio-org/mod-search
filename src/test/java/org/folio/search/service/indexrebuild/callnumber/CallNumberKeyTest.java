package org.folio.search.service.indexrebuild.callnumber;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.folio.search.utils.ShaUtils;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class CallNumberKeyTest {

  @Test
  void of_positive_idIsCompatibleWithCallNumberTables() {
    assertThat(CallNumberKey.of("  NS 1 .B5 ", "pre", "suf", "type")).hasValueSatisfying(key -> {
      assertThat(key.callNumber()).isEqualTo("NS 1 .B5");
      assertThat(key.id()).isEqualTo(ShaUtils.sha("NS 1 .B5", "pre", "suf", "type"));
    });
  }

  @Test
  void of_positive_valuesAreTruncated() {
    assertThat(CallNumberKey.of("c".repeat(60), "p".repeat(30), "s".repeat(30), "t".repeat(50)))
      .hasValueSatisfying(key -> {
        assertThat(key.callNumber()).hasSize(50);
        assertThat(key.prefix()).hasSize(20);
        assertThat(key.suffix()).hasSize(25);
        assertThat(key.typeId()).hasSize(40);
      });
  }

  @Test
  void of_negative_blankCallNumber() {
    assertThat(CallNumberKey.of("  ", "pre", "suf", "type")).isEmpty();
    assertThat(CallNumberKey.of(null, "pre", "suf", "type")).isEmpty();
  }

  @Test
  void fromItem_positive_readsEffectiveComponents() {
    var item = Map.of("effectiveCallNumberComponents", Map.of("callNumber", "A1", "typeId", "t"));

    assertThat(CallNumberKey.fromItem(item)).hasValueSatisfying(
      key -> assertThat(key.id()).isEqualTo(ShaUtils.sha("A1", null, null, "t")));
  }

  @Test
  void fromItem_negative_noComponents() {
    assertThat(CallNumberKey.fromItem(Map.of("id", "item"))).isEmpty();
    assertThat(CallNumberKey.fromItem(null)).isEmpty();
  }
}
