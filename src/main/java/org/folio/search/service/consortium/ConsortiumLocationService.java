package org.folio.search.service.consortium;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.Location;
import org.folio.search.domain.dto.SortOrder;
import org.folio.search.model.SearchResult;
import org.folio.search.repository.ConsortiumLocationRepository;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ConsortiumLocationService {

  private final ConsortiumLocationRepository repository;

  public SearchResult<Location> fetchLocations(String tenantHeader,
                                               String tenantId,
                                               Integer limit,
                                               Integer offset,
                                               String sortBy,
                                               SortOrder sortOrder) {
    log.info("fetching consortium locations for tenant: {}", tenantHeader);
    return repository.fetchLocations(tenantHeader, tenantId, limit, offset, sortBy, sortOrder);
  }

}
