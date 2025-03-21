package org.folio.search.cql.searchterm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.folio.search.service.lccn.StringNormalizer;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class NoSpaceSearchTermProcessorTest {
  @Mock
  private StringNormalizer normalizer;
  @InjectMocks
  private NoSpaceSearchTermProcessor noSpaceSearchTermProcessor;

  @Test
  void getSearchTerm_positive() {
    // given
    var searchTerm = " N 123456 ";
    var normalizedTerm = "n123456";
    when(normalizer.apply(searchTerm)).thenReturn(Optional.of(normalizedTerm));

    // when
    var actual = noSpaceSearchTermProcessor.getSearchTerm(searchTerm);

    // then
    assertThat(actual).isEqualTo(normalizedTerm);
  }
}
