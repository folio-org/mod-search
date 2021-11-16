package org.folio.search.model.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.ResourceRequest;

@Data
@RequiredArgsConstructor(staticName = "of")
public class CqlResourceIdsRequest implements ResourceRequest {

  /**
   * Resource name.
   */
  private final String resource;

  /**
   * Request tenant id.
   */
  private final String tenantId;

  /**
   * A CQL query string with search conditions.
   */
  private final String query;

  /**
   * Field path to the id field of the document.
   */
  private final String sourceFieldPath;
}
