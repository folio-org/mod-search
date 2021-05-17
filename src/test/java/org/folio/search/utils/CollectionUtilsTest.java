package org.folio.search.utils;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.CollectionUtils.addToList;
import static org.folio.search.utils.CollectionUtils.mergeSafely;
import static org.folio.search.utils.CollectionUtils.nullIfEmpty;

import java.util.ArrayList;
import java.util.List;
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
  void addToList_positive_addToTheEnd() {
    var initial = new ArrayList<>(List.of(1, 2));
    addToList(initial, List.of(3, 4), false);
    assertThat(initial).containsExactly(1, 2, 3, 4);
  }

  @Test
  void addToList_positive_addToTheTop() {
    var initial = new ArrayList<>(List.of(1, 2));
    addToList(initial, List.of(3, 4), true);
    assertThat(initial).containsExactly(3, 4, 1, 2);
  }

  @Test
  void addToList_positive_initialListIsNull() {
    List<Integer> initial = null;
    addToList(initial, List.of(3, 4), true);
    assertThat(initial).isNull();
  }

  @Test
  void addToList_positive_sourceListIsNull() {
    List<Integer> initial = new ArrayList<>(List.of(3, 5));
    addToList(initial, null, true);
    assertThat(initial).contains(3, 5);
  }
}
