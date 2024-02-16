package org.folio.search.cql;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;


@UnitTest
@ExtendWith(MockitoExtension.class)
class LccnSearchTermProcessorTest {

  @Test
  void getSearchTerm_positive() {
    var searchTerm = " N 123456 ";
    var lccnSearchTermProcessor = new LccnSearchTermProcessor();

    var actual = lccnSearchTermProcessor.getSearchTerm(searchTerm);
    assertThat(actual).isEqualTo("n123456");
  }
}
