package org.folio.search.repository.cache;

import static java.util.stream.Collectors.toSet;
import static org.folio.search.client.cql.CqlQuery.exactMatchAny;
import static org.folio.search.configuration.SearchCacheNames.ALTERNATIVE_TITLE_TYPES_CACHE;
import static org.folio.search.configuration.SearchCacheNames.IDENTIFIER_IDS_CACHE;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.client.AlternativeTitleTypesClient;
import org.folio.search.client.IdentifierTypeClient;
import org.folio.search.client.cql.CqlQuery;
import org.folio.search.model.service.ReferenceRecord;
import org.folio.search.model.service.ResultList;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class InstanceReferenceDataService {

  private final IdentifierTypeClient identifierTypeClient;
  private final AlternativeTitleTypesClient alternativeTitleTypesClient;

  @Cacheable(cacheNames = IDENTIFIER_IDS_CACHE, key = "@folioExecutionContext.tenantId + ':' + #identifiers")
  public Set<String> fetchIdentifierIds(Collection<String> identifiers) {
    log.info("Fetching identifiers [identifierNames: {}]", identifiers);
    return fetchReferenceData(identifiers, identifierTypeClient::getIdentifierTypes);
  }

  @Cacheable(
    cacheNames = ALTERNATIVE_TITLE_TYPES_CACHE,
    key = "@folioExecutionContext.tenantId + ':' + #alternativeTitleTypeNames")
  public Set<String> fetchAlternativeTitleIds(Collection<String> alternativeTitleTypeNames) {
    log.info("Fetching alternative title types [alternativeTitleTypeNames: {}]", alternativeTitleTypeNames);
    return fetchReferenceData(alternativeTitleTypeNames, alternativeTitleTypesClient::getAlternativeTitleTypes);
  }

  private static Set<String> fetchReferenceData(Collection<String> names,
    Function<CqlQuery, ResultList<ReferenceRecord>> dataProvider) {
    return dataProvider.apply(exactMatchAny("name", names)).getResult().stream()
      .map(ReferenceRecord::getId)
      .collect(toSet());
  }
}
