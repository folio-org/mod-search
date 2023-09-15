package org.folio.search.cql;

import static org.folio.search.utils.CallNumberUtils.normalizeEffectiveShelvingOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.marc4j.callnum.CallNumber;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;
import org.marc4j.callnum.NlmCallNumber;
import org.springframework.stereotype.Component;

@Component
public class EffectiveShelvingOrderTermProcessor implements SearchTermProcessor {

  @Override
  public List<String> getSearchTerm(String inputTerm) {

    var searchTerms = new ArrayList<String>();

    getValidShelfKey(searchTerms, new SuDocCallNumber(inputTerm));
    getValidShelfKey(searchTerms, new NlmCallNumber(inputTerm));
    getValidShelfKey(searchTerms, new LCCallNumber(inputTerm));
    getValidShelfKey(searchTerms, new DeweyCallNumber(inputTerm));

    if (searchTerms.isEmpty()) {
      searchTerms.add(normalizeEffectiveShelvingOrder(inputTerm).trim());
    }

    return searchTerms;
  }

  private static void getValidShelfKey(List<String> searchTerms, CallNumber value) {
    Optional.of(value)
      .filter(CallNumber::isValid)
      .map(CallNumber::getShelfKey)
      .map(String::trim)
      .ifPresent(searchTerms::add);
  }
}
