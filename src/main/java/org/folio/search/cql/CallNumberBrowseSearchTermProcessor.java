package org.folio.search.cql;

import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.folio.search.service.setter.instance.CallNumberProcessor;
import org.marc4j.callnum.LCCallNumber;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CallNumberBrowseSearchTermProcessor implements SearchTermProcessor {

  private static final String SHELF_KEY_REGEX = "([A-Z]{1,2})\\s(\\d+(\\.\\d+)?)(\\s[A-Z]\\d+){0,2}(.*)";
  private static final Pattern SHELF_KEY_PATTERN = Pattern.compile(SHELF_KEY_REGEX);

  private final CallNumberProcessor callNumberProcessor;

  @Override
  public Long getSearchTerm(String term) {
    var termToProcess = SHELF_KEY_PATTERN.matcher(term).matches() ? term : new LCCallNumber(term).getShelfKey();
    return callNumberProcessor.getCallNumberAsLong(termToProcess);
  }
}
