package org.folio.search.model.service;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.folio.search.domain.dto.CqlSearchRequest;
import org.folio.search.model.ResourceRequest;

/**
 * CQL based search request model.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CqlSearchServiceRequest extends CqlSearchRequest implements ResourceRequest {

  /**
   * Resource name.
   */
  private String resource;

  /**
   * Request tenant id.
   */
  private String tenantId;
}
