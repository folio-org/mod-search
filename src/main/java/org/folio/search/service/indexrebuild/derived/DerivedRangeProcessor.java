package org.folio.search.service.indexrebuild.derived;

import static org.folio.search.configuration.RetryTemplateConfiguration.ITEM_INDEXING_RETRY_TEMPLATE_NAME;

import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.search.model.event.IndexRebuildRangeEvent;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildRangeRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.folio.search.service.indexrebuild.model.IndexRebuildRange;
import org.folio.search.service.indexrebuild.model.IndexRebuildRangeStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Builds documents of a range of keys of a derived index. Events of an unknown or already finished range are ignored. A
 * range is retried before the run is marked as failed, because the index was already dropped and a failed run needs a
 * manual retry.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class DerivedRangeProcessor {

  private final DerivedRebuildStrategies strategies;
  private final IndexRebuildRangeRepository rangeRepository;
  private final IndexRebuildStateRepository stateRepository;
  private final DerivedIndexReader reader;
  private final DerivedDocumentWriter documentWriter;
  private final DerivedFinalizer finalizer;
  @Qualifier(ITEM_INDEXING_RETRY_TEMPLATE_NAME)
  private final RetryTemplate retryTemplate;

  public void process(IndexRebuildRangeEvent event) {
    var target = IndexRebuildTarget.fromResource(event.getResource());
    var strategy = strategies.get(target);
    var range = rangeRepository.findById(target, event.getId()).orElse(null);
    if (range == null || range.status() == IndexRebuildRangeStatus.SUCCESS) {
      log.info("process:: ignoring unknown or finished range [resource: {}, rangeId: {}]", target.getResource(),
        event.getId());
      return;
    }
    try {
      retryTemplate.invoke(() -> rebuild(strategy, range));
      rangeRepository.markSuccess(range.id());
      log.debug("process:: range processed [resource: {}, rangeId: {}]", target.getResource(), range.id());
      finalizer.tryFinalize(target, range.tenantId());
    } catch (RuntimeException e) {
      log.warn(new FormattedMessage("process:: failed to process range [resource: {}, rangeId: {}]",
        target.getResource(), event.getId()), e);
      rangeRepository.markFailed(range.id(), e.getMessage());
      stateRepository.markAllFailed(target, e.getMessage());
    }
  }

  private void rebuild(DerivedRebuildStrategy strategy, IndexRebuildRange range) {
    var keys = findKeys(strategy, range);
    log.debug("rebuild:: rebuilding range [resource: {}, rangeId: {}, lower: {}, upper: {}, scopeTenant: {}, "
              + "keys: {}]", strategy.target().getResource(), range.id(), range.lower(), range.upper(),
      range.scopeTenantId(), keys.size());
    documentWriter.rebuild(strategy, range.tenantId(), keys);
  }

  private Set<String> findKeys(DerivedRebuildStrategy strategy, IndexRebuildRange range) {
    var keys = new HashSet<>(
      reader.findKeys(strategy, range.tenantId(), range.lower(), range.upper(), range.scopeTenantId()));
    if (range.scopeTenantId() != null) {
      keys.addAll(reader.findDocumentKeysOfTenant(strategy, range.tenantId(), range.lower(), range.upper(),
        range.scopeTenantId()));
    }
    return keys;
  }
}
