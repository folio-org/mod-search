package org.folio.search.service.indexrebuild.derived;

import static org.folio.search.model.types.SearchOperationType.CLEAR_SCROLL;
import static org.folio.search.model.types.SearchOperationType.REFRESH;
import static org.folio.search.model.types.SearchOperationType.SCROLL;
import static org.folio.search.model.types.SearchOperationType.SEARCH;
import static org.folio.search.utils.SearchUtils.TENANT_ID_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;
import static org.opensearch.client.RequestOptions.DEFAULT;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.index.query.QueryBuilders.termsQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.repository.IndexNameProvider;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.opensearch.action.admin.indices.refresh.RefreshRequest;
import org.opensearch.action.search.ClearScrollRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.FieldSortBuilder;
import org.springframework.stereotype.Component;

/**
 * Reads keys and source documents of derived indexes.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class DerivedIndexReader {

  private static final int PAGE_SIZE = 1_000;
  private static final TimeValue SCROLL_KEEP_ALIVE = TimeValue.timeValueMinutes(1);

  private final IndexNameProvider indexNameProvider;
  private final RestHighLevelClient elasticsearchClient;

  /**
   * Keys of a source document.
   */
  public static Set<String> keysOf(DerivedRebuildStrategy strategy, Map<String, Object> sourceDocument) {
    var value = sourceDocument.get(strategy.keyField());
    var keys = new HashSet<String>();
    if (value instanceof String key) {
      keys.add(key);
    } else if (value instanceof Collection<?> values) {
      values.stream().filter(String.class::isInstance).map(String.class::cast).forEach(keys::add);
    }
    return keys;
  }

  /**
   * Makes the latest changes of an index visible to the searches below.
   */
  public void refresh(IndexRebuildTarget target, String tenantId) {
    var index = indexNameProvider.getIndexName(target.getResourceType(), tenantId);
    log.debug("refresh:: refreshing index [index: {}]", index);
    var refreshRequest = new RefreshRequest(index);
    performExceptionalOperation(() -> elasticsearchClient.indices().refresh(refreshRequest, DEFAULT), index, REFRESH);
  }

  /**
   * Finds keys of source documents in the given key range.
   *
   * @param scopeTenantId limits the result to source documents of the tenant, nullable
   */
  public Set<String> findKeys(DerivedRebuildStrategy strategy, String tenantId, String lower, String upper,
                              String scopeTenantId) {
    var query = boolQuery().filter(rangeQuery(strategy.keyField()).gte(lower).lt(upper));
    if (scopeTenantId != null) {
      query.filter(termQuery(TENANT_ID_FIELD_NAME, scopeTenantId));
    }
    var keys = findKeys(strategy, tenantId, query);
    keys.removeIf(Predicate.not(key -> inRange(key, lower, upper)));
    return keys;
  }

  /**
   * Finds keys of source documents matching the query.
   */
  public Set<String> findKeys(DerivedRebuildStrategy strategy, String tenantId, QueryBuilder query) {
    var keys = new HashSet<String>();
    scroll(sourceIndex(strategy, tenantId), query, new String[] {strategy.keyField()},
      hit -> keys.addAll(keysOf(strategy, hit.getSourceAsMap())));
    return keys;
  }

  /**
   * Finds existing documents of the target in the given key range that reference the tenant.
   */
  public Set<String> findDocumentKeysOfTenant(DerivedRebuildStrategy strategy, String tenantId, String lower,
                                              String upper, String scopeTenantId) {
    var index = indexNameProvider.getIndexName(strategy.target().getResourceType(), tenantId);
    var query = boolQuery()
      .filter(rangeQuery(strategy.documentKeyField()).gte(lower).lt(upper))
      .filter(strategy.documentsOfTenantQuery(scopeTenantId));
    var keys = new HashSet<String>();
    scroll(index, query, new String[0], hit -> keys.add(hit.getId()));
    return keys;
  }

  public List<Map<String, Object>> findSourceDocuments(DerivedRebuildStrategy strategy, String tenantId,
                                                       Collection<String> keys) {
    var documents = new ArrayList<Map<String, Object>>();
    scroll(sourceIndex(strategy, tenantId), termsQuery(strategy.keyField(), keys), strategy.sourceFields(),
      hit -> documents.add(hit.getSourceAsMap()));
    return documents;
  }

  private String sourceIndex(DerivedRebuildStrategy strategy, String tenantId) {
    return indexNameProvider.getIndexName(strategy.source().getResourceType(), tenantId);
  }

  private static boolean inRange(String key, String lower, String upper) {
    return key.compareTo(lower) >= 0 && key.compareTo(upper) < 0;
  }

  private void scroll(String index, QueryBuilder query, String[] fields, Consumer<SearchHit> consumer) {
    var source = new SearchSourceBuilder().query(query).size(PAGE_SIZE).sort(FieldSortBuilder.DOC_FIELD_NAME)
      .fetchSource(fields, null);
    var response = search(index, new SearchRequest(index).source(source).scroll(SCROLL_KEEP_ALIVE));
    var scrollId = response.getScrollId();
    try {
      while (response.getHits().getHits().length > 0) {
        response.getHits().forEach(consumer);
        response = next(index, scrollId);
        scrollId = response.getScrollId();
      }
    } finally {
      clear(index, scrollId);
    }
  }

  private SearchResponse search(String index, SearchRequest request) {
    return performExceptionalOperation(() -> elasticsearchClient.search(request, DEFAULT), index, SEARCH);
  }

  private SearchResponse next(String index, String scrollId) {
    var request = new SearchScrollRequest(scrollId).scroll(SCROLL_KEEP_ALIVE);
    return performExceptionalOperation(() -> elasticsearchClient.scroll(request, DEFAULT), index, SCROLL);
  }

  private void clear(String index, String scrollId) {
    var request = new ClearScrollRequest();
    request.addScrollId(scrollId);
    performExceptionalOperation(() -> elasticsearchClient.clearScroll(request, DEFAULT), index, CLEAR_SCROLL);
  }
}
