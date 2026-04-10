package org.folio.search.repository;

import static org.folio.search.utils.SearchUtils.performExceptionalOperation;
import static org.opensearch.client.RequestOptions.DEFAULT;
import static org.opensearch.common.xcontent.XContentType.JSON;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.utils.JsonConverter;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
@RequiredArgsConstructor
public class V2BrowseProjectionRepository {

  private static final int SCROLL_BATCH_SIZE = 5000;
  private static final int BULK_BATCH_SIZE = 5000;

  private final RestHighLevelClient client;
  private final SearchRepository searchRepository;
  private final JsonConverter jsonConverter;

  /**
   * Queries the V2 main index for documents matching browse IDs in a nested field.
   * Used for contributor, subject, classification browse ID lookups on instance docs.
   */
  public void streamByNestedBrowseIds(String mainIndex, String browseIdField, Set<String> browseIds,
                                      Consumer<List<Map<String, Object>>> consumer, String... sourceFields) {
    var query = QueryBuilders.boolQuery()
      .filter(QueryBuilders.termsQuery(browseIdField, browseIds))
      .filter(QueryBuilders.termQuery("join_field", "instance"));

    streamByQuery(mainIndex, query, consumer, sourceFields);
  }

  /**
   * Queries the V2 main index for item docs matching call number browse IDs.
   */
  public void streamItemsByCallNumberBrowseIds(String mainIndex, Set<String> browseIds,
                                               Consumer<List<Map<String, Object>>> consumer,
                                               String... sourceFields) {
    var query = QueryBuilders.boolQuery()
      .filter(QueryBuilders.termsQuery("item.itemCallNumberBrowseId", browseIds))
      .filter(QueryBuilders.termQuery("join_field", "item"));

    streamByQuery(mainIndex, query, consumer, sourceFields);
  }

  /**
   * Streams all instance docs needed for full V2 browse rebuilds.
   */
  public void streamAllInstanceBrowseSourceDocs(String mainIndex, Consumer<List<Map<String, Object>>> consumer,
                                                String... sourceFields) {
    streamByQuery(mainIndex, QueryBuilders.termQuery("join_field", "instance"), consumer, sourceFields);
  }

  /**
   * Streams all item docs needed for full V2 call-number browse rebuilds.
   */
  public void streamAllItemBrowseSourceDocs(String mainIndex, Consumer<List<Map<String, Object>>> consumer,
                                            String... sourceFields) {
    streamByQuery(mainIndex, QueryBuilders.termQuery("join_field", "item"), consumer, sourceFields);
  }

  /**
   * Bulk upserts browse documents into the target browse index.
   */
  public void bulkUpsert(String browseIndex, Collection<Map<String, Object>> browseDocs) {
    if (browseDocs.isEmpty()) {
      return;
    }

    var bulkRequest = new BulkRequest();
    var batchSize = 0;
    for (var doc : browseDocs) {
      var id = (String) doc.get("id");
      bulkRequest.add(new IndexRequest(browseIndex)
        .id(id)
        .source(jsonConverter.toJson(doc), JSON));
      batchSize++;
      if (batchSize == BULK_BATCH_SIZE) {
        executeBulkUpsert(browseIndex, bulkRequest);
        bulkRequest = new BulkRequest();
        batchSize = 0;
      }
    }
    executeBulkUpsert(browseIndex, bulkRequest);
  }

  /**
   * Bulk deletes browse documents by their IDs from the target browse index.
   */
  public void bulkDelete(String browseIndex, Set<String> browseIds) {
    if (browseIds.isEmpty()) {
      return;
    }

    var bulkRequest = new BulkRequest();
    var batchSize = 0;
    for (var id : browseIds) {
      bulkRequest.add(new DeleteRequest(browseIndex).id(id));
      batchSize++;
      if (batchSize == BULK_BATCH_SIZE) {
        executeBulkDelete(browseIndex, bulkRequest);
        bulkRequest = new BulkRequest();
        batchSize = 0;
      }
    }
    executeBulkDelete(browseIndex, bulkRequest);
  }

  private void executeBulkUpsert(String browseIndex, BulkRequest bulkRequest) {
    if (bulkRequest.numberOfActions() == 0) {
      return;
    }

    var bulkResponse = executeBulkRequest(browseIndex, bulkRequest, "v2BrowseProjectionBulk");
    if (bulkResponse.hasFailures()) {
      log.warn("bulkUpsert:: some browse documents failed [index: {}, failures: {}]",
        browseIndex, bulkResponse.buildFailureMessage());
    }
  }

  private void executeBulkDelete(String browseIndex, BulkRequest bulkRequest) {
    if (bulkRequest.numberOfActions() == 0) {
      return;
    }

    var bulkResponse = executeBulkRequest(browseIndex, bulkRequest, "v2BrowseProjectionBulkDelete");
    if (bulkResponse.hasFailures()) {
      log.warn("bulkDelete:: some browse document deletes failed [index: {}, failures: {}]",
        browseIndex, bulkResponse.buildFailureMessage());
    }
  }

  private BulkResponse executeBulkRequest(String browseIndex, BulkRequest bulkRequest, String operation) {
    return performExceptionalOperation(() -> client.bulk(bulkRequest, DEFAULT), browseIndex, operation);
  }

  private void streamByQuery(String indexName, QueryBuilder query, Consumer<List<Map<String, Object>>> consumer,
                             String... sourceFields) {
    var searchSource = new SearchSourceBuilder()
      .query(query)
      .size(SCROLL_BATCH_SIZE)
      .sort("_doc", SortOrder.ASC)
      .fetchSource(sourceFields, null);
    streamAndMap(indexName, searchSource, consumer);
  }

  private void streamAndMap(String indexName, SearchSourceBuilder source,
                            Consumer<List<Map<String, Object>>> consumer) {
    searchRepository.streamDocuments(indexName, source,
      hits -> consumer.accept(Arrays.stream(hits).map(SearchHit::getSourceAsMap).toList()));
  }
}
