package org.folio.search.service.indexrebuild.records;

import static org.folio.search.configuration.SearchCacheNames.USER_TENANTS_CACHE;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.IndexRebuildStatusItem;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.exception.ReindexException;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.IndexService;
import org.folio.search.service.indexrebuild.IndexRebuildReadinessService;
import org.folio.search.service.indexrebuild.IndexRebuildScope;
import org.folio.search.service.indexrebuild.IndexRebuildScopeResolver;
import org.folio.search.service.indexrebuild.IndexRebuildStateService;
import org.folio.search.service.indexrebuild.IndexRebuildStatusReporter;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildRangeRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.folio.search.service.indexrebuild.model.IndexRebuildRange;
import org.folio.search.service.indexrebuild.model.IndexRebuildRunMode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

/**
 * Rebuilds an index from inventory records. Records go straight to OpenSearch, no record data is stored locally.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class RecordsReindexService {

  private final RecordsRebuildStrategies strategies;
  private final IndexRebuildScopeResolver scopeResolver;
  private final IndexRebuildReadinessService readinessService;
  private final IndexRebuildStateService stateService;
  private final IndexRebuildStateRepository stateRepository;
  private final IndexRebuildRangeRepository rangeRepository;
  private final IndexRebuildStatusReporter statusReporter;
  private final RecordsRangePublisher rangePublisher;
  private final RecordsFinalizer finalizer;
  private final IndexService indexService;
  private final PrimaryResourceRepository resourceRepository;
  @Qualifier("reindexFullExecutor")
  private final ExecutorService reindexExecutor;

  /**
   * Starts a run for all tenants, or reloads documents of a single tenant.
   *
   * @param target         index to rebuild, must be rebuilt from records
   * @param tenantId       requesting tenant
   * @param targetTenantId tenant to reload, null to rebuild the whole index
   * @param indexSettings  settings of the recreated index, optional
   * @return future that completes when inventory accepted all requests for records
   * @throws RequestValidationException if the run can't be started
   * @throws ReindexException           if the index can't be prepared
   */
  @CacheEvict(cacheNames = USER_TENANTS_CACHE, allEntries = true, beforeInvocation = true)
  public CompletableFuture<Void> start(IndexRebuildTarget target, String tenantId, String targetTenantId,
                                       IndexSettings indexSettings) {
    log.info("start:: starting records reindex [resource: {}, tenant: {}, targetTenant: {}]", target.getResource(),
      tenantId, targetTenantId);
    var strategy = strategies.get(target);
    strategy.validateEnabled();
    var scope = scopeResolver.resolve(tenantId, targetTenantId);
    readinessService.validateCanStart(target, scope);
    stateService.start(target, scope.tenants(), IndexRebuildRunMode.PER_TENANT);
    prepareIndex(target, tenantId, scope, indexSettings);

    log.info("start:: records reindex started [resource: {}, tenants: {}]", target.getResource(), scope.tenants());
    return CompletableFuture.runAsync(() -> createAndPublishRanges(strategy, scope.tenants()), reindexExecutor)
      .handle(failOnError(target, scope.tenants()));
  }

  /**
   * Resumes failed and interrupted runs: requests ranges that didn't succeed and completes tenants with none left.
   */
  public CompletableFuture<Void> retryFailed(IndexRebuildTarget target, String tenantId) {
    log.info("retryFailed:: retrying records reindex [resource: {}, tenant: {}]", target.getResource(), tenantId);
    var strategy = strategies.get(target);
    strategy.validateEnabled();
    var tenants = scopeResolver.resolve(tenantId, null).tenants();
    readinessService.validateCanResume(target, tenants);
    var retried = new ArrayList<String>();
    var ranges = new ArrayList<IndexRebuildRange>();
    for (var tenant : tenants) {
      var unfinished = prepareRetry(strategy, tenant);
      if (!unfinished.isEmpty()) {
        retried.add(tenant);
        ranges.addAll(unfinished);
      }
    }
    log.info("retryFailed:: requesting unfinished ranges again [resource: {}, tenants: {}, ranges: {}]",
      target.getResource(), retried, ranges.size());
    return CompletableFuture.runAsync(() -> rangePublisher.publish(strategy, ranges), reindexExecutor)
      .handle(failOnError(target, retried));
  }

  public List<IndexRebuildStatusItem> getStatus(IndexRebuildTarget target, String tenantId) {
    return statusReporter.getStatus(target, scopeResolver.resolve(tenantId, null).tenants());
  }

  /**
   * Prepares a failed or interrupted run of a tenant for a retry, and completes it if no ranges are left.
   *
   * @return ranges to request again, empty if there is no run to retry or it was completed
   */
  private List<IndexRebuildRange> prepareRetry(RecordsRebuildStrategy strategy, String tenant) {
    var target = strategy.target();
    if (!stateRepository.prepareRetry(target, tenant)) {
      log.debug("prepareRetry:: no failed or interrupted run [resource: {}, tenant: {}]", target.getResource(), tenant);
      return List.of();
    }
    rangeRepository.resetFailed(target, tenant);
    var unfinished = rangeRepository.findUnfinished(target, tenant);
    if (unfinished.isEmpty()) {
      log.info("prepareRetry:: no unfinished ranges, finalizing [resource: {}, tenant: {}]", target.getResource(),
        tenant);
      finalizer.tryFinalize(strategy, tenant);
    }
    return unfinished;
  }

  private void prepareIndex(IndexRebuildTarget target, String tenantId, IndexRebuildScope scope,
                            IndexSettings indexSettings) {
    try {
      if (scope.wholeIndex()) {
        recreateIndex(target, tenantId, indexSettings);
        log.info("prepareIndex:: index recreated [resource: {}, tenant: {}]", target.getResource(), tenantId);
      } else {
        deleteTenantDocuments(target, scope.tenants().getFirst());
        log.info("prepareIndex:: documents of the tenant deleted [resource: {}, tenant: {}]", target.getResource(),
          scope.tenants().getFirst());
      }
    } catch (RuntimeException e) {
      stateService.markFailed(target, scope.tenants(), e.getMessage());
      throw new ReindexException("Failed to prepare the %s index: %s".formatted(target.getResource(), e.getMessage()),
        e);
    }
  }

  private void recreateIndex(IndexRebuildTarget target, String tenantId, IndexSettings indexSettings) {
    indexService.dropIndex(target.getResourceType(), tenantId);
    var response = indexService.createIndex(target.getResourceType(), tenantId, indexSettings);
    if (response.getStatus() == FolioCreateIndexResponse.StatusEnum.ERROR) {
      throw new ReindexException(response.getErrorMessage());
    }
  }

  private void deleteTenantDocuments(IndexRebuildTarget target, String tenant) {
    var response = resourceRepository.deleteResourceByTenantId(target.getResourceType(), tenant);
    if (response.getStatus() == FolioIndexOperationResponse.StatusEnum.ERROR) {
      throw new ReindexException(response.getErrorMessage());
    }
  }

  private void createAndPublishRanges(RecordsRebuildStrategy strategy, List<String> tenants) {
    var ranges = tenants.stream().map(tenant -> rangePublisher.createRanges(strategy, tenant))
      .flatMap(List::stream)
      .toList();
    rangeRepository.saveAll(ranges);
    log.info("createAndPublishRanges:: ranges saved, requesting records [resource: {}, tenants: {}, ranges: {}]",
      strategy.target().getResource(), tenants, ranges.size());
    rangePublisher.publish(strategy, ranges);
    log.info("createAndPublishRanges:: all ranges requested [resource: {}, ranges: {}]",
      strategy.target().getResource(), ranges.size());
  }

  private BiFunction<Void, Throwable, Void> failOnError(IndexRebuildTarget target, List<String> tenants) {
    return (unused, throwable) -> {
      if (throwable != null) {
        log.error(new FormattedMessage("failOnError:: reindex failed [resource: {}, tenants: {}]",
          target.getResource(), tenants), throwable);
        stateService.markFailed(target, tenants, throwable.getMessage());
      }
      return unused;
    };
  }
}
