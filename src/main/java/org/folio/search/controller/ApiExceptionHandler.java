package org.folio.search.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.Optional;
import javax.validation.ConstraintViolationException;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.index.Index;
import org.folio.search.domain.dto.Error;
import org.folio.search.domain.dto.ErrorResponse;
import org.folio.search.domain.dto.Parameter;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.types.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    log.debug("Handling exception [type: SearchServiceException]", exception);
    var defaultErrorResponse = new ErrorResponse()
      .addErrorsItem(new Error()
        .message(exception.getMessage())
        .type(SearchServiceException.class.getSimpleName())
        .code(ErrorCode.SERVICE_ERROR.getDescription()))
      .totalRecords(1);

    return ResponseEntity.status(BAD_REQUEST).body(defaultErrorResponse);
  }

  /**
   * Catches and handles all {@link SearchOperationException} objects during code execution.
   *
   * @param exception {@link SearchOperationException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(SearchOperationException.class)
  public ResponseEntity<ErrorResponse> handleSearchServiceException(SearchOperationException exception) {
    log.warn("Handling exception [type: SearchOperationException]", exception);

    var cause = exception.getCause();
    if (cause instanceof ElasticsearchException) {
      return ResponseEntity.badRequest().body(buildErrorResponse((ElasticsearchException) cause));
    }

    return ResponseEntity.badRequest().body(new ErrorResponse()
      .addErrorsItem(new Error()
        .message(exception.getMessage())
        .type(SearchOperationException.class.getSimpleName())
        .code(ErrorCode.UNKNOWN_ERROR.getDescription()))
      .totalRecords(1));
  }

  /**
   * Catches and handles all exceptions of type {@link MethodArgumentNotValidException}.
   *
   * @param e {@link MethodArgumentNotValidException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
    log.debug("handling exception [type: MethodArgumentNotValidException]", e);
    var errorResponse = new ErrorResponse();
    e.getBindingResult().getAllErrors().forEach(error ->
      errorResponse.addErrorsItem(new Error()
        .message(error.getDefaultMessage())
        .code(ErrorCode.VALIDATION_ERROR.getDescription())
        .type(MethodArgumentNotValidException.class.getSimpleName())
        .addParametersItem(new Parameter()
          .key(((FieldError) error).getField())
          .value(String.valueOf(((FieldError) error).getRejectedValue())))));
    errorResponse.totalRecords(errorResponse.getErrors().size());

    return ResponseEntity.badRequest().body(errorResponse);
  }

  /**
   * Catches and handles all exceptions of type {@link ConstraintViolationException}.
   *
   * @param e {@link ConstraintViolationException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
    log.debug("Handling exception [type: ConstraintViolationException]", e);
    var errorResponse = new ErrorResponse();
    e.getConstraintViolations().forEach(constraintViolation ->
      errorResponse.addErrorsItem(new Error()
        .message(constraintViolation.getMessage())
        .code(ErrorCode.VALIDATION_ERROR.getDescription())
        .type(ConstraintViolationException.class.getSimpleName())));
    errorResponse.totalRecords(errorResponse.getErrors().size());
    return ResponseEntity.badRequest().body(errorResponse);
  }

  /**
   * Handles all uncaught exceptions.
   *
   * @param exception {@link Exception} object
   * @return {@link ResponseEntity} with {@link ErrorResponse} body.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleAllOtherExceptions(Exception exception) {
    log.error("Handling exception [type: {}]", exception.getClass().getSimpleName(), exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse()
      .addErrorsItem(new Error()
        .message(exception.getMessage())
        .code(ErrorCode.UNKNOWN_ERROR.getDescription())
        .type(exception.getClass().getSimpleName()))
      .totalRecords(1));
  }

  private static ErrorResponse buildErrorResponse(ElasticsearchException e) {
    var message = e.getMessage();
    var indexName = Optional.ofNullable(e.getIndex()).map(Index::getName).orElse(null);
    if (StringUtils.contains(message, "index_not_found")) {
      return buildErrorResponse("Index not found: " + indexName);
    }
    if (StringUtils.contains(message, "resource_already_exists")) {
      return buildErrorResponse("Index already exists: " + indexName);
    }
    return buildErrorResponse(e.getMessage());
  }

  private static ErrorResponse buildErrorResponse(String message) {
    return new ErrorResponse()
      .addErrorsItem(new Error().message(message)
        .type(ElasticsearchException.class.getSimpleName())
        .code(ErrorCode.ELASTICSEARCH_ERROR.getDescription()))
      .totalRecords(1);
  }
}
