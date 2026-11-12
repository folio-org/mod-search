package org.folio.search.service.indexrebuild.records;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.search.service.indexrebuild.IndexFingerprintService;
import org.folio.search.service.indexrebuild.jdbc.DeletedRecordRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.springframework.stereotype.Component;

/**
 * Completes the run of a tenant once all its ranges succeeded. Records deleted during the run are removed last, because
 * only then no older copy of them can be written anymore. The index is refreshed before the run is completed, so a
 * completed run is searchable.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class RecordsFinalizer {

  private final IndexRebuildStateRepository stateRepository;
  private final DeletedRecordRepository deletedRecordRepository;
  private final RecordsIndexWriter indexWriter;
  private final IndexFingerprintService fingerprintService;

  public void tryFinalize(RecordsRebuildStrategy strategy, String tenantId) {
    var target = strategy.target();
    if (!stateRepository.tryStartFinalizing(target, tenantId)) {
      log.debug("tryFinalize:: not finalizing, ranges remain or another caller finalizes [resource: {}, tenant: {}]",
        target.getResource(), tenantId);
      return;
    }
    try {
      var deletedIds = deletedRecordRepository.findIds(target, tenantId);
      log.info("tryFinalize:: finalizing, removing records deleted during the run [resource: {}, tenant: {}, "
               + "deleted: {}]", target.getResource(), tenantId, deletedIds.size());
      indexWriter.delete(strategy, tenantId, deletedIds);
      indexWriter.refresh(strategy, tenantId);
      stateRepository.complete(target, tenantId, fingerprintService.current(target));
      deletedRecordRepository.deleteAll(target, tenantId);
      log.info("tryFinalize:: {} index rebuilt [tenant: {}]", target.getResource(), tenantId);
    } catch (RuntimeException e) {
      log.warn(new FormattedMessage("tryFinalize:: failed to finalize {} [tenant: {}]", target.getResource(), tenantId),
        e);
      stateRepository.markFailed(target, tenantId, e.getMessage());
    }
  }
}
