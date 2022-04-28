package org.folio.search.service.browse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.folio.search.cql.EffectiveShelvingOrderTermProcessor;
import org.folio.search.service.setter.item.ItemCallNumberProcessor;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CallNumberTermConverterTest {

  @InjectMocks private CallNumberTermConverter searchTermProcessor;
  @Mock private ItemCallNumberProcessor itemCallNumberProcessor;
  @Mock private EffectiveShelvingOrderTermProcessor effectiveShelvingOrderTermProcessor;

  @Test
  void convert_positive() {
    var term = "value";
    var numericValue = 100L;
    when(effectiveShelvingOrderTermProcessor.getSearchTerm(term)).thenReturn(term);
    when(itemCallNumberProcessor.getCallNumberAsLong(term)).thenReturn(numericValue);

    var actual = searchTermProcessor.convert(term);

    assertThat(actual).isEqualTo(numericValue);
  }
}
