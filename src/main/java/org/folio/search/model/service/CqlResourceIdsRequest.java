package org.folio.search.model.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.search.model.ResourceRequest;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class CqlResourceIdsRequest implements ResourceRequest {

  /**
   * CQL query.
   */
  private String query;

  /**
   * Resource name.
   */
  private String resource;

  /**
   * Request tenant id.
   */
  private String tenantId;
}
