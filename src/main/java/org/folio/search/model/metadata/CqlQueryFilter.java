package org.folio.search.model.metadata;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class CqlQueryFilter {

  /**
   * Query filter in format 'esFieldName = someValue'.
   */
  private String queryFilter;

  /**
   * List of cql query terms which should be considered during building a elasticsearch query.
   */
  private List<String> queryTerms = Collections.emptyList();
}
