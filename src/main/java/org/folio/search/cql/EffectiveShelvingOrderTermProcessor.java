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
  public String getSearchTerm(String inputTerm) {
    return getValidShelfKey(new NlmCallNumber(inputTerm))
      .or(() -> getValidShelfKey(new LCCallNumber(inputTerm)))
      .or(() -> getValidShelfKey(new DeweyCallNumber(inputTerm)))
      .or(() -> getValidShelfKey(new SuDocCallNumber(inputTerm)))
      .orElse(normalizeEffectiveShelvingOrder(inputTerm))
      .trim();
  }

  public List<String> getSearchTerms(String inputTerm) {

    var searchTerms = new ArrayList<String>();

    addShelfKeyIfValid(searchTerms, new NlmCallNumber(inputTerm));
    addShelfKeyIfValid(searchTerms, new LCCallNumber(inputTerm));
    addShelfKeyIfValid(searchTerms, new DeweyCallNumber(inputTerm));
    addShelfKeyIfValid(searchTerms, new SuDocCallNumber(inputTerm));

    if (searchTerms.isEmpty()) {
      searchTerms.add(normalizeEffectiveShelvingOrder(inputTerm));
    }

    return searchTerms;
  }

  private static Optional<String> getValidShelfKey(CallNumber value) {
    return Optional.of(value)
      .filter(CallNumber::isValid)
      .map(CallNumber::getShelfKey);
  }

  private static void addShelfKeyIfValid(List<String> searchTerms, CallNumber value) {
    getValidShelfKey(value)
      .map(String::trim)
      .ifPresent(searchTerms::add);
  }
}
