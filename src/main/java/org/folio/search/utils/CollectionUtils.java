package org.folio.search.utils;

import java.util.LinkedHashMap;
import java.util.Map;
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
   * Checks whether given key has no value associated or this value is null.
   *
   * @param map - The map.
   * @param key - Key to check.
   * @param <K> - Key type
   * @param <V> - Value type
   * @return true if key has a non-null value associated, otherwise false.
   */
  public static <K, V> boolean hasNoValue(Map<K, V> map, K key) {
    return map.get(key) == null;
  }
}
