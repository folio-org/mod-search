package org.folio.search.cql;

import static org.folio.search.service.setter.item.ItemEffectiveShelvingOrderProcessor.normalizeValue;

import java.util.Optional;
import org.marc4j.callnum.CallNumber;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;
import org.springframework.stereotype.Component;

@Component
public class EffectiveShelvingOrderTermProcessor implements SearchTermProcessor {

  @Override
  public String getSearchTerm(String inputTerm) {
    return getValidShelfKey(new LCCallNumber(inputTerm))
      .or(() -> getValidShelfKey(new DeweyCallNumber(inputTerm)))
      .orElse(normalizeValue(inputTerm))
      .trim();
  }

  private static Optional<String> getValidShelfKey(CallNumber value) {
    return Optional.of(value)
      .filter(CallNumber::isValid)
      .map(CallNumber::getShelfKey);
  }
}
