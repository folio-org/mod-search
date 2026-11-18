package org.folio.search.service.indexrebuild;

import static org.folio.search.utils.SearchUtils.TENANT_ID_FIELD_NAME;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.service.indexrebuild.jdbc.DeletedRecordRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildRangeRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.folio.search.service.indexrebuild.model.IndexRebuildRunMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class IndexRebuildStateService {

  private final IndexRebuildStateRepository stateRepository;
  private final IndexRebuildRangeRepository rangeRepository;
  private final DeletedRecordRepository deletedRecordRepository;

  /**
   * Starts a run for the tenants and removes what the previous run left. Either all tenants are started or none.
   *
   * @param mode {@link IndexRebuildRunMode#PER_TENANT} rejects the start only if a run of one of the tenants is active
   *             and removes ranges of these tenants, {@link IndexRebuildRunMode#WHOLE_INDEX} rejects it if any run of
   *             the target is active and removes all its ranges
   * @throws RequestValidationException if a conflicting run is already active
   */
  @Transactional
  public void start(IndexRebuildTarget target, List<String> tenants, IndexRebuildRunMode mode) {
    if (mode == IndexRebuildRunMode.WHOLE_INDEX && stateRepository.isAnyActive(target)) {
      log.warn("start:: rejected, a run is already active [resource: {}]", target.getResource());
      throw alreadyInProgress(target, "resource", target.getResource());
    }
    for (var tenant : tenants) {
      if (!stateRepository.claim(target, tenant)) {
        log.warn("start:: rejected, a run of the tenant is already active [resource: {}, tenant: {}]",
          target.getResource(), tenant);
        throw alreadyInProgress(target, TENANT_ID_FIELD_NAME, tenant);
      }
      if (mode == IndexRebuildRunMode.PER_TENANT) {
        rangeRepository.delete(target, tenant);
      }
      if (target.isBuiltFromRecords()) {
        // deletes tracked for the previous run,
        // cleared in the claiming transaction
        // so none of the new run's deletes are lost
        deletedRecordRepository.deleteAll(target, tenant);
      }
    }
    if (mode == IndexRebuildRunMode.WHOLE_INDEX) {
      rangeRepository.deleteAll(target);
    }
    log.info("start:: run claimed [resource: {}, mode: {}, tenants: {}]", target.getResource(), mode, tenants);
  }

  public void markFailed(IndexRebuildTarget target, List<String> tenants, String failCause) {
    log.warn("markFailed:: run failed [resource: {}, tenants: {}, cause: {}]", target.getResource(), tenants,
      failCause);
    tenants.forEach(tenant -> stateRepository.markFailed(target, tenant, failCause));
  }

  private static RequestValidationException alreadyInProgress(IndexRebuildTarget target, String key, String value) {
    return new RequestValidationException("Reindex is already in progress for: %s"
      .formatted(target.getResource()), key, value);
  }
}
