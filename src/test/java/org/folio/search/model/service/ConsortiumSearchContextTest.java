package org.folio.search.model.service;

import static org.folio.search.model.service.ConsortiumSearchContext.FILTER_REQUIRED_MSG;
import static org.folio.search.model.service.ConsortiumSearchContext.SORT_NOT_ALLOWED_MSG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.folio.search.domain.dto.SortOrder;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.Pair;
import org.folio.search.model.types.ResourceType;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ConsortiumSearchContextTest {

  @Test
  public void testBuilder_success() {
    ConsortiumSearchContext consContext = ConsortiumSearchContext.builderFor(ResourceType.HOLDINGS)
      .filter("name", "value")
      .limit(10)
      .offset(1)
      .sortBy("id")
      .sortOrder(SortOrder.DESC)
      .build();

    assertEquals(ResourceType.HOLDINGS, consContext.getResourceType());
    assertEquals(List.of(Pair.pair("name", "value")), consContext.getFilters());
    assertEquals(10, consContext.getLimit());
    assertEquals(1, consContext.getOffset());
    assertEquals("id", consContext.getSortBy());
    assertEquals(SortOrder.DESC, consContext.getSortOrder());
  }

  @Test
  public void testBuilder_error_filterIsRequired() {
    var searchContextBuilder = ConsortiumSearchContext.builderFor(ResourceType.HOLDINGS).sortBy("id");
    Exception exception = assertThrows(RequestValidationException.class, searchContextBuilder::build);
    assertEquals(FILTER_REQUIRED_MSG, exception.getMessage());
  }

  @Test
  public void testBuilder_error_sortNotAllowed() {
    var searchContextBuilder = ConsortiumSearchContext.builderFor(ResourceType.HOLDINGS)
      .filter("name", "value")
      .sortBy("wrongField");
    Exception exception = assertThrows(RequestValidationException.class, searchContextBuilder::build);
    assertEquals(SORT_NOT_ALLOWED_MSG.formatted(ResourceType.HOLDINGS.getValue()), exception.getMessage());
  }
}
