package org.folio.search.model;

import static java.util.Collections.emptyList;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class SearchResult<T> {

  /**
   * Amount of records found.
   */
  protected int totalRecords;

  /**
   * List with found records.
   */
  protected List<T> records;

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

  /**
   * Checks if search result is empty or not.
   *
   * @return true - if search result is empty, false - otherwise.
   */
  public boolean isEmpty() {
    return CollectionUtils.isEmpty(records);
  }
}
