package org.folio.search.utils;

import static java.util.stream.Collectors.joining;

import java.util.Objects;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.folio.search.exception.IndexingFailureException;
import org.opensearch.OpenSearchStatusException;
import org.springframework.core.retry.RetryException;

/**
 * Extracts structured details (message, HTTP status, OpenSearch error type) from indexing failures.
 */
@UtilityClass
public class IndexingFailureUtils {

  private static final Pattern ERROR_TYPE_PATTERN = Pattern.compile("type=([^,\\]\\s]+)");
  private static final String CAUSE_SEPARATOR = "; caused by: ";

  /**
   * Builds failure message from the whole exception chain, so both wrapper context (index, operation) and the
   * underlying error are kept. Consecutive duplicate messages and retry framework wrappers are skipped.
   *
   * @param throwable failure
   * @return failure message
   */
  public static String getFullMessage(Throwable throwable) {
    return ExceptionUtils.getThrowableList(throwable).stream()
      .filter(t -> !(t instanceof RetryException))
      .map(t -> t.getClass().getSimpleName() + ": " + t.getMessage())
      .distinct()
      .collect(joining(CAUSE_SEPARATOR));
  }

  /**
   * Finds HTTP status of the failure in the exception chain.
   *
   * @param throwable failure
   * @return HTTP status or {@code null} if unknown
   */
  public static Integer getStatus(Throwable throwable) {
    var indexingFailure = ExceptionUtils.throwableOfType(throwable, IndexingFailureException.class);
    if (indexingFailure != null && indexingFailure.getStatus() != null) {
      return indexingFailure.getStatus();
    }
    var statusException = ExceptionUtils.throwableOfType(throwable, OpenSearchStatusException.class);
    return statusException != null ? statusException.status().getStatus() : null;
  }

  /**
   * Finds OpenSearch error type (e.g. {@code mapper_parsing_exception}) of the failure in the exception chain.
   *
   * @param throwable failure
   * @return error type or {@code null} if unknown
   */
  public static String getErrorType(Throwable throwable) {
    var indexingFailure = ExceptionUtils.throwableOfType(throwable, IndexingFailureException.class);
    if (indexingFailure != null && indexingFailure.getErrorType() != null) {
      return indexingFailure.getErrorType();
    }
    var statusException = ExceptionUtils.throwableOfType(throwable, OpenSearchStatusException.class);
    return statusException != null ? parseErrorType(statusException.getMessage()) : null;
  }

  /**
   * Extracts error type from OpenSearch exception message, e.g.
   * {@code OpenSearch exception [type=mapper_parsing_exception, reason=...]}.
   *
   * @param message exception message
   * @return error type or {@code null} if not found
   */
  public static String parseErrorType(String message) {
    if (message == null) {
      return null;
    }
    var matcher = ERROR_TYPE_PATTERN.matcher(message);
    return matcher.find() ? matcher.group(1) : null;
  }

  /**
   * Checks if the failure is worth retrying: client errors (4xx) except 429 are not.
   *
   * @param throwable failure
   * @return {@code true} if retryable
   */
  public static boolean isRetryable(Throwable throwable) {
    return IndexingFailureException.isRetryableStatus(getStatus(throwable));
  }

  /**
   * Builds message for a failed document from the whole cause chain of the OpenSearch failure (keeps nested
   * {@code caused_by} details).
   *
   * @param cause OpenSearch failure cause
   * @return message
   */
  public static String getCauseChainMessage(Throwable cause) {
    return ExceptionUtils.getThrowableList(cause).stream()
      .map(Throwable::getMessage)
      .filter(Objects::nonNull)
      .distinct()
      .collect(joining(CAUSE_SEPARATOR));
  }
}
