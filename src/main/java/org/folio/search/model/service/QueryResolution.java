package org.folio.search.model.service;

/**
 * Carries the resolved index name and path type for a given query version and tenant.
 *
 * @param indexName the index name (alias for FLAT, legacy index name for LEGACY)
 * @param pathType  the execution path type
 */
public record QueryResolution(String indexName, PathType pathType) {

  public enum PathType {
    LEGACY,
    FLAT
  }
}
