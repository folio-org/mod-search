package org.folio.search.utils;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Stream.empty;
import static java.util.stream.StreamSupport.stream;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

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
    return MapUtils.isEmpty(map) ? null : map;
  }

  /**
   * Merges given array of maps into single {@link LinkedHashMap} object.
   *
   * @param maps array of maps to merge into single one.
   * @param <K>  generic type for map key
   * @param <V>  generic type for map value
   * @return merged maps into one {@link LinkedHashMap} object, null - if result is empty.
   */
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
   * Converts iterable to {@link LinkedHashMap} using given mapper functions.
   *
   * @param iterable    - iterable value to process as {@link Iterable} object
   * @param keyMapper   - key mapper as {@link Function} object
   * @param valueMapper - value mapper as {@link Function} object
   * @param <T>         generic type for iterable value
   * @param <K>         generic type for map key
   * @param <V>         generic type for map value
   * @return created {@link LinkedHashMap} object from {@link Iterable}
   */
  public static <T, K, V> Map<K, V> toMap(Iterable<T> iterable, Function<T, K> keyMapper, Function<T, V> valueMapper) {
    var resultMap = new LinkedHashMap<K, V>();
    for (var value : iterable) {
      resultMap.put(keyMapper.apply(value), valueMapper.apply(value));
    }
    return resultMap;
  }

  /**
   * Merges given array of collections into single {@link LinkedHashSet} object.
   *
   * @param collections array of sets to merge into single one.
   * @param <T>         generic type for collection value
   * @return merged maps into one {@link LinkedHashSet} object.
   */
  @SafeVarargs
  public static <T> Set<T> mergeSafelyToSet(Collection<T>... collections) {
    var set = new LinkedHashSet<T>();
    for (Collection<T> collection : collections) {
      if (isNotEmpty(collection)) {
        set.addAll(collection);
      }
    }
    return set;
  }

  /**
   * Merges given array of collections into single {@link ArrayList} object.
   *
   * @param collections array of collection to merge into single one.
   * @param <T>         generic type for collection value
   * @return merged maps into one {@link LinkedHashSet} object.
   */
  @SafeVarargs
  public static <T> List<T> mergeSafelyToList(Collection<T>... collections) {
    var set = new ArrayList<T>();
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
   * @param initial      initial list, where values should be added
   * @param sourceValues list with source values
   * @param addToTop     boolean property, which specifies if values should be added to the beginning of the list or not
   * @param <T>          generic type for list values
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
  public static <T> Collector<T, ?, Set<T>> toLinkedHashSet() {
    return toCollection(LinkedHashSet::new);
  }

  /**
   * Collects elements from the given {@link Iterable} object into the new {@link LinkedHashMap} object.
   *
   * @param keyMapper - key mapper as {@link Function} object
   * @param <K>       - generic type for map keys
   * @param <V>       - generic type for map values
   * @return {@link LinkedHashMap} object with elements from iterable in encounter order.
   */
  public static <K, V> Map<K, V> toLinkedHashMap(Iterable<V> iterable, Function<V, K> keyMapper) {
    var resultMap = new LinkedHashMap<K, V>();
    iterable.forEach(value -> resultMap.put(keyMapper.apply(value), value));
    return resultMap;
  }

  /**
   * Returns a Collector that accumulates the input elements into a new {@link LinkedHashMap} in encounter order.
   *
   * @param keyMapper - key mapper as {@link Function} object
   * @param <K>       - generic type for map keys
   * @param <V>       - generic type for map values
   * @return a {@link Collector} which collects all the input elements into a {@link LinkedHashMap} in encounter order
   */
  public static <K, V> Collector<V, ?, Map<K, V>> toLinkedHashMap(Function<V, K> keyMapper) {
    return toLinkedHashMap(keyMapper, Function.identity());
  }

  /**
   * Returns a Collector that accumulates the input elements into a new {@link LinkedHashMap} in encounter order.
   *
   * @param keyMapper   - key mapper as {@link Function} object
   * @param valueMapper - value mapper as {@link Function} object
   * @param <T>         - generic type for input values
   * @param <K>         - generic type for map keys
   * @param <V>         - generic type for map values
   * @return a {@link Collector} which collects all the input elements into a {@link LinkedHashMap} in encounter order
   */
  public static <T, K, V> Collector<T, ?, Map<K, V>> toLinkedHashMap(
    Function<T, K> keyMapper, Function<T, V> valueMapper) {
    return Collectors.toMap(keyMapper, valueMapper, (o, n) -> n, LinkedHashMap::new);
  }

  /**
   * Returns nullableList if it is not null or empty, defaultList otherwise.
   *
   * @param nullableList nullable value to check
   * @param <T>          generic type for value
   * @return nullableList if it is not null or empty, defaultList otherwise.
   */
  public static <T> Stream<T> toStreamSafe(List<T> nullableList) {
    return (nullableList != null && !nullableList.isEmpty()) ? nullableList.stream() : empty();
  }

  /**
   * Returns list if set is not null or empty, null otherwise.
   *
   * @param nullableSet nullable value to check
   * @param <T>         generic type for value
   * @return list if it is not null or empty, null otherwise.
   */
  public static <T> List<T> toListSafe(Set<T> nullableSet) {
    return isEmpty(nullableSet) ? null : new ArrayList<>(nullableSet);
  }

  /**
   * Returns filtered list if set is not null or empty after filtration, null otherwise.
   *
   * @param nullableSet nullable value to check
   * @param filter      predicate for filtering incoming set
   * @param <T>         generic type for value
   * @return list if it is not null or empty, null otherwise.
   */
  public static <T> List<T> toListSafe(Set<T> nullableSet, Predicate<T> filter) {
    var filteredSet = nullableSet.stream().filter(filter).collect(Collectors.toSet());
    return isEmpty(filteredSet) ? null : new ArrayList<>(filteredSet);
  }

  /**
   * Return the last element of the given list.
   *
   * @param list - list to process as {@link List} object
   * @param <T>  - generic type for list elements
   * @return {@link Optional} of the latest element of the list, it will be empty if the given list is empty.
   */
  public static <T> Optional<T> findLast(List<T> list) {
    return isEmpty(list) ? Optional.empty() : Optional.ofNullable(list.get(list.size() - 1));
  }

  /**
   * Return the first element of the given list.
   *
   * @param list - list to process as {@link List} object
   * @param <T>  - generic type for list elements
   * @return {@link Optional} of the first element of the list, it will be empty if the given list is empty.
   */
  public static <T> Optional<T> findFirst(List<T> list) {
    return isEmpty(list) ? Optional.empty() : Optional.ofNullable(list.get(0));
  }

  /**
   * Verifies that some element in iterable satisfies given condition.
   *
   * @param iterable - iterable to check
   * @param checker  - predicate for iterable elements
   * @param <T>      generic type for iterable elements
   * @return true if any element matched
   */
  public static <T> boolean anyMatch(Iterable<T> iterable, Predicate<T> checker) {
    for (var element : iterable) {
      if (checker.test(element)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Verifies that all elements in iterable do not satisfy given condition.
   *
   * @param iterable - iterable to check
   * @param checker  - predicate for iterable elements
   * @param <T>      generic type for iterable element
   * @return true if all elements do not match given condition
   */
  public static <T> boolean noneMatch(Iterable<T> iterable, Predicate<T> checker) {
    return !anyMatch(iterable, checker);
  }

  /**
   * Verifies that all elements in iterable satisfy given condition.
   *
   * @param iterable - iterable to check
   * @param checker  - predicate for iterable elements
   * @param <T>      generic type for iterable elements
   * @return true if any element matched
   */
  public static <T> boolean allMatch(Iterable<T> iterable, Predicate<T> checker) {
    for (var element : iterable) {
      if (!checker.test(element)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Return {@link Predicate} to distinct objects by single key.
   *
   * @param keyExtractor - function to extract value
   * @param <T>      generic type of object to distinct
   * @return {@link Predicate} that maintains a state about what it has seen before
   */
  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }

  /**
   * Returns {@link List} with {@link String} values by path from passed map.
   *
   * @param map  - map with resource fields
   * @param path - search path, where fields are separated with {@code .} character
   * @return {@link List} with {@link String} values, it would be empty if map does not contain correct value by path.
   */
  public static List<String> getValuesByPath(Map<String, Object> map, String path) {
    if (StringUtils.isBlank(path) || MapUtils.isEmpty(map)) {
      return emptyList();
    }
    var pathValues = path.split("\\.");
    var currentField = map.get(pathValues[0]);
    for (var i = 1; i < pathValues.length; i++) {
      if (currentField instanceof Map<?, ?>) {
        currentField = ((Map<?, ?>) currentField).get(pathValues[i]);
      } else if (isListContainingMaps(currentField)) {
        currentField = getValueForList((Iterable<?>) currentField, pathValues[i]);
      } else {
        return emptyList();
      }
    }

    return getStrings(currentField);
  }

  /**
   * Reverses the elements in the given list.
   *
   * @param list - a list to process
   * @param <T>  - generic type for the list elements
   * @return reversed list
   */
  public static <T> List<T> reverse(List<T> list) {
    var listIterator = list.listIterator(list.size());
    var result = new ArrayList<T>();
    while (listIterator.hasPrevious()) {
      result.add(listIterator.previous());
    }
    return result;
  }

  /**
   * Subtracts target collection from the source and returns it as a new set value.
   *
   * @param source - source collection to subtract from
   * @param target - target collection to be subtracted
   * @param <T>    - generic type for collection elements
   * @return a new {@link LinkedHashSet} object with a subtraction result
   */
  public static <T> Set<T> subtract(Collection<T> source, Collection<T> target) {
    var result = new LinkedHashSet<>(source);
    target.forEach(result::remove);
    return result;
  }

  public static <T> Set<T> subtractSorted(Collection<T> source, Collection<T> target) {
    return subtract(source, target).stream()
      .filter(Objects::nonNull)
      .sorted()
      .collect(toCollection(LinkedHashSet::new));
  }

  private static List<?> getValueForList(Iterable<?> iterable, String pathValue) {
    return stream(iterable.spliterator(), false)
      .filter(Map.class::isInstance)
      .map(value -> ((Map<?, ?>) value).get(pathValue))
      .flatMap(CollectionUtils::unwrapIfPossible)
      .toList();
  }

  private static Stream<?> unwrapIfPossible(Object object) {
    return isListContainingMaps(object) ? stream(((Iterable<?>) object).spliterator(), false) : Stream.of(object);
  }

  private static boolean isListContainingMaps(Object object) {
    if (object instanceof Iterable<?>) {
      return anyMatch((Iterable<?>) object, Map.class::isInstance);
    }
    return false;
  }

  private static List<String> getStrings(Object currentField) {
    if (currentField instanceof String string) {
      return singletonList(string);
    }

    if (currentField instanceof Iterable<?>) {
      return stream(((Iterable<?>) currentField).spliterator(), false)
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .toList();
    }

    return emptyList();
  }
}
