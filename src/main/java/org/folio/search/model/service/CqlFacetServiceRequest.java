package org.folio.search.model.service;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.folio.search.domain.dto.CqlFacetRequest;
import org.folio.search.model.ResourceRequest;

/**
 * CQL based facet request model.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CqlFacetServiceRequest extends CqlFacetRequest implements ResourceRequest {

  /**
   * Resource name.
   */
  private String resource;

  /**
   * Request tenant id.
   */
  private String tenantId;
}
