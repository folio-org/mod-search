package org.folio.search.utils;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.CollectionUtils.hasNoValue;
import static org.folio.search.utils.CollectionUtils.mergeSafely;
import static org.folio.search.utils.CollectionUtils.nullIfEmpty;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class CollectionUtilsTest {

  @Test
  void shouldReturnNullIfEmptyMap() {
    assertThat(nullIfEmpty(emptyMap())).isNull();
  }

  @Test
  void shouldReturnNullIfMapIsNull() {
    assertThat(nullIfEmpty(null)).isNull();
  }

  @Test
  void shouldReturnMapIfNotEmpty() {
    Map<String, String> map = Map.of("key", "value");

    assertThat(nullIfEmpty(map)).isEqualTo(map);
  }

  @Test
  void shouldMergeTwoMaps() {
    Map<String, String> map1 = Map.of("key", "value");
    Map<String, String> map2 = Map.of("key2", "value2");

    assertThat(mergeSafely(map1, map2))
      .containsEntry("key", "value")
      .containsEntry("key2", "value2");
  }

  @Test
  void shouldReturnFirstMapIfSecondIsNull() {
    Map<String, String> map1 = Map.of("key", "value");

    assertThat(mergeSafely(map1, null))
      .containsEntry("key", "value");
  }

  @Test
  void shouldReturnNullIfAllMapsAreNull() {
    assertThat(mergeSafely(null, null)).isNull();
  }

  @Test
  void hasNoValue_keyIsNull() {
    var map = new HashMap<>();
    map.put("key", null);

    assertTrue(hasNoValue(map, "key"));
  }

  @Test
  void hasNoValue_noKeyPresent() {
    assertTrue(hasNoValue(emptyMap(), "key"));
  }
}
