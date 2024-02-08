package org.folio.search.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.search.model.types.ErrorCode;
import org.folio.spring.testing.type.UnitTest;
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
    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.SERVICE_ERROR);
  }
}
