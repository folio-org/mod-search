package org.folio.search.service.consortium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.folio.search.domain.dto.ConsortiumLibrary;
import org.folio.search.domain.dto.SortOrder;
import org.folio.search.model.SearchResult;
import org.folio.search.repository.ConsortiumLibraryRepository;
import org.folio.spring.testing.type.UnitTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
public class ConsortiumLibraryServiceTest {

  public static final String ID = UUID.randomUUID().toString();
  public static final String LIBRARY_NAME = "library name";
  public static final String CONSORTIUM_TENANT = "consortium";

  public static final String SORT_BY_ID = "id";
  public static final String SORT_BT_NAME = "name";
  public static final String SORT_BY_TENANT_ID = "tenantId";

  @Mock
  private ConsortiumLibraryRepository repository;
  @Mock
  private ConsortiumTenantExecutor executor;

  @InjectMocks
  private ConsortiumLibraryService service;

  @ParameterizedTest
  @SuppressWarnings("unchecked")
  @ValueSource(strings = {SORT_BY_ID, SORT_BT_NAME, SORT_BY_TENANT_ID})
  void fetchLibraries_ValidSortBy(String sortBy) {
    var tenantHeader = CONSORTIUM_TENANT;
    var tenantId = CONSORTIUM_TENANT;
    var sortOrder = SortOrder.ASC;
    var limit = 10;
    var offset = 0;
    var searchResult = prepareSearchResult();

    when(repository.fetchLibraries(tenantHeader, tenantId, limit, offset, sortBy, sortOrder))
      .thenReturn(searchResult);
    when(executor.execute(eq(tenantId), any(Supplier.class)))
      .thenAnswer(invocation -> ((Supplier<ConsortiumLibrary>) invocation.getArgument(1)).get());

    var actual = service.fetchLibraries(tenantHeader, tenantId, limit, offset, sortBy, sortOrder);

    assertThat(actual).isEqualTo(searchResult);
    verify(repository).fetchLibraries(tenantHeader, tenantId, limit, offset, sortBy, sortOrder);
    verify(executor).execute(eq(tenantId), any(Supplier.class));
  }

  @Test
  void fetchLibraries_InvalidSortBy() {
    var sortOrder = SortOrder.ASC;
    var limit = 10;
    var offset = 0;

    Assertions.assertThrows(IllegalArgumentException.class, () ->
      service.fetchLibraries(CONSORTIUM_TENANT, CONSORTIUM_TENANT, limit, offset, "invalid", sortOrder)
    );
  }

  @NotNull
  private static SearchResult<ConsortiumLibrary> prepareSearchResult() {
    var library = new ConsortiumLibrary()
      .id(ID)
      .name(LIBRARY_NAME)
      .tenantId(CONSORTIUM_TENANT)
      .campusId(ID);

    var searchResult = new SearchResult<ConsortiumLibrary>();
    searchResult.records(List.of(library));

    return searchResult;
  }
}
