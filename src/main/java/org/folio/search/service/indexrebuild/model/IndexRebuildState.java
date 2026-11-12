package org.folio.search.service.indexrebuild.model;

import java.sql.Timestamp;

/**
 * The latest known rebuild state of an index for a tenant. No history of previous runs is kept.
 */
public record IndexRebuildState(String tenantId, IndexRebuildStatus status, String fingerprint, Timestamp startedAt,
                                Timestamp finishedAt, String failCause) { }
