package org.folio.search.repository.cache;

import static org.folio.search.client.cql.CqlQuery.exactMatchAny;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.client.IdentifierTypeClient;
import org.folio.search.model.service.ReferenceRecord;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class InstanceIdentifierTypeCache {
  public static final String CACHE_NAME = "identifier-types";
  private final IdentifierTypeClient client;

  @Cacheable(cacheNames = CACHE_NAME,
    key = "@folioExecutionContext.tenantId + ': ' + #identifiers")
  public Set<String> fetchIdentifierIds(Collection<String> identifiers) {
    return client.getIdentifierTypes(exactMatchAny("name", identifiers))
      .getResult().stream()
      .peek(identifier -> log.info("Identifier fetched [identifier={}]", identifier))
      .map(ReferenceRecord::getId)
      .collect(Collectors.toSet());
  }
}
