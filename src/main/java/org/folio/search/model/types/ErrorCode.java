package org.folio.search.model.types;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  UNKNOWN_ERROR("unknown_error"),
  SERVICE_ERROR("service_error"),
  ELASTICSEARCH_ERROR("elasticsearch_error"),
  VALIDATION_ERROR("validation_error"),
  NOT_FOUND_ERROR("not_found_error"),
  CONSTRAINT_VIOLATION("constraint_violation_error"),
  INTEGRATION_ERROR("integration-error");

  @JsonValue
  private final String value;
}
