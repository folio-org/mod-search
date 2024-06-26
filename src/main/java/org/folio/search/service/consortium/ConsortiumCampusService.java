package org.folio.search.service.consortium;

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

  public static final String ID = "id";
  public static final String NAME = "name";
  public static final String TENANT_ID = "tenantId";
  private final ConsortiumCampusRepository repository;
  private final ConsortiumTenantExecutor executor;

  public SearchResult<ConsortiumCampus> fetchCampuses(String tenantHeader,
                                                      String tenantId,
                                                      Integer limit,
                                                      Integer offset,
                                                      String sortBy,
                                                      SortOrder sortOrder) {
    log.info("fetching consortium campuses for tenant: {}, tenantId: {}, sortBy: {}",
      tenantHeader,
      tenantId,
      sortBy);

    validateSortByValue(sortBy);

    return executor.execute(
      tenantHeader,
      () -> repository.fetchCampuses(tenantHeader, tenantId, limit, offset, sortBy, sortOrder));
  }

  private void validateSortByValue(String sortBy) {
    if (!(NAME.equals(sortBy) || ID.equals(sortBy)  || TENANT_ID.equals(sortBy))) {
      throw new IllegalArgumentException("Invalid sortBy value: " + sortBy);
    }
  }

}
