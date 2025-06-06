package org.folio.search.service.consortium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.folio.search.domain.dto.ConsortiumLocation;
import org.folio.search.domain.dto.SortOrder;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.SearchResult;
import org.folio.search.repository.ConsortiumLocationRepository;
import org.folio.spring.testing.type.UnitTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ConsortiumLocationServiceTest {

  private static final String ID = UUID.randomUUID().toString();
  private static final String LOCATION_NAME = "location name";
  private static final String CONSORTIUM_TENANT = "consortium";
  private static final String NAME = "name";

  @Mock
  private ConsortiumLocationRepository repository;
  @Mock
  private ConsortiumTenantExecutor executor;

  @InjectMocks
  private ConsortiumLocationService service;

  @Test
  void fetchLocations_ValidSortBy() {
    var tenantHeader = CONSORTIUM_TENANT;
    var tenantId = CONSORTIUM_TENANT;
    var locationId = ID;
    var sortOrder = SortOrder.ASC;
    var sortBy = NAME;
    var limit = 10;
    var offset = 0;
    var searchResult = prepareSearchResult();

    when(repository.fetchLocations(tenantHeader, tenantId, locationId, limit, offset, sortBy, sortOrder))
      .thenReturn(searchResult);
    when(executor.execute(eq(tenantId), ArgumentMatchers.<Supplier<ConsortiumLocation>>any()))
      .thenAnswer(invocation -> invocation.<Supplier<ConsortiumLocation>>getArgument(1).get());

    var actual = service.fetchLocations(tenantHeader, tenantId, locationId, limit, offset, sortBy, sortOrder);

    assertThat(actual).isEqualTo(searchResult);
    verify(repository).fetchLocations(tenantHeader, tenantId, locationId, limit, offset, sortBy, sortOrder);
    verify(executor).execute(eq(tenantId), ArgumentMatchers.<Supplier<ConsortiumLocation>>any());
  }

  @Test
  void fetchLocations_InvalidSortBy() {
    var sortOrder = SortOrder.ASC;
    var limit = 10;
    var offset = 0;

    Assertions.assertThrows(RequestValidationException.class, () ->
      service.fetchLocations(CONSORTIUM_TENANT, CONSORTIUM_TENANT, ID, limit, offset, "invalid", sortOrder)
    );
  }

  @Test
  void fetchLocations_InvalidPaginationParameters() {
    var sortOrder = SortOrder.ASC;
    var limit = 1000;
    var offset = 9900;

    Assertions.assertThrows(RequestValidationException.class, () ->
      service.fetchLocations(CONSORTIUM_TENANT, CONSORTIUM_TENANT, ID, limit, offset, NAME, sortOrder)
    );
  }

  @NotNull
  private static SearchResult<ConsortiumLocation> prepareSearchResult() {
    var location = new ConsortiumLocation()
      .id(ID)
      .name(LOCATION_NAME)
      .tenantId(CONSORTIUM_TENANT)
      .description("desc")
      .discoveryDisplayName("display-name")
      .campusId(ID)
      .libraryId(ID)
      .institutionId(ID)
      .servicePointIds(List.of(UUID.fromString(ID)));

    var searchResult = new SearchResult<ConsortiumLocation>();
    searchResult.records(List.of(location));
    return searchResult;
  }
}
