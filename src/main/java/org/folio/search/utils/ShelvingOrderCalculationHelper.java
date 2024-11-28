package org.folio.search.utils;

import java.util.Locale;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.folio.search.cql.SuDocCallNumber;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;
import org.marc4j.callnum.NlmCallNumber;

@UtilityClass
public class ShelvingOrderCalculationHelper {

  public static String calculate(@NonNull String input, @NonNull ShelvingOrderAlgorithmType algorithmType) {
    return switch (algorithmType) {
      case LC -> new LCCallNumber(input).getShelfKey().trim();
      case DEWEY -> new DeweyCallNumber(input).getShelfKey().trim();
      case NLM -> new NlmCallNumber(input).getShelfKey().trim();
      case SUDOC -> new SuDocCallNumber(input).getShelfKey().trim();
      case OTHER, DEFAULT -> normalize(input);
    };
  }

  private static String normalize(String input) {
    return input.toUpperCase(Locale.ROOT).trim();
  }
}
