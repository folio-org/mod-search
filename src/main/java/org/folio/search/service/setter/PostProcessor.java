package org.folio.search.service.setter;

import java.util.List;
import org.opensearch.action.search.SearchResponse;

/**
 * Generic interface for post processors.
 */
public interface PostProcessor {
  /**
   * Processes List of objects retrieved from {@link SearchResponse}.
   *
   * @param objects List of objects to be processed
   */
  void process(List<Object> objects);
}
