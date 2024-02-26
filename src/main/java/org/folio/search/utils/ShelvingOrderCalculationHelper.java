package org.folio.search.utils;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;

@UtilityClass
public class ShelvingOrderCalculationHelper {

  public static String calculate(@NonNull String input, @NonNull ShelvingOrderAlgorithmType algorithmType) {
    return switch (algorithmType) {
      case LC -> new LCCallNumber(input).getShelfKey().trim();
      case DEWEY -> new DeweyCallNumber(input).getShelfKey().trim();
      case DEFAULT -> CallNumberUtils.normalizeEffectiveShelvingOrder(input);
    };
  }
}
