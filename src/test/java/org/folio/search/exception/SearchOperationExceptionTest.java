package org.folio.search.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.search.model.types.ErrorCode;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class SearchOperationExceptionTest {

  @Test
  void constructor_positive_message() {
    var error = new SearchOperationException("error");
    assertThat(error).isNotNull();
    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ELASTICSEARCH_ERROR);
  }

  @Test
  void constructor_positive_messageAndCause() {
    var error = new SearchOperationException("error", new Exception("error"));
    assertThat(error).isNotNull();
    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ELASTICSEARCH_ERROR);
  }
}
