package org.folio.search.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import org.folio.search.exception.IndexingFailureException;
import org.folio.search.exception.SearchOperationException;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.springframework.core.retry.RetryException;

@UnitTest
class IndexingFailureUtilsTest {

  private static final String MAPPING_ERROR =
    "OpenSearch exception [type=mapper_parsing_exception, reason=failed to parse field [statusName]]";

  @Test
  void getFullMessage_positive_keepsWrapperContextAndRootCause() {
    var failure = new SearchOperationException(
      "Failed to perform elasticsearch request [index=item_diku, type=bulkApi, message: Connection refused]",
      new ConnectException("Connection refused"));

    var message = IndexingFailureUtils.getFullMessage(new RetryException("Retry policy exhausted", failure));

    assertThat(message)
      .isEqualTo("SearchOperationException: Failed to perform elasticsearch request [index=item_diku, type=bulkApi, "
                 + "message: Connection refused]; caused by: ConnectException: Connection refused");
  }

  @Test
  void getStatusAndErrorType_positive_fromIndexingFailureException() {
    var failure = new RetryException("Retry policy exhausted",
      new IndexingFailureException("mapping error", 400, "mapper_parsing_exception"));

    assertThat(IndexingFailureUtils.getStatus(failure)).isEqualTo(400);
    assertThat(IndexingFailureUtils.getErrorType(failure)).isEqualTo("mapper_parsing_exception");
    assertThat(IndexingFailureUtils.isRetryable(failure)).isFalse();
  }

  @Test
  void getStatusAndErrorType_positive_fromOpenSearchStatusException() {
    var failure = new SearchOperationException("Failed to perform elasticsearch request",
      new OpenSearchStatusException(MAPPING_ERROR, RestStatus.BAD_REQUEST));

    assertThat(IndexingFailureUtils.getStatus(failure)).isEqualTo(400);
    assertThat(IndexingFailureUtils.getErrorType(failure)).isEqualTo("mapper_parsing_exception");
  }

  @Test
  void getStatusAndErrorType_positive_unknownForConnectionFailure() {
    var failure = new SearchOperationException("Failed to perform elasticsearch request",
      new ConnectException("Connection refused"));

    assertThat(IndexingFailureUtils.getStatus(failure)).isNull();
    assertThat(IndexingFailureUtils.getErrorType(failure)).isNull();
    assertThat(IndexingFailureUtils.isRetryable(failure)).isTrue();
  }

  @Test
  void parseErrorType_positive_noTypeInMessage() {
    assertThat(IndexingFailureUtils.parseErrorType("something went wrong")).isNull();
    assertThat(IndexingFailureUtils.parseErrorType(null)).isNull();
  }

  @Test
  void getCauseChainMessage_positive_keepsNestedCauses() {
    var cause = new OpenSearchStatusException(MAPPING_ERROR, RestStatus.BAD_REQUEST,
      new IllegalArgumentException("Can't parse [keyword] value"));

    assertThat(IndexingFailureUtils.getCauseChainMessage(cause))
      .isEqualTo(MAPPING_ERROR + "; caused by: Can't parse [keyword] value");
  }

  @ParameterizedTest
  @CsvSource({"400,false", "404,false", "409,false", "429,true", "500,true", "503,true"})
  void isRetryable_parameterized(int status, boolean expected) {
    var failure = new IndexingFailureException("failure", status, null);

    assertThat(IndexingFailureUtils.isRetryable(failure)).isEqualTo(expected);
  }
}
