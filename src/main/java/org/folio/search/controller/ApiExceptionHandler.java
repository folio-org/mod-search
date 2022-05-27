package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.apache.logging.log4j.Level.DEBUG;
import static org.apache.logging.log4j.Level.WARN;
import static org.folio.search.model.types.ErrorCode.ELASTICSEARCH_ERROR;
import static org.folio.search.model.types.ErrorCode.NOT_FOUND_ERROR;
import static org.folio.search.model.types.ErrorCode.SERVICE_ERROR;
import static org.folio.search.model.types.ErrorCode.UNKNOWN_ERROR;
import static org.folio.search.model.types.ErrorCode.VALIDATION_ERROR;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import java.util.List;
import java.util.Optional;
import javax.persistence.EntityNotFoundException;
import javax.validation.ConstraintViolationException;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.opensearch.OpenSearchException;
import org.opensearch.index.Index;
import org.folio.search.domain.dto.Error;
import org.folio.search.domain.dto.ErrorResponse;
import org.folio.search.domain.dto.Parameter;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.exception.ValidationException;
import org.folio.search.model.types.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
   * Catches and handles all {@link UnsupportedOperationException} objects during code execution.
   *
   * @param exception {@link UnsupportedOperationException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(UnsupportedOperationException.class)
  public ResponseEntity<ErrorResponse> handleUnsupportedOperationException(UnsupportedOperationException exception) {
    logException(DEBUG, exception);
    return buildResponseEntity(exception, BAD_REQUEST, SERVICE_ERROR);
  }

  /**
   * Catches and handles all {@link SearchOperationException} objects during code execution.
   *
   * @param exception {@link SearchOperationException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(SearchOperationException.class)
  public ResponseEntity<ErrorResponse> handleSearchOperationException(SearchOperationException exception) {
    logException(DEBUG, exception);

    var cause = exception.getCause();
    return cause instanceof OpenSearchException
      ? handleOpenSearchException((OpenSearchException) cause)
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
        .message(String.format("%s %s", constraintViolation.getPropertyPath(), constraintViolation.getMessage()))
        .code(ErrorCode.VALIDATION_ERROR.getValue())
        .type(ConstraintViolationException.class.getSimpleName())));
    errorResponse.totalRecords(errorResponse.getErrors().size());

    return buildResponseEntity(errorResponse, BAD_REQUEST);
  }

  /**
   * Catches and handles all exceptions for type {@link ValidationException}.
   *
   * @param exception {@link ValidationException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(ValidationException exception) {
    var errorResponse = buildValidationError(exception, exception.getKey(), exception.getValue());
    return buildResponseEntity(errorResponse, UNPROCESSABLE_ENTITY);
  }

  /**
   * Catches and handles all exceptions for type {@link RequestValidationException}.
   *
   * @param exception {@link RequestValidationException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(RequestValidationException.class)
  public ResponseEntity<ErrorResponse> handleRequestValidationException(RequestValidationException exception) {
    var errorResponse = buildValidationError(exception, exception.getKey(), exception.getValue());
    return buildResponseEntity(errorResponse, BAD_REQUEST);
  }

  /**
   * Catches and handles all exceptions for type {@link MethodArgumentTypeMismatchException}.
   *
   * @param exception {@link MethodArgumentTypeMismatchException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
    MethodArgumentTypeMismatchException exception) {
    return buildResponseEntity(exception, BAD_REQUEST, VALIDATION_ERROR);
  }

  /**
   * Catches and handles all {@link IllegalArgumentException} exceptions.
   *
   * @param exception {@link IllegalArgumentException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException exception) {
    logException(DEBUG, exception);
    return buildResponseEntity(exception, BAD_REQUEST, VALIDATION_ERROR);
  }

  /**
   * Handles all {@link EntityNotFoundException} exceptions.
   *
   * @param exception {@link EntityNotFoundException} object
   * @return {@link ResponseEntity} with {@link ErrorResponse} body.
   */
  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleEntityNotFoundException(EntityNotFoundException exception) {
    logException(DEBUG, exception);
    return buildResponseEntity(exception, NOT_FOUND, NOT_FOUND_ERROR);
  }

  /**
   * Handles all {@link HttpMediaTypeNotSupportedException} exceptions.
   *
   * @param e {@link HttpMediaTypeNotSupportedException} object
   * @return {@link ResponseEntity} with {@link ErrorResponse} body.
   */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
    logException(DEBUG, e);
    return buildResponseEntity(e, BAD_REQUEST, VALIDATION_ERROR);
  }

  /**
   * Handles all {@link HttpMessageNotReadableException} exceptions.
   *
   * @param e {@link HttpMessageNotReadableException} object
   * @return {@link ResponseEntity} with {@link ErrorResponse} body.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handlerHttpMessageNotReadableException(HttpMessageNotReadableException e) {
    return Optional.ofNullable(e.getCause())
      .map(Throwable::getCause)
      .filter(IllegalArgumentException.class::isInstance)
      .map(IllegalArgumentException.class::cast)
      .map(this::handleIllegalArgumentException)
      .orElseGet(() -> {
        logException(DEBUG, e);
        return buildResponseEntity(e, BAD_REQUEST, VALIDATION_ERROR);
      });
  }

  /**
   * Catches and handles all {@link MissingServletRequestParameterException} exceptions.
   *
   * @param exception {@link MissingServletRequestParameterException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
    MissingServletRequestParameterException exception) {
    logException(DEBUG, exception);
    return buildResponseEntity(exception, BAD_REQUEST, VALIDATION_ERROR);
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

  private static ResponseEntity<ErrorResponse> handleOpenSearchException(OpenSearchException exception) {
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

  private static ErrorResponse buildValidationError(Exception exception, String key, String value) {
    var error = new Error()
      .type(exception.getClass().getSimpleName())
      .code(VALIDATION_ERROR.getValue())
      .message(exception.getMessage())
      .parameters(List.of(new Parameter().key(key).value(value)));
    return new ErrorResponse().errors(List.of(error)).totalRecords(1);
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
        .type(OpenSearchException.class.getSimpleName())
        .code(ELASTICSEARCH_ERROR.getValue()))
      .totalRecords(1);
  }

  private static void logException(Level logLevel, Exception exception) {
    log.log(logLevel, "Handling exception", exception);
  }
}
