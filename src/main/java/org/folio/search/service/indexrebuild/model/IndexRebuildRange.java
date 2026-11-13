package org.folio.search.service.indexrebuild.model;

import java.util.UUID;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;

/**
 * A range of records processed as one unit.
 *
 * @param scopeTenantId tenant a single tenant rerun of a combined index is limited to, null otherwise
 */
public record IndexRebuildRange(UUID id, UUID traceId, IndexRebuildTarget target, String tenantId, String lower,
                                String upper, IndexRebuildRangeStatus status, String scopeTenantId) { }
