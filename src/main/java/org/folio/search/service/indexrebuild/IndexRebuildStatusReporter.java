package org.folio.search.service.indexrebuild;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.IndexRebuildStatusItem;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildRangeRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.folio.search.service.indexrebuild.model.IndexRebuildState;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IndexRebuildStatusReporter {

  private static final String NOT_STARTED = "NOT_STARTED";

  private final IndexRebuildStateRepository stateRepository;
  private final IndexRebuildRangeRepository rangeRepository;
  private final IndexFingerprintService fingerprintService;
  private final IndexRebuildReadinessService readinessService;

  /**
   * Reports the latest state of the target for each of the tenants.
   */
  public List<IndexRebuildStatusItem> getStatus(IndexRebuildTarget target, List<String> tenants) {
    var states = stateRepository.findAll(target).stream()
      .collect(Collectors.toMap(IndexRebuildState::tenantId, Function.identity()));
    var counts = rangeRepository.countByTenant(target);
    var fingerprint = fingerprintService.current(target);
    return tenants.stream()
      .map(tenant -> toStatusItem(target, tenant, states.get(tenant), counts, fingerprint))
      .toList();
  }

  private IndexRebuildStatusItem toStatusItem(IndexRebuildTarget target, String tenant, IndexRebuildState state,
                                              Map<String, IndexRebuildRangeRepository.RangeCounts> counts,
                                              String fingerprint) {
    var blockedBy = readinessService.findBlockedBy(target.getDependsOn(), tenant).stream()
      .map(IndexRebuildTarget::getResource).toList();
    var item = new IndexRebuildStatusItem()
      .tenantId(tenant)
      .status(state == null ? NOT_STARTED : state.status().name())
      .reindexRequired(!readinessService.isReady(state, fingerprint))
      .blockedBy(blockedBy);
    var rangeCounts = counts.get(tenant);
    if (rangeCounts != null) {
      item.totalRanges(rangeCounts.total()).processedRanges(rangeCounts.processed());
    }
    if (state != null) {
      item.failCause(state.failCause())
        .startedAt(state.startedAt() == null ? null : state.startedAt().toInstant().toString())
        .finishedAt(state.finishedAt() == null ? null : state.finishedAt().toInstant().toString());
    }
    return item;
  }
}
