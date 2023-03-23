package org.folio.search.utils;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.CollectionUtils.addToList;
import static org.folio.search.utils.CollectionUtils.allMatch;
import static org.folio.search.utils.CollectionUtils.anyMatch;
import static org.folio.search.utils.CollectionUtils.mergeSafely;
import static org.folio.search.utils.CollectionUtils.mergeSafelyToList;
import static org.folio.search.utils.CollectionUtils.mergeSafelyToSet;
import static org.folio.search.utils.CollectionUtils.nullIfEmpty;
import static org.folio.search.utils.CollectionUtils.toLinkedHashMap;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.setOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class CollectionUtilsTest {

  private static Stream<Arguments> getValueByPathTestDataProvider() {
    var map = unstructuredMap();
    return Stream.of(
      arguments(null, emptyMap(), emptyList()),
      arguments("", emptyMap(), emptyList()),
      arguments(" ", emptyMap(), emptyList()),
      arguments("key", emptyMap(), emptyList()),
      arguments("key1.key2", emptyMap(), emptyList()),
      arguments("unknown", map, emptyList()),
      arguments("unknown1.unknown2.unknown2", map, emptyList()),
      arguments("k1", map, List.of("str")),
      arguments("k2", map, emptyList()),
      arguments("k3", map, emptyList()),
      arguments("k4", map, List.of("str1", "str2")),
      arguments("k4.k41", map, emptyList()),
      arguments("k5.k51", map, List.of("str")),
      arguments("k5.k51.k511", map, emptyList()),
      arguments("k6.k61", map, emptyList()),
      arguments("k6.k61.k611", map, List.of("str")),
      arguments("k7.k71", map, List.of("str1", "str2", "str3")),
      arguments("k8.k81", map, emptyList()),
      arguments("k8.k81.k811", map, List.of("str1", "str2", "str3")),
      arguments("k9.k91", map, List.of("str1", "str4")),
      arguments("k9.k91.k911", map, List.of("str3"))
    );
  }

  private static Map<String, Object> unstructuredMap() {
    return mapOf(
      "k1", "str",
      "k2", 123,
      "k3", false,
      "k4", List.of("str1", "str2"),
      "k5", mapOf("k51", "str"),
      "k6", mapOf("k61", mapOf("k611", "str")),
      "k7", List.of(mapOf("k71", "str1"), mapOf("k71", "str2"), mapOf("k71", "str3")),
      "k8", List.of(mapOf("k81", mapOf("k811", "str1")), mapOf("k81", mapOf("k811", "str2"))),
      "k8", List.of(
        mapOf("k81", List.of(mapOf("k811", "str1"), mapOf("k811", "str2"))),
        mapOf("k81", List.of(mapOf("k811", "str3")))),
      "k9", List.of(mapOf("k91", "str1"), List.of("str2"), mapOf("k91", mapOf("k911", "str3")), mapOf("k91", "str4"))
    );
  }

  private static Stream<Arguments> findFirstDataProvider() {
    return Stream.of(
      arguments(null, null),
      arguments(emptyList(), null),
      arguments(asList(1, 2, 3), 1),
      arguments(List.of(1), 1),
      arguments(List.of("string"), "string"),
      arguments(asList(null, null, null), null)
    );
  }

  private static Stream<Arguments> findLastDataProvider() {
    return Stream.of(
      arguments(null, null),
      arguments(emptyList(), null),
      arguments(asList(1, 2, 3), 3),
      arguments(List.of(1), 1),
      arguments(List.of("string"), "string"),
      arguments(asList(null, null, null), null)
    );
  }

  private static Stream<Arguments> subtractSortedDataProvider() {
    return Stream.of(
      arguments(emptySet(), emptySet(), emptySet()),
      arguments(asList("4", "2", "3", "1"), asList("2", "1", "10"), setOf("3", "4")),
      arguments(asList("4", "2", null, "1"), asList(null, "1", "10"), setOf("2", "4"))
    );
  }

  @Test
  void testToListSafeWithNonNullSetAndFilterReturnsExpectedList() {
    // Arrange
    Set<String> set = new HashSet<>();
    set.add("hello");
    set.add("world");
    Predicate<String> filter = s -> s.startsWith("h");

    // Act
    List<String> result = CollectionUtils.toListSafe(set, filter);

    // Assert
    assertThat(result)
      .isNotNull()
      .hasSize(1)
      .containsExactly("hello");
  }

  @Test
  void testToListSafeWithNonNullSetAndNoFilterReturnsExpectedList() {
    // Arrange
    Set<String> set = new HashSet<>();
    set.add("hello");
    set.add("world");

    // Act
    List<String> result = CollectionUtils.toListSafe(set, s -> true);

    // Assert
    assertThat(result)
      .isNotNull()
      .hasSize(2)
      .containsExactlyInAnyOrder("hello", "world");
  }

  @Test
  void testToListSafeWithNullSetReturnsNull() {
    // Arrange
    Set<String> set = null;

    // Act
    List<String> result = CollectionUtils.toListSafe(set, s -> true);

    // Assert
    assertNull(result);
  }

  @Test
  void testToListSafeWithEmptySetReturnsNull() {
    // Arrange
    Set<String> set = new HashSet<>();

    // Act
    List<String> result = CollectionUtils.toListSafe(set, s -> true);

    // Assert
    assertNull(result);
  }

  @Test
  void testToListSafeWithNonNullSetAndFilterThatRemovesAllReturnsNull() {
    // Arrange
    Set<String> set = new HashSet<>();
    set.add("hello");
    set.add("world");
    Predicate<String> filter = s -> false;

    // Act
    List<String> result = CollectionUtils.toListSafe(set, filter);

    // Assert
    assertNull(result);
  }

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

  @Test
  void toSafeStream_positive() {
    var actual = CollectionUtils.toStreamSafe(List.of(1, 2)).toList();
    assertThat(actual).containsExactly(1, 2);
  }

  @Test
  void toSafeStream_positive_nullValue() {
    var actual = CollectionUtils.toStreamSafe(null).toList();
    assertThat(actual).isEmpty();
  }

  @Test
  void toSafeStream_positive_emptyCollection() {
    var actual = CollectionUtils.toStreamSafe(emptyList()).toList();
    assertThat(actual).isEmpty();
  }

  @Test
  void mergeSafelyToSet_positive() {
    var actual = mergeSafelyToSet(List.of(1, 2, 3), List.of(3, 1, 2), List.of(5), null, emptySet());
    assertThat(actual).containsExactly(1, 2, 3, 5);
  }

  @Test
  void mergeSafelyToList_positive() {
    var actual = mergeSafelyToList(List.of(1, 2, 3), List.of(3, 1, 2), List.of(5), null, emptySet());
    assertThat(actual).containsExactly(1, 2, 3, 3, 1, 2, 5);
  }

  @Test
  void anyMatchTest_positive() {
    var given = List.of(1, 2, 3);
    assertThat(anyMatch(given, e -> e == 2)).isTrue();
  }

  @Test
  void anyMatchTest_negative() {
    var given = List.of(1, 2, 3);
    assertThat(anyMatch(given, e -> e == 5)).isFalse();
  }

  @Test
  void allMatch_positive() {
    var given = List.of(1, 2, 3);
    assertThat(allMatch(given, e -> e > 0)).isTrue();
  }

  @Test
  void allMatch_negative() {
    var given = List.of(1, -2, 3);
    assertThat(allMatch(given, e -> e > 0)).isFalse();
  }

  @DisplayName("getValueBy_parameterized")
  @MethodSource("getValueByPathTestDataProvider")
  @ParameterizedTest(name = "[{index}] path=''{0}'', expected={2}")
  void getValueByPath_positive(String path, Map<String, Object> map, List<String> expected) {
    var actual = CollectionUtils.getValuesByPath(map, path);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void toLinkedHashMap_positive() {
    var actual = Stream.of(1, 1, 2, 2).collect(toLinkedHashMap(Function.identity(), String::valueOf));
    assertThat(actual).isInstanceOf(LinkedHashMap.class).isEqualTo(mapOf(1, "1", 2, "2"));
  }

  @ParameterizedTest
  @MethodSource("findFirstDataProvider")
  void findFirst_parameterized(List<Object> list, Object expected) {
    var actual = CollectionUtils.findFirst(list);
    assertThat(actual).isEqualTo(Optional.ofNullable(expected));
  }

  @ParameterizedTest
  @MethodSource("findLastDataProvider")
  void findLast_parameterized(List<Object> list, Object expected) {
    var actual = CollectionUtils.findLast(list);
    assertThat(actual).isEqualTo(Optional.ofNullable(expected));
  }

  @ParameterizedTest
  @MethodSource("subtractSortedDataProvider")
  void subtractSorted_parameterized(Collection<String> c1, Collection<String> c2, Set<String> expected) {
    var actual = CollectionUtils.subtractSorted(c1, c2);
    assertThat(actual).isEqualTo(expected);
  }
}
