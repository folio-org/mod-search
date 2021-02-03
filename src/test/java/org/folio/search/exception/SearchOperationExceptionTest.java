package org.folio.search.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchOperationExceptionTest {

  @Test
  void constructor_positive_message() {
    var error = new SearchOperationException("error");
    assertThat(error).isNotNull();
  }

  @Test
  void constructor_positive_messageAndCause() {
    var error = new SearchOperationException("error", new Exception("error"));
    assertThat(error).isNotNull();
  }
}
