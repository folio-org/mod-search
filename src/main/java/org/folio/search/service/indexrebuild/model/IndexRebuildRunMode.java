package org.folio.search.service.indexrebuild.model;

/**
 * How runs of an index are divided between tenants.
 */
public enum IndexRebuildRunMode {

  /**
   * Each tenant has its own run, so runs of different tenants can be active at the same time. Used for indexes whose
   * documents belong to a single tenant.
   */
  PER_TENANT,

  /**
   * One run covers the whole index, so only one run can be active. Used for indexes whose documents combine records of
   * several tenants.
   */
  WHOLE_INDEX
}
