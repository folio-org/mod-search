package org.folio.search.integration;

import static java.util.stream.Collectors.toSet;
import static org.folio.search.model.client.CqlQuery.exactMatchAny;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.client.InventoryReferenceDataClient;
import org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType;
import org.folio.search.model.client.CqlQueryParam;
import org.folio.search.model.service.ReferenceRecord;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ReferenceDataService {

  private static final int DEFAULT_LIMIT = 100;

  private final InventoryReferenceDataClient inventoryReferenceDataClient;

//  @Cacheable(cacheNames = REFERENCE_DATA_CACHE, unless = "#result.isEmpty()",
//             key = "@folioExecutionContext.tenantId + ':' + #values + ':' + #type.toString() + ':' + #param.toString()")
  public Set<String> fetchReferenceData(ReferenceDataType type, CqlQueryParam param, Collection<String> values) {
    log.info("Fetching reference [type: {}, field: {}, values: {}]", type.toString(), param.toString(), values);
    var uri = type.getUri();
    var query = exactMatchAny(param, values);
    try {
      return inventoryReferenceDataClient.getReferenceData(uri, query, DEFAULT_LIMIT)
        .getResult().stream().map(ReferenceRecord::getId)
        .collect(toSet());
    } catch (Exception e) {
      return Collections.emptySet();
    }
  }
}
