package org.folio.search.cql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.folio.search.service.setter.instance.CallNumberProcessor;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CallNumberBrowseSearchTermProcessorTest {

  @InjectMocks private CallNumberBrowseSearchTermProcessor searchTermProcessor;
  @Mock private CallNumberProcessor callNumberProcessor;

  @Test
  void getSearchTerm_positive_validCallNumber() {
    when(callNumberProcessor.getCallNumberAsLong("A 11")).thenReturn(100L);
    var actual = searchTermProcessor.getSearchTerm("A 11");
    assertThat(actual).isEqualTo(100L);
  }
}
