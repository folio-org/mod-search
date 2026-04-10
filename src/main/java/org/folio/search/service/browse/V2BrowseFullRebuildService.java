package org.folio.search.service.browse;

import static org.folio.search.utils.SearchUtils.performExceptionalOperation;

import java.util.UUID;
import java.util.concurrent.Executor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.IndexFamilyService;
import org.folio.spring.FolioExecutionContext;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.springframework.beans.factory.annotation.Qualifier;
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

  public V2BrowseFullRebuildService(IndexFamilyService indexFamilyService,
                                    V2BrowseProjectionService browseProjectionService,
                                    RestHighLevelClient elasticsearchClient,
                                    IndexRepository indexRepository,
                                    @Qualifier("v2BrowseRebuildExecutor") Executor browseRebuildExecutor,
                                    FolioExecutionContext context) {
    this.indexFamilyService = indexFamilyService;
    this.browseProjectionService = browseProjectionService;
    this.elasticsearchClient = elasticsearchClient;
    this.indexRepository = indexRepository;
    this.browseRebuildExecutor = browseRebuildExecutor;
    this.context = context;
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

    // Step 1: Clear all browse indices
    var t0 = System.nanoTime();
    for (var entry : browseIndices.entrySet()) {
      deleteAllDocuments(entry.getValue());
      log.info("rebuildBrowse:: cleared browse index [type: {}, index: {}]", entry.getKey(), entry.getValue());
    }
    log.info("rebuildBrowse:: clear phase done [elapsed: {}ms]", (System.nanoTime() - t0) / 1_000_000);

    disableRefreshInterval(browseIndexNames);
    try {
      t0 = System.nanoTime();
      browseProjectionService.rebuildFull(mainIndex, browseIndices);
      log.info("rebuildBrowse:: projection phase done [elapsed: {}ms]", (System.nanoTime() - t0) / 1_000_000);
    } finally {
      enableRefreshInterval(browseIndexNames);
    }

    log.info("rebuildBrowse:: completed [familyId: {}, mainIndex: {}]", familyId, mainIndex);
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

}
