package org.folio.search.service.setter.linkeddata.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.folio.search.domain.dto.LinkedDataIdentifier;
import org.folio.search.service.setter.instance.IsbnProcessor;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class LinkedDataIsbnProcessorTest {

  @Mock
  private IsbnProcessor isbnProcessor;
  @InjectMocks
  private LinkedDataIsbnProcessor processor;

  @Test
  void getFieldValue_positive_returnsEmptySet_whenIdentifiersAreNull() {
    var actual = processor.getFieldValue(null);

    assertThat(actual).isEmpty();
  }

  @Test
  void getFieldValue_positive_skipsNullIdentifiers() {
    when(isbnProcessor.normalizeIsbn("9781234567897")).thenReturn(List.of("9781234567897"));
    var identifiers = Arrays.asList(null, new LinkedDataIdentifier("9781234567897", "ISBN"));

    var actual = processor.getFieldValue(identifiers);

    assertThat(actual).isEqualTo(Set.of("9781234567897"));
  }

  @Test
  void getFieldValue_positive_ignoresNonIsbnIdentifiers() {
    var actual = processor.getFieldValue(List.of(new LinkedDataIdentifier("n79021425", "LCCN")));

    assertThat(actual).isEmpty();
  }
}
