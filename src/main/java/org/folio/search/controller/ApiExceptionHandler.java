package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.apache.logging.log4j.Level.DEBUG;
import static org.apache.logging.log4j.Level.WARN;
import static org.folio.search.model.types.ErrorCode.ELASTICSEARCH_ERROR;
import static org.folio.search.model.types.ErrorCode.UNKNOWN_ERROR;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import java.util.List;
import java.util.Optional;
import javax.validation.ConstraintViolationException;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.index.Index;
import org.folio.search.domain.dto.Error;
import org.folio.search.domain.dto.ErrorResponse;
import org.folio.search.domain.dto.Parameter;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.exception.ValidationException;
import org.folio.search.model.types.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Log4j2
@RestControllerAdvice
public class ApiExceptionHandler {

  /**
   * Catches and handles all {@link SearchServiceException} objects during code execution.
   *
   * @param exception {@link SearchServiceException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(SearchServiceException.class)
  public ResponseEntity<ErrorResponse> handleSearchServiceException(SearchServiceException exception) {
    logException(DEBUG, exception);
    return buildResponseEntity(exception, BAD_REQUEST, exception.getErrorCode());
  }

  /**
   * Catches and handles all {@link SearchOperationException} objects during code execution.
   *
   * @param exception {@link SearchOperationException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(SearchOperationException.class)
  public ResponseEntity<ErrorResponse> handleSearchServiceException(SearchOperationException exception) {
    logException(DEBUG, exception);

    var cause = exception.getCause();
    return cause instanceof ElasticsearchException
      ? handleElasticsearchException((ElasticsearchException) cause)
      : buildResponseEntity(exception, INTERNAL_SERVER_ERROR, exception.getErrorCode());
  }

  /**
   * Catches and handles all exceptions of type {@link MethodArgumentNotValidException}.
   *
   * @param e {@link MethodArgumentNotValidException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
    var validationErrors = Optional.of(e.getBindingResult()).map(Errors::getAllErrors).orElse(emptyList());
    var errorResponse = new ErrorResponse();
    validationErrors.forEach(error ->
      errorResponse.addErrorsItem(new Error()
        .message(error.getDefaultMessage())
        .code(ErrorCode.VALIDATION_ERROR.getValue())
        .type(MethodArgumentNotValidException.class.getSimpleName())
        .addParametersItem(new Parameter()
          .key(((FieldError) error).getField())
          .value(String.valueOf(((FieldError) error).getRejectedValue())))));
    errorResponse.totalRecords(errorResponse.getErrors().size());

    return buildResponseEntity(errorResponse, BAD_REQUEST);
  }

  /**
   * Catches and handles all exceptions of type {@link ConstraintViolationException}.
   *
   * @param exception {@link ConstraintViolationException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
    logException(DEBUG, exception);
    var errorResponse = new ErrorResponse();
    exception.getConstraintViolations().forEach(constraintViolation ->
      errorResponse.addErrorsItem(new Error()
        .message(constraintViolation.getMessage())
        .code(ErrorCode.VALIDATION_ERROR.getValue())
        .type(ConstraintViolationException.class.getSimpleName())));
    errorResponse.totalRecords(errorResponse.getErrors().size());

    return buildResponseEntity(errorResponse, BAD_REQUEST);
  }

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(ValidationException exception) {
    var error = new Error()
      .message(exception.getMessage())
      .parameters(List.of(new Parameter().key(exception.getKey()).value(exception.getValue())));
    var errorResponse = new ErrorResponse().errors(List.of(error)).totalRecords(1);
    return buildResponseEntity(errorResponse, UNPROCESSABLE_ENTITY);
  }

  /**
   * Handles all uncaught exceptions.
   *
   * @param exception {@link Exception} object
   * @return {@link ResponseEntity} with {@link ErrorResponse} body.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleAllOtherExceptions(Exception exception) {
    logException(WARN, exception);
    return buildResponseEntity(exception, INTERNAL_SERVER_ERROR, UNKNOWN_ERROR);
  }

  private static ResponseEntity<ErrorResponse> handleElasticsearchException(ElasticsearchException exception) {
    var message = exception.getMessage();
    var indexName = Optional.ofNullable(exception.getIndex()).map(Index::getName).orElse(null);
    if (StringUtils.contains(message, "index_not_found")) {
      logException(DEBUG, exception);
      return buildResponseEntity(buildErrorResponse("Index not found: " + indexName), BAD_REQUEST);
    }
    if (StringUtils.contains(message, "resource_already_exists")) {
      logException(DEBUG, exception);
      return buildResponseEntity(buildErrorResponse("Index already exists: " + indexName), BAD_REQUEST);
    }
    logException(WARN, exception);
    return buildResponseEntity(exception, INTERNAL_SERVER_ERROR, ELASTICSEARCH_ERROR);
  }

  private static ResponseEntity<ErrorResponse> buildResponseEntity(Exception e, HttpStatus status, ErrorCode code) {
    var errorResponse = new ErrorResponse()
      .errors(List.of(new Error()
        .message(e.getMessage())
        .type(e.getClass().getSimpleName())
        .code(code.getValue())))
      .totalRecords(1);
    return buildResponseEntity(errorResponse, status);
  }

  private static ResponseEntity<ErrorResponse> buildResponseEntity(ErrorResponse errorResponse, HttpStatus status) {
    return ResponseEntity.status(status).body(errorResponse);
  }

  private static ErrorResponse buildErrorResponse(String message) {
    return new ErrorResponse()
      .addErrorsItem(new Error().message(message)
        .type(ElasticsearchException.class.getSimpleName())
        .code(ELASTICSEARCH_ERROR.getValue()))
      .totalRecords(1);
  }

  private static void logException(Level logLevel, Exception exception) {
    log.log(logLevel, "Handling exception", exception);
  }
}
