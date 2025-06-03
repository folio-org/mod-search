package org.folio.search.service.consortium;

import static org.folio.search.service.SearchService.DEFAULT_MAX_SEARCH_RESULT_WINDOW;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ConsortiumLocation;
import org.folio.search.domain.dto.SortOrder;
import org.folio.search.exception.RequestValidationException;
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
  private final ConsortiumLocationRepository repository;
  private final ConsortiumTenantExecutor executor;

  public SearchResult<ConsortiumLocation> fetchLocations(String tenantHeader,
                                                         String tenantId,
                                                         String id,
                                                         Integer limit,
                                                         Integer offset,
                                                         String sortBy,
                                                         SortOrder sortOrder) {
    log.info("fetching consortium locations for tenant: {}, tenantId: {}, id: {}, sortBy: {}",
      tenantHeader,
      tenantId,
      id,
      sortBy);
    validatePaginationParameters(limit, offset);
    validateSortByValue(sortBy);
    return executor.execute(
      tenantHeader,
      () -> repository.fetchLocations(tenantHeader, tenantId, id, limit, offset, sortBy, sortOrder));
  }

  private void validateSortByValue(String sortBy) {
    if (!(NAME.equals(sortBy) || ID.equals(sortBy) || TENANT_ID.equals(sortBy))) {
      var validationException = new RequestValidationException("Invalid sortBy value is being used",
        "sortBy", sortBy);
      log.warn(validationException.getMessage());
      throw validationException;
    }
  }

  private void validatePaginationParameters(Integer limit, Integer offset) {
    if (offset + limit > DEFAULT_MAX_SEARCH_RESULT_WINDOW) {
      var validationException = new RequestValidationException("The sum of limit and offset should not exceed 10000.",
        "offset + limit", String.valueOf(offset + limit));
      log.warn(validationException.getMessage());
      throw validationException;
    }
  }
}
