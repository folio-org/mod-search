package org.folio.search.cql;

import static org.folio.search.utils.CallNumberUtils.normalizeEffectiveShelvingOrder;

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
      .or(() -> getValidShelfKey(new SuDocCallNumber(inputTerm)))
      .or(() -> getValidShelfKey(new LCCallNumber(inputTerm)))
      .or(() -> getValidShelfKey(new DeweyCallNumber(inputTerm)))
      .orElse(normalizeEffectiveShelvingOrder(inputTerm))
      .trim();
  }

  private static Optional<String> getValidShelfKey(CallNumber value) {
    return Optional.of(value)
      .filter(CallNumber::isValid)
      .map(CallNumber::getShelfKey);
  }
}
