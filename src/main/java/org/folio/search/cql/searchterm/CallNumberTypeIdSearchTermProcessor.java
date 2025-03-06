package org.folio.search.cql.searchterm;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.service.consortium.BrowseConfigServiceDecorator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CallNumberTypeIdSearchTermProcessor implements SearchTermProcessor {

  private final BrowseConfigServiceDecorator configService;

  @Override
  public Object getSearchTerm(String inputTerm) {
    try {
      var browseOptionType = BrowseOptionType.fromValue(inputTerm);
      var browseConfig = configService.getConfig(BrowseType.CALL_NUMBER, browseOptionType);
      return browseConfig.getTypeIds() == null
             ? new String[0]
             : browseConfig.getTypeIds().stream().map(Object::toString).toArray(String[]::new);
    } catch (IllegalArgumentException e) {
      return inputTerm;
    }
  }
}
