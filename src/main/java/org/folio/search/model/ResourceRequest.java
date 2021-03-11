package org.folio.search.model;

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
  String getResource();
}
