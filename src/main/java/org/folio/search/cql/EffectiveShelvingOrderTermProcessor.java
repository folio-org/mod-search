package org.folio.search.cql;

import static org.apache.commons.lang3.StringUtils.toRootUpperCase;
import static org.folio.search.utils.CollectionUtils.anyMatch;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.marc4j.callnum.CallNumber;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;
import org.springframework.stereotype.Component;

@Component
public class EffectiveShelvingOrderTermProcessor implements SearchTermProcessor {

  private static final Pattern DEWEY_NUMBER_PATTERN = Pattern.compile("\\d{4}(\\.\\d+)?(\\s.{0,10}){0,10}");
  private static final Pattern SHELF_KEY_PATTERN =
    Pattern.compile("([A-Z]+)\\s(\\d{2,}(\\.\\d+)?)(\\s[A-Z]\\d{1,10}){0,2}(\\s.{0,10}){0,10}");

  private static final List<Pattern> PATTERNS = List.of(SHELF_KEY_PATTERN, DEWEY_NUMBER_PATTERN);

  @Override
  public String getSearchTerm(String inputTerm) {
    if (StringUtils.isEmpty(inputTerm) || anyMatch(PATTERNS, pattern -> pattern.matcher(inputTerm).matches())) {
      return toRootUpperCase(inputTerm);
    }

    return getValidShelfKey(new LCCallNumber(inputTerm))
      .or(() -> getValidShelfKey(new DeweyCallNumber(inputTerm)))
      .orElse(toRootUpperCase(inputTerm));
  }

  private static Optional<String> getValidShelfKey(CallNumber value) {
    return Optional.of(value)
      .filter(CallNumber::isValid)
      .map(CallNumber::getShelfKey);
  }
}
