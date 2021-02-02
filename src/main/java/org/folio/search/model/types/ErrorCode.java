package org.folio.search.model.types;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  UNKNOWN_ERROR("Unknown error"),
  SERVICE_ERROR("Service error"),
  ELASTICSEARCH_ERROR("Elasticsearch error"),
  VALIDATION_ERROR("Validation error"),
  CONSTRAINT_VIOLATION("Constraint violation error");

  private final String description;
}
