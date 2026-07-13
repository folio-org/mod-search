package org.folio.search.service.setter.linkeddata.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.folio.search.domain.dto.LinkedDataAuthority;
import org.folio.search.domain.dto.LinkedDataIdentifier;
import org.folio.search.service.lccn.StringNormalizer;
import org.folio.search.service.setter.instance.IsbnProcessor;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class LinkedDataAuthorityIdentifierProcessorTest {

  @Mock
  private IsbnProcessor isbnProcessor;
  @Mock
  private StringNormalizer stringNormalizer;
  @InjectMocks
  private LinkedDataAuthorityIdentifierProcessor processor;

  @Test
  void getFieldValue_positive_returnsEmptySet_whenIdentifiersAreNull() {
    var actual = processor.getFieldValue(new LinkedDataAuthority());

    assertThat(actual).isEmpty();
    verifyNoInteractions(isbnProcessor, stringNormalizer);
  }

  @Test
  void getFieldValue_positive_returnsEmptySet_whenIdentifiersAreEmpty() {
    var authority = new LinkedDataAuthority().identifiers(List.of());

    var actual = processor.getFieldValue(authority);

    assertThat(actual).isEmpty();
    verifyNoInteractions(isbnProcessor, stringNormalizer);
  }

  @Test
  void getFieldValue_positive_filtersOutIdentifiersWithNullValue() {
    var authority = new LinkedDataAuthority()
      .identifiers(List.of(new LinkedDataIdentifier(null, "LCCN")));

    var actual = processor.getFieldValue(authority);

    assertThat(actual).isEmpty();
    verifyNoInteractions(isbnProcessor, stringNormalizer);
  }

  @Test
  void getFieldValue_positive_normalizesIsbnIdentifiersUsingIsbnProcessor() {
    var authority = new LinkedDataAuthority()
      .identifiers(List.of(new LinkedDataIdentifier("9781234567897", "ISBN")));
    lenient().when(isbnProcessor.normalizeIsbn("9781234567897")).thenReturn(List.of("9781234567897"));

    var actual = processor.getFieldValue(authority);

    assertThat(actual).isEqualTo(Set.of("9781234567897"));
    verifyNoInteractions(stringNormalizer);
  }

  @Test
  void getFieldValue_positive_normalizesNonIsbnIdentifiersUsingStringNormalizer() {
    var authority = new LinkedDataAuthority()
      .identifiers(List.of(new LinkedDataIdentifier("n79021425", "LCCN")));
    lenient().when(stringNormalizer.apply(eq("n79021425"))).thenReturn(Optional.of("n79021425"));

    var actual = processor.getFieldValue(authority);

    assertThat(actual).isEqualTo(Set.of("n79021425"));
    verifyNoInteractions(isbnProcessor);
  }
}
