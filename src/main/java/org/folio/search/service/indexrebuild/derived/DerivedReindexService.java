package org.folio.search.service.indexrebuild.derived;

import static org.folio.search.configuration.SearchCacheNames.USER_TENANTS_CACHE;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.IndexRebuildStatusItem;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.exception.ReindexException;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.event.IndexRebuildRangeEvent;
import org.folio.search.service.IndexService;
import org.folio.search.service.indexrebuild.IndexRebuildRangeIds;
import org.folio.search.service.indexrebuild.IndexRebuildReadinessService;
import org.folio.search.service.indexrebuild.IndexRebuildScope;
import org.folio.search.service.indexrebuild.IndexRebuildScopeResolver;
import org.folio.search.service.indexrebuild.IndexRebuildStateService;
import org.folio.search.service.indexrebuild.IndexRebuildStatusReporter;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildRangeRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.folio.search.service.indexrebuild.model.IndexRebuildRange;
import org.folio.search.service.indexrebuild.model.IndexRebuildRangeStatus;
import org.folio.search.service.indexrebuild.model.IndexRebuildRunMode;
import org.folio.search.service.reindex.RangeGenerator;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

/**
 * Rebuilds a derived index from its source index, which has to be up to date first.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class DerivedReindexService {

  private final DerivedRebuildStrategies strategies;
  private final IndexRebuildScopeResolver scopeResolver;
  private final IndexRebuildReadinessService readinessService;
  private final IndexRebuildStateService stateService;
  private final IndexRebuildStateRepository stateRepository;
  private final IndexRebuildRangeRepository rangeRepository;
  private final IndexRebuildStatusReporter statusReporter;
  private final DerivedFinalizer finalizer;
  private final DerivedIndexReader reader;
  private final IndexService indexService;
  private final ReindexConfigurationProperties reindexConfig;
  private final FolioMessageProducer<IndexRebuildRangeEvent> rangeEventProducer;
  @Qualifier("reindexFullExecutor")
  private final ExecutorService reindexExecutor;

  /**
   * Starts a run for all tenants, or recomputes documents of a single tenant.
   *
   * @param target         index to rebuild, must be derived from another index
   * @param tenantId       requesting tenant
   * @param targetTenantId tenant to recompute, null to rebuild the whole index
   * @param indexSettings  settings of the recreated index, optional
   * @return future that completes when requests for all ranges are sent
   * @throws RequestValidationException if the run can't be started
   * @throws ReindexException           if the index can't be prepared
   */
  @CacheEvict(cacheNames = USER_TENANTS_CACHE, allEntries = true, beforeInvocation = true)
  public CompletableFuture<Void> start(IndexRebuildTarget target, String tenantId, String targetTenantId,
                                       IndexSettings indexSettings) {
    log.info("start:: starting derived index reindex [resource: {}, tenant: {}, targetTenant: {}]",
      target.getResource(), tenantId, targetTenantId);
    var strategy = strategies.get(target);
    strategy.validateEnabled();
    var scope = scopeResolver.resolve(tenantId, targetTenantId);
    readinessService.validateCanStart(target, scope);
    stateService.start(target, scope.tenants(), IndexRebuildRunMode.WHOLE_INDEX);
    prepare(strategy, tenantId, scope, indexSettings);

    log.info("start:: derived index reindex started [resource: {}, tenants: {}]", target.getResource(),
      scope.tenants());
    return CompletableFuture.runAsync(() -> createAndSendRanges(target, tenantId, scope), reindexExecutor)
      .handle(failOnError(target));
  }

  /**
   * Resumes a failed or interrupted run.
   */
  public CompletableFuture<Void> retryFailed(IndexRebuildTarget target, String tenantId) {
    log.info("retryFailed:: retrying derived index reindex [resource: {}, tenant: {}]", target.getResource(),
      tenantId);
    strategies.get(target);
    readinessService.validateCanResume(target, scopeResolver.resolve(tenantId, null).tenants());
    if (!stateRepository.prepareAllRetry(target)) {
      log.info("retryFailed:: no failed or interrupted run [resource: {}]", target.getResource());
      return CompletableFuture.completedFuture(null);
    }
    rangeRepository.resetAllFailed(target);
    var ranges = rangeRepository.findAllUnfinished(target);
    if (ranges.isEmpty()) {
      log.info("retryFailed:: no unfinished ranges, finalizing [resource: {}]", target.getResource());
      finalizer.tryFinalize(target, tenantId);
      return CompletableFuture.completedFuture(null);
    }
    log.info("retryFailed:: sending unfinished ranges again [resource: {}, ranges: {}]", target.getResource(),
      ranges.size());
    return CompletableFuture.runAsync(() -> send(ranges), reindexExecutor).handle(failOnError(target));
  }

  public List<IndexRebuildStatusItem> getStatus(IndexRebuildTarget target, String tenantId) {
    return statusReporter.getStatus(target, scopeResolver.resolve(tenantId, null).tenants());
  }

  private void prepare(DerivedRebuildStrategy strategy, String tenantId, IndexRebuildScope scope,
                       IndexSettings indexSettings) {
    var target = strategy.target();
    try {
      reader.refresh(strategy.source(), tenantId);
      if (scope.wholeIndex()) {
        indexService.dropIndex(target.getResourceType(), tenantId);
        var response = indexService.createIndex(target.getResourceType(), tenantId, indexSettings);
        if (response.getStatus() == FolioCreateIndexResponse.StatusEnum.ERROR) {
          throw new ReindexException(response.getErrorMessage());
        }
        log.info("prepare:: index recreated [resource: {}, tenant: {}]", target.getResource(), tenantId);
      } else {
        log.info("prepare:: keeping the index for a single tenant run [resource: {}, tenant: {}]",
          target.getResource(), scope.tenants().getFirst());
      }
    } catch (RuntimeException e) {
      stateRepository.markAllFailed(target, e.getMessage());
      throw new ReindexException("Failed to prepare the %s index: %s".formatted(target.getResource(), e.getMessage()),
        e);
    }
  }

  private void createAndSendRanges(IndexRebuildTarget target, String tenantId, IndexRebuildScope scope) {
    // a single tenant can be as large as the whole index, ranges outside its keys are cheap
    var level = reindexConfig.getUploadRangeLevel();
    var scopeTenantId = scope.wholeIndex() ? null : scope.tenants().getFirst();
    var traceId = UUID.randomUUID();
    var ranges = RangeGenerator.createHexRanges(level).stream()
      .map(range -> new IndexRebuildRange(IndexRebuildRangeIds.newId(), traceId, target, tenantId, range.lowerBound(),
        range.upperBound(), IndexRebuildRangeStatus.PENDING, scopeTenantId))
      .toList();
    rangeRepository.saveAll(ranges);
    log.info("createAndSendRanges:: ranges saved, sending [resource: {}, ranges: {}, scopeTenant: {}, traceId: {}]",
      target.getResource(), ranges.size(), scopeTenantId, traceId);
    send(ranges);
  }

  private void send(List<IndexRebuildRange> ranges) {
    rangeEventProducer.sendMessages(ranges.stream().map(range -> {
      var event = new IndexRebuildRangeEvent();
      event.setId(range.id());
      event.setResource(range.target().getResource());
      return event;
    }).toList());
  }

  private BiFunction<Void, Throwable, Void> failOnError(IndexRebuildTarget target) {
    return (unused, throwable) -> {
      if (throwable != null) {
        log.error(new FormattedMessage("failOnError:: reindex failed [resource: {}]", target.getResource()),
          throwable);
        stateRepository.markAllFailed(target, throwable.getMessage());
      }
      return unused;
    };
  }
}
