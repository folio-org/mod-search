package org.folio.search.model;

import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor(staticName = "of")
public class SearchResult<T> {

  /**
   * Amount of records found.
   */
  private final int totalRecords;

  /**
   * List with found records.
   */
  private final List<T> records;
}
