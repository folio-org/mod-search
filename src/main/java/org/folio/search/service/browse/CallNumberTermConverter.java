package org.folio.search.service.browse;

import lombok.RequiredArgsConstructor;
import org.folio.search.cql.EffectiveShelvingOrderTermProcessor;
import org.folio.search.service.setter.item.ItemCallNumberProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CallNumberTermConverter {

  private final ItemCallNumberProcessor itemCallNumberProcessor;
  private final EffectiveShelvingOrderTermProcessor effectiveShelvingOrderTermProcessor;

  /**
   * Converts given term to numeric value.
   *
   * @param term - search term to process for call-number browsing
   * @return numeric value for the given term
   */
  public Long convert(String term) {
    var searchTerm = effectiveShelvingOrderTermProcessor.getSearchTerm(term);
    return itemCallNumberProcessor.getCallNumberAsLong(searchTerm);
  }
}
