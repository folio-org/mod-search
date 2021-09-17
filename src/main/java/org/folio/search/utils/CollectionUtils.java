package org.folio.search.utils;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Stream.empty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections.MapUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CollectionUtils {

  /**
   * Returns null if given map is empty or null.
   *
   * @param map given map to check as {@link Map} object
   * @param <K> generic type for map key
   * @param <V> generic type for map value
   * @return map itself if it's not empty, null - otherwise
   */
  public static <K, V> Map<K, V> nullIfEmpty(Map<K, V> map) {
    return MapUtils.isNotEmpty(map) ? map : null;
  }

  /**
   * Merges given array of maps into single {@link LinkedHashMap} object.
   *
   * @param maps array of maps to merge into single one.
   * @param <K> generic type for map key
   * @param <V> generic type for map value
   * @return merged maps into one {@link LinkedHashMap} object, null - if result is empty.
   */
  @SafeVarargs
  public static <K, V> Map<K, V> mergeSafely(Map<K, V>... maps) {
    Map<K, V> baseMap = new LinkedHashMap<>();

    for (var map : maps) {
      if (MapUtils.isNotEmpty(map)) {
        baseMap.putAll(map);
      }
    }

    return nullIfEmpty(baseMap);
  }

  /**
   * Merges given array of collections into single {@link LinkedHashSet} object.
   *
   * @param collections array of maps to merge into single one.
   * @param <T> generic type for collection value
   * @return merged maps into one {@link LinkedHashSet} object.
   */
  @SafeVarargs
  public static <T> Collection<T> mergeSafelyToSet(Collection<T>... collections) {
    var set = new LinkedHashSet<T>();
    for (Collection<T> collection : collections) {
      if (isNotEmpty(collection)) {
        set.addAll(collection);
      }
    }
    return set;
  }

  /**
   * Adds all values from the passed list to the initial. Does nothing if initial or given list is null.
   *
   * @param initial initial list, where values should be added
   * @param sourceValues list with source values
   * @param addToTop boolean property, which specifies if values should be added to the beginning of the list or not
   * @param <T> generic type for list values
   */
  public static <T> void addToList(List<T> initial, List<T> sourceValues, boolean addToTop) {
    if (initial == null || sourceValues == null) {
      return;
    }
    var startIndex = addToTop ? 0 : initial.size();
    initial.addAll(startIndex, sourceValues);
  }

  /**
   * Returns a Collector that accumulates the input elements into a new {@link LinkedHashSet}, in encounter order.
   *
   * @param <T> the type of input elements
   * @return a {@link Collector} which collects all the input elements into a {@link LinkedHashSet}, in encounter order
   */
  public static <T> Collector<T, ?, LinkedHashSet<T>> toLinkedHashSet() {
    return toCollection(LinkedHashSet::new);
  }

  /**
   * Returns nullableList if it is not null or empty, defaultList otherwise.
   *
   * @param nullableList nullable value to check
   * @param <T> generic type for value
   * @return nullableList if it is not null or empty, defaultList otherwise.
   */
  public static <T> Stream<T> toStreamSafe(List<T> nullableList) {
    return (nullableList != null && !nullableList.isEmpty()) ? nullableList.stream() : empty();
  }

  /**
   * Verifies that some element in iterable satisfies given condition.
   *
   * @param collection - collection to check
   * @param checker - predicate for iterable elements
   * @param <T> generic type for collection element
   * @return true if any element matched
   */
  public static <T> boolean anyMatch(Iterable<T> collection, Predicate<T> checker) {
    for (T value : collection) {
      if (checker.test(value)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Verifies that all elements in iterable do not satisfy given condition.
   *
   * @param collection - collection to check
   * @param checker - predicate for iterable elements
   * @param <T> generic type for collection element
   * @return true if all elements do not match given condition
   */
  public static <T> boolean noneMatch(Iterable<T> collection, Predicate<T> checker) {
    return !anyMatch(collection, checker);
  }
}
