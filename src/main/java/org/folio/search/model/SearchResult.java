package org.folio.search.model;

import static java.util.Collections.emptyList;
import static org.folio.search.utils.CollectionUtils.reverse;

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
public class SearchResult<T> {

  /**
   * Amount of records found.
   */
  private int totalRecords;

  /**
   * List with found records.
   */
  private List<T> records;

  /**
   * Maps search result records using given mapping function.
   *
   * @param mappingFunction - mapping function for result element conversion
   * @param <R> - generic type for search result elements.
   * @return new search result object with mapped elements
   */
  public <R> SearchResult<R> map(Function<T, R> mappingFunction) {
    var resultList = new ArrayList<R>();
    for (T record : this.records) {
      resultList.add(mappingFunction.apply(record));
    }

    return new SearchResult<>(this.totalRecords, resultList);
  }

  /**
   * Creates empty {@link SearchResult} object.
   *
   * @param <R> - generic type for result elements
   * @return empty {@link SearchResult} object
   */
  public static <R> SearchResult<R> empty() {
    return new SearchResult<>(0, emptyList());
  }

  /**
   * Sets total records and returns {@link SearchResult} object.
   *
   * @param totalRecords - amount of records in search response
   * @return {@link SearchResult} with new total records value
   */
  public SearchResult<T> totalRecords(int totalRecords) {
    this.totalRecords = totalRecords;
    return this;
  }

  /**
   * Sets records and returns {@link SearchResult} object.
   *
   * @param records - list of records to set
   * @return {@link SearchResult} with new records value
   */
  public SearchResult<T> records(List<T> records) {
    this.records = records;
    return this;
  }

  public SearchResult<T> withReversedRecords() {
    this.records = reverse(this.records);
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
}
