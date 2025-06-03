package org.folio.search.service.consortium;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ConsortiumCampus;
import org.folio.search.domain.dto.SortOrder;
import org.folio.search.model.SearchResult;
import org.folio.search.repository.ConsortiumCampusRepository;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ConsortiumCampusService {

  private static final Set<String> VALID_SORT_BY = Set.of("id", "name", "tenantId");
  private final ConsortiumCampusRepository repository;
  private final ConsortiumTenantExecutor executor;

  public SearchResult<ConsortiumCampus> fetchCampuses(String tenantHeader,
                                                      String tenantId,
                                                      String id,
                                                      Integer limit,
                                                      Integer offset,
                                                      String sortBy,
                                                      SortOrder sortOrder) {
    log.info("fetching consortium campuses for tenant: {}, tenantId: {}, id: {}, sortBy: {}",
      tenantHeader,
      tenantId,
      id,
      sortBy);

    validateSortByValue(sortBy);

    return executor.execute(
      tenantHeader,
      () -> repository.fetchCampuses(tenantHeader, tenantId, id, limit, offset, sortBy, sortOrder));
  }

  private void validateSortByValue(String sortBy) {
    if (!VALID_SORT_BY.contains(sortBy)) {
      throw new IllegalArgumentException("Invalid sortBy value: " + sortBy);
    }
  }
}
