package org.folio.search.controller;

import static org.springframework.http.ResponseEntity.status;

import org.folio.search.domain.dto.ValidationError;
import org.folio.search.domain.dto.ValidationErrors;
import org.folio.search.exception.ValidationException;
import org.folio.tenant.domain.dto.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErrorHandler {
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ValidationErrors> handleValidationException(ValidationException ex) {
    final ValidationError error = new ValidationError()
      .message(ex.getMessage())
      .parameters(new Parameter().key(ex.getKey()).value(ex.getValue()));

    return status(422).body(new ValidationErrors().addErrorsItem(error));
  }
}
