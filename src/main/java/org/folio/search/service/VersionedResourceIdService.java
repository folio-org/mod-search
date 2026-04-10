package org.folio.search.service;

import static org.opensearch.search.sort.SortBuilders.fieldSort;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.configuration.properties.StreamIdsProperties;
import org.folio.search.cql.flat.FlatSearchQueryConverter;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.model.service.QueryResolution;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.consortium.FlatConsortiumSearchHelper;
import org.folio.search.utils.StreamIdWriter;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

/**
 * Version-aware resource ID streaming. LEGACY delegates to ResourceIdService.
 * FLAT uses FlatSearchQueryConverter + explicit-index scroll.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class VersionedResourceIdService {

  private static final String FLAT_DOC_ID_PATH = "id";
  private static final String HOLDINGS_ID_PATH = "holdings.id";

  private final ResourceIdService resourceIdService;
  private final QueryVersionResolver queryVersionResolver;
  private final SearchRepository searchRepository;
  private final FlatSearchQueryConverter flatSearchQueryConverter;
  private final FlatConsortiumSearchHelper flatConsortiumSearchHelper;
  private final StreamIdsProperties streamIdsProperties;
  private final ObjectMapper objectMapper;

  public void streamResourceIdsAsText(CqlResourceIdsRequest request, OutputStream outputStream,
                                      String queryVersion) {
    var resolution = queryVersionResolver.resolve(queryVersion, request.getTenantId());

    if (resolution.pathType() == QueryResolution.PathType.LEGACY) {
      resourceIdService.streamResourceIdsAsText(request, outputStream);
      return;
    }

    var writer = StreamIdWriter.createOutputStreamWriter(outputStream);
    streamResourceIds(request, resolution, ids -> StreamIdWriter.writeRecordIdsToText(ids, writer));
  }

  public void streamResourceIdsAsJson(CqlResourceIdsRequest request, OutputStream outputStream,
                                      String queryVersion) {
    var resolution = queryVersionResolver.resolve(queryVersion, request.getTenantId());

    if (resolution.pathType() == QueryResolution.PathType.LEGACY) {
      resourceIdService.streamResourceIdsAsJson(request, outputStream);
      return;
    }

    StreamIdWriter.processStreamToJson(objectMapper, outputStream, (json, counter) ->
      streamResourceIds(request, resolution, ids -> {
        counter.addAndGet(ids.size());
        StreamIdWriter.writeRecordIdsToJson(ids, json);
      }));
  }

  private void streamResourceIds(CqlResourceIdsRequest request, QueryResolution resolution,
                                 Consumer<List<String>> idsConsumer) {
    log.info("streamResourceIds:: using flat path [query: {}, resource: {}, index: {}]",
      request.getQuery(), request.getResource(), resolution.indexName());

    if (HOLDINGS_ID_PATH.equals(request.getSourceFieldPath())) {
      streamHoldingIds(request, resolution, idsConsumer);
      return;
    }

    var searchSource = buildFlatInstanceSearchSource(request);
    searchSource.fetchSource(new String[] {FLAT_DOC_ID_PATH}, null);
    searchRepository.streamResourceIds(resolution.indexName(), searchSource, FLAT_DOC_ID_PATH, idsConsumer);
  }

  private void streamHoldingIds(CqlResourceIdsRequest request, QueryResolution resolution,
                                Consumer<List<String>> idsConsumer) {
    var instanceSearchSource = buildFlatInstanceSearchSource(request);
    instanceSearchSource.fetchSource(new String[] {FLAT_DOC_ID_PATH}, null);

    searchRepository.streamResourceIds(resolution.indexName(), instanceSearchSource, FLAT_DOC_ID_PATH, instanceIds -> {
      if (CollectionUtils.isEmpty(instanceIds)) {
        return;
      }

      var holdingsQuery = new BoolQueryBuilder()
        .filter(QueryBuilders.termQuery("resourceType", "holding"))
        .filter(QueryBuilders.termsQuery("instanceId", instanceIds));

      var holdingsSearchSource = new SearchSourceBuilder()
        .query(holdingsQuery)
        .size(streamIdsProperties.getScrollQuerySize())
        .fetchSource(new String[] {FLAT_DOC_ID_PATH}, null)
        .sort(fieldSort("_doc"));

      searchRepository.streamResourceIds(resolution.indexName(), holdingsSearchSource, FLAT_DOC_ID_PATH, idsConsumer);
    });
  }

  private SearchSourceBuilder buildFlatInstanceSearchSource(CqlResourceIdsRequest request) {
    var searchSource = flatSearchQueryConverter.convert(request.getQuery(), request.getResource());
    searchSource.query(flatConsortiumSearchHelper.addConsortiumFilter(searchSource.query(), request.getTenantId()));

    var filteredQuery = new BoolQueryBuilder()
      .must(searchSource.query())
      .filter(QueryBuilders.termQuery("resourceType", "instance"));

    return searchSource
      .query(filteredQuery)
      .size(streamIdsProperties.getScrollQuerySize())
      .sort(fieldSort("_doc"));
  }

}
