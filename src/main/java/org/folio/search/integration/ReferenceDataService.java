package org.folio.search.integration;

import static java.util.stream.Collectors.toSet;
import static org.folio.search.configuration.SearchCacheNames.REFERENCE_DATA_CACHE;
import static org.folio.search.model.client.CqlQuery.exactMatchAny;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.client.InventoryReferenceDataClient;
import org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType;
import org.folio.search.model.service.ReferenceRecord;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ReferenceDataService {

  private final InventoryReferenceDataClient inventoryReferenceDataClient;

  @Cacheable(cacheNames = REFERENCE_DATA_CACHE, unless = "#result.isEmpty()",
             key = "@folioExecutionContext.tenantId + ':' + #names + ':' + #type.toString()")
  public Set<String> fetchReferenceData(ReferenceDataType type, Collection<String> names) {
    log.info("Fetching identifiers [identifierNames: {}]", names);
    var uri = type.getUri();
    var query = exactMatchAny("name", names);
    try {
      return inventoryReferenceDataClient.getReferenceData(uri, query)
        .getResult().stream().map(ReferenceRecord::getId)
        .collect(toSet());
    } catch (Exception e) {
      return Collections.emptySet();
    }
  }
}
