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
    assertThat(actual).isEqualTo("1861972717");
  }

  @Test
  void getSearchTerm_withTrailingWildcard() {
    var searchTerm = "9781609383657*";
    when(isbnProcessor.normalizeIsbn("9781609383657")).thenReturn(List.of("9781609383657"));
    var actual = isbnSearchTermProcessor.getSearchTerm(searchTerm);
    assertThat(actual).isEqualTo("9781609383657*");
  }

  @Test
  void getSearchTerm_withTrailingWildcard_isbn10() {
    var searchTerm = "047144250X*";
    when(isbnProcessor.normalizeIsbn("047144250X")).thenReturn(List.of("047144250x", "9780471442509"));
    var actual = isbnSearchTermProcessor.getSearchTerm(searchTerm);
    // Only the first normalized value is used, so wildcard is applied to it
    assertThat(actual).isEqualTo("047144250x*");
  }

  @Test
  void getSearchTerm_validIsbn10_convertsToIsbn13() {
    // Test case for valid ISBN-10 that normalizes to both ISBN-10 and ISBN-13
    var searchTerm = "0262012103";
    when(isbnProcessor.normalizeIsbn(searchTerm)).thenReturn(List.of("0262012103", "9780262012102"));
    var actual = isbnSearchTermProcessor.getSearchTerm(searchTerm);
    // Should return only the first normalized value
    assertThat(actual).isEqualTo("0262012103");
  }

  @Test
  void getSearchTerm_validIsbn10WithWildcard_convertsToIsbn13() {
    // Test case for valid ISBN-10 with wildcard that normalizes to both ISBN-10 and ISBN-13
    var searchTerm = "0262012103*";
    when(isbnProcessor.normalizeIsbn("0262012103")).thenReturn(List.of("0262012103", "9780262012102"));
    var actual = isbnSearchTermProcessor.getSearchTerm(searchTerm);
    // Should return only the first normalized value with wildcard
    assertThat(actual).isEqualTo("0262012103*");
  }
}
