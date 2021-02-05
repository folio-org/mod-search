package org.folio.search.model.types;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  UNKNOWN_ERROR("unknown_error"),
  SERVICE_ERROR("service_error"),
  ELASTICSEARCH_ERROR("elasticsearch_error"),
  VALIDATION_ERROR("validation_error"),
  CONSTRAINT_VIOLATION("constraint_violation_error");

  private final String description;
}
