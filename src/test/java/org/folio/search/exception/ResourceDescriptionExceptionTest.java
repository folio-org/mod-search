package org.folio.search.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ResourceDescriptionExceptionTest {

  @Test
  void constructor_positive_message() {
    var error = new ResourceDescriptionException("error");
    assertThat(error).isNotNull();
  }

  @Test
  void constructor_positive_messageAndCause() {
    var error = new ResourceDescriptionException("error", new Exception("error"));
    assertThat(error).isNotNull();
  }
}