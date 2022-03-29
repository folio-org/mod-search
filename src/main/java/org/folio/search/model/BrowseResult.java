package org.folio.search.model;

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections.CollectionUtils;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class BrowseResult<T> {

  /**
   * Amount of records found.
   */
  private int totalRecords;

  /**
   * Previous value for browsing backward.
   */
  private String prev;

  /**
   * Next value for browsing forward.
   */
  private String next;

  /**
   * List with found records.
   */
  private List<T> records;

  /**
   * Creates {@link BrowseResult} object from {@link SearchResult} value.
   *
   * @param result - {@link SearchResult} object to be processed
   * @param <R> - generic type for {@link BrowseResult} records
   * @return created {@link BrowseResult} object
   */
  public static <R> BrowseResult<R> of(SearchResult<R> result) {
    return new BrowseResult<>(result.getTotalRecords(), null, null, result.getRecords());
  }

  /**
   * Creates {@link BrowseResult} object from the number of total records and list of items.
   *
   * @param totalRecords - number of found items
   * @param records - found items
   * @param <R> - generic type for {@link BrowseResult} records
   * @return created {@link BrowseResult} object
   */
  public static <R> BrowseResult<R> of(int totalRecords, List<R> records) {
    return new BrowseResult<>(totalRecords, null, null, records);
  }

  /**
   * Creates empty {@link BrowseResult} object.
   *
   * @param <R> - generic type for result elements
   * @return empty {@link BrowseResult} object
   */
  public static <R> BrowseResult<R> empty() {
    return new BrowseResult<>(0, null, null, emptyList());
  }

  /**
   * Sets total records and returns {@link BrowseResult} object.
   *
   * @param totalRecords - amount of records in search response
   * @return {@link SearchResult} with new total records value
   */
  public BrowseResult<T> totalRecords(int totalRecords) {
    this.totalRecords = totalRecords;
    return this;
  }

  /**
   * Sets records and returns {@link BrowseResult} object.
   *
   * @param records - list of records to set
   * @return {@link SearchResult} with new records value
   */
  public BrowseResult<T> records(List<T> records) {
    this.records = records;
    return this;
  }

  /**
   * Sets the previous value to the {@link BrowseResult} value.
   *
   * @param prev - previous value for browsing backward
   * @return {@link SearchResult} with new prev value
   */
  public BrowseResult<T> prev(String prev) {
    this.prev = prev;
    return this;
  }

  /**
   * Sets the next value to the {@link BrowseResult} value.
   *
   * @param next - next value for browsing forward
   * @return {@link SearchResult} with new next value
   */
  public BrowseResult<T> next(String next) {
    this.next = next;
    return this;
  }

  /**
   * Checks if search result is empty or not.
   *
   * @return true - if search result is empty, false - otherwise.
   */
  public boolean isEmpty() {
    return CollectionUtils.isEmpty(records);
  }

  /**
   * Maps search result records using given mapping function.
   *
   * @param mappingFunction - mapping function for result element conversion
   * @param <R> - generic type for search result elements.
   * @return new search result object with mapped elements
   */
  public <R> BrowseResult<R> map(Function<T, R> mappingFunction) {
    var resultList = new ArrayList<R>();
    for (T record : this.records) {
      resultList.add(mappingFunction.apply(record));
    }

    return new BrowseResult<>(this.totalRecords, this.prev, this.next, resultList);
  }
}
