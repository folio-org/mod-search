package org.folio.search.service.consortium;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ConsortiumLocation;
import org.folio.search.domain.dto.SortOrder;
import org.folio.search.model.SearchResult;
import org.folio.search.repository.ConsortiumLocationRepository;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ConsortiumLocationService {

  public static final String NAME = "name";
  public static final String ID = "id";
  public static final String TENANT_ID = "tenantId";
  private static final int MAX_RESULT_WINDOW = 10000;
  private final ConsortiumLocationRepository repository;
  private final ConsortiumTenantExecutor executor;

  public SearchResult<ConsortiumLocation> fetchLocations(String tenantHeader,
                                                         String tenantId,
                                                         Integer limit,
                                                         Integer offset,
                                                         String sortBy,
                                                         SortOrder sortOrder) {
    log.info("fetching consortium locations for tenant: {}, tenantId: {}, sortBy: {}",
      tenantHeader,
      tenantId,
      sortBy);
    validatePaginationParameters(limit, offset);
    validateSortByValue(sortBy);
    return executor.execute(
      tenantHeader,
      () -> repository.fetchLocations(tenantHeader, tenantId, limit, offset, sortBy, sortOrder));
  }

  private void validateSortByValue(String sortBy) {
    if (!(NAME.equals(sortBy) || ID.equals(sortBy) || TENANT_ID.equals(sortBy))) {
      throw new IllegalArgumentException("Invalid sortBy value: " + sortBy);
    }
  }

  private void validatePaginationParameters(Integer limit, Integer offset) {
    if (limit + offset > MAX_RESULT_WINDOW) {
      throw new IllegalArgumentException("The combination of limit and offset exceeds "
        + "the maximum result window of " + MAX_RESULT_WINDOW);
    }
  }

}
