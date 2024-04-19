package org.folio.search.service.consortium;

import org.folio.search.domain.dto.ConsortiumLocation;
import org.folio.search.domain.dto.SortOrder;
import org.folio.search.model.SearchResult;
import org.folio.search.repository.ConsortiumLocationRepository;
import org.folio.spring.testing.type.UnitTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
public class ConsortiumLocationServiceTest {

  public static final String ID = "id";
  public static final String LOCATION_NAME = "location name";
  public static final String CONSORTIUM_TENANT = "consortium";
  public static final String NAME = "name";
  @Mock
  private ConsortiumLocationRepository repository;

  @InjectMocks
  private  ConsortiumLocationService service;

  @Test
  void fetchLocations_ValidSortBy() {
    var tenantHeader = CONSORTIUM_TENANT;
    var tenantId = CONSORTIUM_TENANT;
    var sortOrder = SortOrder.ASC;
    var sortBy = NAME;
    var limit = 10;
    var offset = 0;
    var searchResult = prepareSearchResult();

    when(repository.fetchLocations(tenantHeader, tenantId, limit, offset, sortBy, sortOrder))
      .thenReturn(searchResult);

    var actual = service.fetchLocations(tenantHeader, tenantId, limit, offset, sortBy, sortOrder);

    assertThat(actual).isNotNull();
    assertThat(actual.getRecords()).hasSize(1);
    assertThat(actual.getRecords().get(0).getTenantId()).isEqualTo(CONSORTIUM_TENANT);
    assertThat(actual.getRecords().get(0).getName()).isEqualTo(LOCATION_NAME);
    assertThat(actual.getRecords().get(0).getId()).isEqualTo(ID);
    verify(repository).fetchLocations(tenantHeader, tenantId, limit, offset, sortBy, sortOrder);
  }

  @NotNull
  private static SearchResult<ConsortiumLocation> prepareSearchResult() {
    var location = new ConsortiumLocation();
    location.setId(ID);
    location.setName(LOCATION_NAME);
    location.setTenantId(CONSORTIUM_TENANT);

    var searchResult = new SearchResult<ConsortiumLocation>();
    searchResult.records(List.of(location));
    return searchResult;
  }
}
