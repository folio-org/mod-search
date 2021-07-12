package org.folio.search.utils;

import static java.util.stream.Stream.empty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.MapUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CollectionUtils {

  public static <K, V> Map<K, V> nullIfEmpty(Map<K, V> map) {
    return MapUtils.isEmpty(map) ? null : map;
  }

  @SafeVarargs
  public static <K, V> Map<K, V> mergeSafely(Map<K, V>... maps) {
    Map<K, V> baseMap = new LinkedHashMap<>();

    for (Map<K, V> map : maps) {
      if (map != null && !map.isEmpty()) {
        baseMap.putAll(map);
      }
    }

    return nullIfEmpty(baseMap);
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
   * Returns nullableList if it is not null or empty, defaultList otherwise.
   *
   * @param nullableList nullable value to check
   * @param <T> generic type for value
   * @return nullableList if it is not null or empty, defaultList otherwise.
   */
  public static <T> Stream<T> toStreamSafe(List<T> nullableList) {
    return (nullableList != null && !nullableList.isEmpty()) ? nullableList.stream() : empty();
  }
}
