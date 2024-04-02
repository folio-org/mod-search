package org.folio.search.cql;

import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@ExtendWith(MockitoExtension.class)
public class NaturalIdSearchTermProcessorTest {

  @Test
  void getSearchTerm_positive() {
    var searchTerm = "N123456";
    var naturalIdSearchTermProcessor = new NaturalIdSearchTermProcessor();

    var actual = naturalIdSearchTermProcessor.getSearchTerm(searchTerm);
    assertThat(actual).isEqualTo("n123456");
  }
}

