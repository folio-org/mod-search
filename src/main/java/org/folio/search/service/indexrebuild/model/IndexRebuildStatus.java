package org.folio.search.service.indexrebuild.model;

/**
 * State of an index rebuild for a tenant.
 */
public enum IndexRebuildStatus {

  IN_PROGRESS,
  FINALIZING,
  COMPLETED,
  FAILED
}
