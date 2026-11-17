package org.folio.search.service.reindex;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.service.reindex.jdbc.ReindexJobRepository;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the identifier of the reindex job initiated by this mod-search instance for the current tenant.
 *
 * <p>The identifier is persisted in the instance's own tenant schema, so it is shared by all pods of the same
 * instance and is isolated from other mod-search instances (e.g. a v1/v2 side-by-side deployment) and from other
 * tenants. Starting a new full reindex replaces the previous identifier, which makes events of earlier jobs
 * unmatched from that point on.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ReindexJobService {

  private final ReindexJobRepository jobRepository;
  private final FolioExecutionContext context;

  @Transactional
  public UUID startNewJob() {
    var jobId = UUID.randomUUID();
    jobRepository.replaceActiveJob(jobId);
    log.info("startNewJob:: registered reindex job [tenant: {}, reindexJobId: {}]", context.getTenantId(), jobId);
    return jobId;
  }

  public Optional<UUID> getActiveJobId() {
    return jobRepository.findActiveJobId();
  }

  /**
   * Returns the active job identifier, registering a new job if none exists yet
   * (e.g. an upload-only reindex on a tenant that never ran a full reindex with job tracking).
   */
  @Transactional
  public UUID getOrStartJob() {
    return getActiveJobId().orElseGet(this::startNewJob);
  }
}
