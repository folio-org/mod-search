package org.folio.search.service.browse;

import static org.folio.search.utils.SearchUtils.performExceptionalOperation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.reindex.runtime.V2ReindexPhaseType;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.IndexFamilyService;
import org.folio.search.service.reindex.V2ReindexRuntimeStatusTracker;
import org.folio.spring.FolioExecutionContext;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class V2BrowseFullRebuildService {

  private final IndexFamilyService indexFamilyService;
  private final V2BrowseProjectionService browseProjectionService;
  private final RestHighLevelClient elasticsearchClient;
  private final IndexRepository indexRepository;
  private final Executor browseRebuildExecutor;
  private final FolioExecutionContext context;
  @Nullable
  private final V2ReindexRuntimeStatusTracker runtimeStatusTracker;

  public V2BrowseFullRebuildService(IndexFamilyService indexFamilyService,
                                    V2BrowseProjectionService browseProjectionService,
                                    RestHighLevelClient elasticsearchClient,
                                    IndexRepository indexRepository,
                                    @Qualifier("v2BrowseRebuildExecutor") Executor browseRebuildExecutor,
                                    FolioExecutionContext context,
                                    @Nullable V2ReindexRuntimeStatusTracker runtimeStatusTracker) {
    this.indexFamilyService = indexFamilyService;
    this.browseProjectionService = browseProjectionService;
    this.elasticsearchClient = elasticsearchClient;
    this.indexRepository = indexRepository;
    this.browseRebuildExecutor = browseRebuildExecutor;
    this.context = context;
    this.runtimeStatusTracker = runtimeStatusTracker;
  }

  public void rebuildBrowseAsync(UUID familyId) {
    browseRebuildExecutor.execute(() -> rebuildBrowse(familyId));
  }

  public void rebuildBrowse(UUID familyId) {
    var family = indexFamilyService.findById(familyId)
      .orElseThrow(() -> new RequestValidationException(
        "Index family not found", "familyId", familyId.toString()));

    var status = family.getStatus();
    if (status != IndexFamilyStatus.ACTIVE
        && status != IndexFamilyStatus.BUILDING
        && status != IndexFamilyStatus.STAGED) {
      throw new RequestValidationException(
        "Only ACTIVE, BUILDING or STAGED families can have browse rebuilt", "status", status.getValue());
    }

    var mainIndex = family.getIndexName();
    var browseIndices = indexFamilyService.getV2BrowsePhysicalIndexMap(context.getTenantId(), family.getGeneration());

    log.info("rebuildBrowse:: starting full browse rebuild [familyId: {}, mainIndex: {}]", familyId, mainIndex);
    var browseIndexNames = browseIndices.values().toArray(String[]::new);

    try {
      startBrowsePhaseIfTracked(familyId, browseIndices.size() + 1L,
        Map.of("mainIndex", mainIndex, "browseIndexCount", browseIndices.size()));
      var t0 = System.nanoTime();
      for (var entry : browseIndices.entrySet()) {
        deleteAllDocuments(entry.getValue());
        advanceBrowsePhaseIfTracked(familyId, 1L);
        log.info("rebuildBrowse:: cleared browse index [type: {}, index: {}]", entry.getKey(), entry.getValue());
      }
      var clearPhaseElapsedMs = (System.nanoTime() - t0) / 1_000_000;
      updateBrowsePhaseDetailsIfTracked(familyId, Map.of("clearPhaseElapsedMs", clearPhaseElapsedMs));
      log.info("rebuildBrowse:: clear phase done [elapsed: {}ms]", clearPhaseElapsedMs);

      disableRefreshInterval(browseIndexNames);
      t0 = System.nanoTime();
      browseProjectionService.rebuildFull(mainIndex, browseIndices);
      advanceBrowsePhaseIfTracked(familyId, 1L);
      var projectionPhaseElapsedMs = (System.nanoTime() - t0) / 1_000_000;
      updateBrowsePhaseDetailsIfTracked(familyId, Map.of("projectionPhaseElapsedMs", projectionPhaseElapsedMs));
      log.info("rebuildBrowse:: projection phase done [elapsed: {}ms]", projectionPhaseElapsedMs);
      completeBrowsePhaseIfTracked(familyId);
      log.info("rebuildBrowse:: completed [familyId: {}, mainIndex: {}]", familyId, mainIndex);
    } catch (Exception e) {
      failBrowsePhaseIfTracked(familyId, e.getMessage());
      throw e;
    } finally {
      enableRefreshInterval(browseIndexNames);
    }
  }

  private void disableRefreshInterval(String... indices) {
    for (var index : indices) {
      log.info("disableRefreshInterval:: [index: {}]", index);
      indexRepository.updateIndexSettings(index, "{\"index.refresh_interval\": \"-1\"}");
    }
  }

  private void enableRefreshInterval(String... indices) {
    for (var index : indices) {
      indexRepository.updateIndexSettings(index, "{\"index.refresh_interval\": \"1s\"}");
    }
    indexRepository.refreshIndices(indices);
    log.info("enableRefreshInterval:: restored and refreshed [count: {}]", indices.length);
  }

  private void deleteAllDocuments(String indexName) {
    var request = new DeleteByQueryRequest(indexName);
    request.setQuery(QueryBuilders.matchAllQuery());
    request.setRefresh(true);
    performExceptionalOperation(
      () -> elasticsearchClient.deleteByQuery(request, RequestOptions.DEFAULT),
      indexName, "deleteAllDocuments");
  }

  private void startBrowsePhaseIfTracked(UUID familyId, long totalSteps, Map<String, Object> details) {
    if (runtimeStatusTracker != null) {
      runtimeStatusTracker.startPhase(familyId, V2ReindexPhaseType.BROWSE_REBUILD, totalSteps, details);
    }
  }

  private void advanceBrowsePhaseIfTracked(UUID familyId, long completedSteps) {
    if (runtimeStatusTracker != null) {
      runtimeStatusTracker.advancePhase(familyId, V2ReindexPhaseType.BROWSE_REBUILD, completedSteps, null);
    }
  }

  private void completeBrowsePhaseIfTracked(UUID familyId) {
    if (runtimeStatusTracker != null) {
      runtimeStatusTracker.completePhase(familyId, V2ReindexPhaseType.BROWSE_REBUILD);
    }
  }

  private void failBrowsePhaseIfTracked(UUID familyId, String errorMessage) {
    if (runtimeStatusTracker != null) {
      runtimeStatusTracker.failPhase(familyId, V2ReindexPhaseType.BROWSE_REBUILD, errorMessage);
    }
  }

  private void updateBrowsePhaseDetailsIfTracked(UUID familyId, Map<String, Object> details) {
    if (runtimeStatusTracker != null) {
      runtimeStatusTracker.updatePhaseDetails(familyId, V2ReindexPhaseType.BROWSE_REBUILD, details);
    }
  }

}
