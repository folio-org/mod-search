package org.folio.search.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class SearchServiceExceptionTest {

  @Test
  void constructor_positive_message() {
    var error = new SearchServiceException("error");
    assertThat(error).isNotNull();
  }

  @Test
  void constructor_positive_messageAndCause() {
    var error = new SearchServiceException("error", new Exception("error"));
    assertThat(error).isNotNull();
  }
}