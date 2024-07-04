package org.folio.search.service.consortium;

import java.util.Set;

import org.folio.search.domain.dto.ConsortiumInstitution;
import org.folio.search.domain.dto.SortOrder;
import org.folio.search.model.SearchResult;
import org.folio.search.repository.ConsortiumInstitutionRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class ConsortiumInstitutionService {

  private static final Set<String> VALID_SORT_BY = Set.of("id", "name", "tenantId");
  private final ConsortiumInstitutionRepository repository;
  private final ConsortiumTenantExecutor executor;

  public SearchResult<ConsortiumInstitution> fetchInstitutions(String tenantHeader,
                                                               String tenantId,
                                                               Integer limit,
                                                               Integer offset,
                                                               String sortBy,
                                                               SortOrder sortOrder) {
    log.info("fetching consortium institution for tenant: {}, tenantId: {}, sortBy: {}",
      tenantHeader,
      tenantId,
      sortBy);

    validateSortByValue(sortBy);

    return executor.execute(
      tenantHeader,
      () -> repository.fetchInstitutions(tenantHeader, tenantId, limit, offset, sortBy, sortOrder));
  }

  private void validateSortByValue(String sortBy) {
    if (!VALID_SORT_BY.contains(sortBy)) {
      throw new IllegalArgumentException("Invalid sortBy value: " + sortBy);
    }
  }

}
