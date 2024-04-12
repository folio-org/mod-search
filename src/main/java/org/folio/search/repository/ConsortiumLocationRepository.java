package org.folio.search.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.Location;
import org.folio.search.domain.dto.SortOrder;
import org.folio.search.model.SearchResult;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.jetbrains.annotations.NotNull;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.springframework.stereotype.Repository;

import java.io.IOException;

import static org.folio.search.utils.SearchUtils.TENANT_ID_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;
import static org.opensearch.search.sort.SortOrder.ASC;
import static org.opensearch.search.sort.SortOrder.DESC;

@Log4j2
@Repository
@RequiredArgsConstructor
public class ConsortiumLocationRepository {

  public static final String LOCATION_INDEX = "location";
  private static final String OPERATION_TYPE = "searchApi";
  private final ElasticsearchDocumentConverter documentConverter;

  private final RestHighLevelClient client;

  public SearchResult<Location> fetchLocations(String tenantId,
                                               Integer limit,
                                               Integer offset,
                                               String sortBy,
                                               SortOrder sortOrder) {

    var sourceBuilder = getSearchSourceBuilder(tenantId, limit, offset, sortBy, sortOrder);
    var response = search(sourceBuilder);
    return documentConverter.convertToSearchResult(response, Location.class);
  }

  @NotNull
  private static SearchSourceBuilder getSearchSourceBuilder(String tenantId, Integer limit, Integer offset, String sortBy, SortOrder sortOrder) {
    var sourceBuilder = new SearchSourceBuilder();
    sourceBuilder.query(QueryBuilders.termQuery(TENANT_ID_FIELD_NAME, tenantId));
    sourceBuilder.from(offset);
    sourceBuilder.size(limit);
    sourceBuilder.sort(SortBuilders
      .fieldSort(sortBy)
      .order(sortOrder == SortOrder.DESC ? DESC : ASC));

    log.info("searching locations by query: {}", sourceBuilder.toString());
    return sourceBuilder;
  }

  private SearchResponse search(SearchSourceBuilder sourceBuilder) {
    var searchRequest = new SearchRequest(LOCATION_INDEX);
    searchRequest.source(sourceBuilder);
    return performExceptionalOperation(() -> client.search(searchRequest, RequestOptions.DEFAULT), LOCATION_INDEX, OPERATION_TYPE);
  }

}
