package org.folio.search.cql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.folio.search.service.setter.instance.OclcProcessor;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OclcSearchTermProcessorTest {

  @InjectMocks
  private OclcSearchTermProcessor oclcSearchTermProcessor;
  @Mock
  private OclcProcessor oclcProcessor;

  @Test
  void getSearchTerm_positive() {
    var searchTerm = "123*";
    when(oclcProcessor.normalizeOclc(searchTerm)).thenReturn(searchTerm);
    var actual = oclcSearchTermProcessor.getSearchTerm(searchTerm);
    assertThat(actual).isEqualTo(searchTerm);
  }

}
