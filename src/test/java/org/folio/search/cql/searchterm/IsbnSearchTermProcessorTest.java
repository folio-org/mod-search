package org.folio.search.cql.searchterm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.search.service.setter.instance.IsbnProcessor;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IsbnSearchTermProcessorTest {

  @InjectMocks
  private IsbnSearchTermProcessor isbnSearchTermProcessor;
  @Mock
  private IsbnProcessor isbnProcessor;

  @Test
  void getSearchTerm_positive() {
    var searchTerm = "123";
    when(isbnProcessor.normalizeIsbn(searchTerm)).thenReturn(List.of(searchTerm));
    var actual = isbnSearchTermProcessor.getSearchTerm(searchTerm);
    assertThat(actual).isEqualTo(searchTerm);
  }

  @Test
  void getSearchTerm_positive_multipleValues() {
    var searchTerm = "1 86197 271-7 (paper)";
    when(isbnProcessor.normalizeIsbn(searchTerm)).thenReturn(List.of("1861972717", "9781861972712", "(paper)"));
    var actual = isbnSearchTermProcessor.getSearchTerm(searchTerm);
    assertThat(actual).isEqualTo("1861972717 9781861972712 (paper)");
  }
}
