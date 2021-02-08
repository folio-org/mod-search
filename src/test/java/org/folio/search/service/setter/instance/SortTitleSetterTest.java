package org.folio.search.service.setter.instance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class SortTitleSetterTest {
  private final SortTitleSetter contributorsSetter = new SortTitleSetter();

  @Test
  void shouldReturnTitle() {
    assertThat(contributorsSetter.getFieldValue(Map.of("title", "the title")))
      .isEqualTo("the title");
  }

  @Test
  void shouldReturnNullIfMapIsNull() {
    assertThat(contributorsSetter.getFieldValue(null)).isNull();
  }

  @Test
  void shouldReturnNullIfNoTitleProperty() {
    assertThat(contributorsSetter.getFieldValue(Map.of("alternativeTitle", "another title")))
      .isNull();
  }
}
