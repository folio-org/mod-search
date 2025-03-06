package org.folio.search.cql.searchterm;

import static org.folio.search.utils.CallNumberUtils.getShelfKeyFromCallNumber;
import static org.folio.search.utils.CallNumberUtils.normalizeEffectiveShelvingOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.folio.search.cql.SuDocCallNumber;
import org.folio.search.domain.dto.CallNumberType;
import org.marc4j.callnum.CallNumber;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;
import org.marc4j.callnum.NlmCallNumber;
import org.springframework.stereotype.Component;

@Component
public class EffectiveShelvingOrderTermProcessor implements SearchTermProcessor {

  private static final Map<String, Function<String, String>> CN_TYPE_TO_SHELF_KEY_GENERATOR = Map.of(
    CallNumberType.NLM.getValue(), cn -> getShelfKey(new NlmCallNumber(cn)),
    CallNumberType.LC.getValue(), cn -> getShelfKey(new LCCallNumber(cn)),
    CallNumberType.DEWEY.getValue(), cn -> getShelfKey(new DeweyCallNumber(cn)),
    CallNumberType.SUDOC.getValue(), cn -> getShelfKey(new SuDocCallNumber(cn))
  );

  @Override
  public String getSearchTerm(String inputTerm) {
    return getShelfKeyFromCallNumber(inputTerm).orElse(inputTerm);
  }

  public String getSearchTerm(String inputTerm, String callNumberTypeName) {
    if (callNumberTypeName == null) {
      return normalizeEffectiveShelvingOrder(inputTerm);
    }

    return Optional.ofNullable(CN_TYPE_TO_SHELF_KEY_GENERATOR.get(callNumberTypeName))
      .map(function -> function.apply(inputTerm))
      .orElse(normalizeEffectiveShelvingOrder(inputTerm));
  }

  public List<String> getSearchTerms(String inputTerm) {
    var searchTerms = new ArrayList<String>();

    searchTerms.add(getShelfKey(new NlmCallNumber(inputTerm)));
    searchTerms.add(getShelfKey(new LCCallNumber(inputTerm)));
    searchTerms.add(getShelfKey(new DeweyCallNumber(inputTerm)));
    searchTerms.add(getShelfKey(new SuDocCallNumber(inputTerm)));

    searchTerms.add(normalizeEffectiveShelvingOrder(inputTerm));

    return searchTerms;
  }

  private static String getShelfKey(CallNumber value) {
    return value.getShelfKey().trim();
  }
}
