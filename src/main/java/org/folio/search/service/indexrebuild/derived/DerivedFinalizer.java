package org.folio.search.service.indexrebuild.derived;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.service.indexrebuild.IndexFingerprintService;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.springframework.stereotype.Component;

/**
 * Completes a run of a derived index once all its ranges succeeded. The run covers the whole index, so all tenants
 * complete together. The index is refreshed first, so a completed run is searchable.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class DerivedFinalizer {

  private final IndexRebuildStateRepository stateRepository;
  private final IndexFingerprintService fingerprintService;
  private final DerivedIndexReader reader;

  /**
   * Completes the run if all its ranges succeeded and no other caller completes it.
   *
   * @param tenantId tenant the index name is resolved for
   */
  public void tryFinalize(IndexRebuildTarget target, String tenantId) {
    if (!stateRepository.tryStartAllFinalizing(target)) {
      log.debug("tryFinalize:: not finalizing, ranges remain or another caller finalizes [resource: {}]",
        target.getResource());
      return;
    }
    reader.refresh(target, tenantId);
    stateRepository.completeAll(target, fingerprintService.current(target));
    log.info("tryFinalize:: {} index rebuilt", target.getResource());
  }
}
