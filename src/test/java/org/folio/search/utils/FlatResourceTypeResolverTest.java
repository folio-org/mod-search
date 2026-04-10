package org.folio.search.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class FlatResourceTypeResolverTest {

  @Test
  void resolve_returnsExpectedTypeForSupportedTopics() {
    assertThat(FlatResourceTypeResolver.resolve("folio.diku.inventory.instance")).isEqualTo("instance");
    assertThat(FlatResourceTypeResolver.resolve("folio.diku.inventory.holdings-record")).isEqualTo("holding");
    assertThat(FlatResourceTypeResolver.resolve("folio.diku.inventory.item")).isEqualTo("item");
    assertThat(FlatResourceTypeResolver.resolve("folio.diku.inventory.bound-with")).isEqualTo("item");
  }

  @Test
  void resolve_rejectsUnsupportedTopics() {
    assertThatThrownBy(() -> FlatResourceTypeResolver.resolve("folio.diku.inventory.location"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Unsupported flat resource topic");
  }
}
