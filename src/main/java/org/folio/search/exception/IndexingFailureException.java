package org.folio.search.exception;

import lombok.Getter;
import org.folio.search.model.types.ErrorCode;

/**
 * Thrown when OpenSearch rejects documents during indexing. Keeps the failure HTTP status and OpenSearch error type
 * so the failure can be classified (retryable or not) and stored with structured details.
 */
@Getter
public class IndexingFailureException extends BaseSearchException {

  private static final int TOO_MANY_REQUESTS = 429;

  private final Integer status;
  private final String errorType;

  /**
   * Creates exception instance.
   *
   * @param message   failure message
   * @param status    HTTP status of the failure, nullable
   * @param errorType OpenSearch error type, e.g. {@code mapper_parsing_exception}, nullable
   */
  public IndexingFailureException(String message, Integer status, String errorType) {
    super(message, ErrorCode.ELASTICSEARCH_ERROR);
    this.status = status;
    this.errorType = errorType;
  }

  /**
   * Checks if the failure is worth retrying. Client errors (4xx) except {@code 429 Too Many Requests} will fail the
   * same way on every attempt.
   *
   * @return {@code true} if the failure is retryable
   */
  public boolean isRetryable() {
    return isRetryableStatus(status);
  }

  public static boolean isRetryableStatus(Integer status) {
    return status == null || status == TOO_MANY_REQUESTS || status < 400 || status >= 500;
  }
}
