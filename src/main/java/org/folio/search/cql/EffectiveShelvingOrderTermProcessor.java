package org.folio.search.cql;

import static org.folio.search.utils.CallNumberUtils.normalizeEffectiveShelvingOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.folio.search.domain.dto.CallNumberType;
import org.marc4j.callnum.CallNumber;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;
import org.marc4j.callnum.NlmCallNumber;
import org.springframework.stereotype.Component;

@Component
public class EffectiveShelvingOrderTermProcessor implements SearchTermProcessor {

  private static final Map<String, Function<String, Optional<String>>> CN_TYPE_TO_SHELF_KEY_GENERATOR = Map.of(
    CallNumberType.NLM.getValue(), cn -> getValidShelfKey(new NlmCallNumber(cn)),
    CallNumberType.LC.getValue(), cn -> getValidShelfKey(new LCCallNumber(cn)),
    CallNumberType.DEWEY.getValue(), cn -> getValidShelfKey(new DeweyCallNumber(cn)),
    CallNumberType.SUDOC.getValue(), cn -> getValidShelfKey(new SuDocCallNumber(cn))
  );

  @Override
  public String getSearchTerm(String inputTerm) {
    return getValidShelfKey(new NlmCallNumber(inputTerm))
      .or(() -> getValidShelfKey(new LCCallNumber(inputTerm)))
      .or(() -> getValidShelfKey(new DeweyCallNumber(inputTerm)))
      .or(() -> getValidShelfKey(new SuDocCallNumber(inputTerm)))
      .orElse(normalizeEffectiveShelvingOrder(inputTerm))
      .trim();
  }

  public String getSearchTerm(String inputTerm, String callNumberTypeName) {
    if (callNumberTypeName == null) {
      return normalizeEffectiveShelvingOrder(inputTerm);
    }

    return Optional.ofNullable(CN_TYPE_TO_SHELF_KEY_GENERATOR.get(callNumberTypeName))
      .flatMap(function -> function.apply(inputTerm))
      .orElse(normalizeEffectiveShelvingOrder(inputTerm))
      .trim();
  }

  public List<String> getSearchTerms(String inputTerm) {
    var searchTerms = new ArrayList<String>();

    addShelfKeyIfValid(searchTerms, new NlmCallNumber(inputTerm));
    addShelfKeyIfValid(searchTerms, new LCCallNumber(inputTerm));
    addShelfKeyIfValid(searchTerms, new DeweyCallNumber(inputTerm));
    addShelfKeyIfValid(searchTerms, new SuDocCallNumber(inputTerm));

    searchTerms.add(normalizeEffectiveShelvingOrder(inputTerm));

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
