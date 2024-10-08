package org.folio.search.model;

import org.folio.search.model.types.ResourceType;

public interface ResourceRequest {

  /**
   * Returns tenant id as {@link String} object.
   *
   * @return tenant id
   */
  String getTenantId();

  /**
   * Returns resource name as {@link String} object.
   *
   * @return resource name
   */
  ResourceType getResource();
}
