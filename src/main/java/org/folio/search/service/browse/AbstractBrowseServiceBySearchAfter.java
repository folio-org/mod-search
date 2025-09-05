package org.folio.search.service.browse;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.folio.search.utils.CollectionUtils.mergeSafelyToList;
import static org.folio.search.utils.CollectionUtils.reverse;
import static org.springframework.core.GenericTypeResolver.resolveTypeArguments;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ArrayUtils;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.opensearch.action.search.MultiSearchResponse.Item;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

@Log4j2
public abstract class AbstractBrowseServiceBySearchAfter<T, R> extends AbstractBrowseService<T> {

  protected SearchRepository searchRepository;
  protected ElasticsearchDocumentConverter documentConverter;
  protected Class<R> browseResponseClass;

  /**
   * Resolves browseResponseClass from generics.
   */
  @SuppressWarnings("unchecked")
  protected AbstractBrowseServiceBySearchAfter() {
    var genericTypes = resolveTypeArguments(getClass(), AbstractBrowseServiceBySearchAfter.class);
    var errorMessage = "Failed to resolve generic types for " + getClass().getSimpleName() + ".";
    Assert.isTrue(genericTypes != null, errorMessage);
    this.browseResponseClass = (Class<R>) genericTypes[1];
  }

  /**
   * Injects {@link SearchRepository} bean to the service by Spring framework.
   *
   * @param searchRepository - {@link SearchRepository} bean.
   */
  @Autowired
  public void setSearchRepository(SearchRepository searchRepository) {
    this.searchRepository = searchRepository;
  }

  /**
   * Injects {@link ElasticsearchDocumentConverter} bean to the service by Spring framework.
   *
   * @param documentConverter - {@link ElasticsearchDocumentConverter} bean.
   */
  @Autowired
  public void setDocumentConverter(ElasticsearchDocumentConverter documentConverter) {
    this.documentConverter = documentConverter;
  }

  @Override
  protected BrowseResult<T> browseInOneDirection(BrowseRequest request, BrowseContext context) {
    logBrowseRequest(request, "browseInOneDirection");

    return context.isAnchorIncluded(context.isBrowsingForward())
           ? getSearchResultWithAnchor(request, context)
           : getSearchResultWithoutAnchor(request, context);
  }

  @Override
  protected BrowseResult<T> browseAround(BrowseRequest request, BrowseContext context) {
    logBrowseRequest(request, "browseAround");
    var succeedingQuery = getSearchQuery(request, context, true);
    var precedingQuery = getSearchQuery(request, context, false);
    Item[] responses;

    if (context.isAnchorIncluded(true)) {
      var anchorResponse = processAnchorResponse(request, context, precedingQuery, succeedingQuery);
      var searchSources = List.of(precedingQuery, succeedingQuery);
      logMultiSearchRequest(request, searchSources.size());
      responses = ArrayUtils.add(searchRepository.msearch(request, searchSources).getResponses(), anchorResponse);
    } else {
      var searchSources = List.of(precedingQuery, succeedingQuery);
      logMultiSearchRequest(request, searchSources.size());
      responses = searchRepository.msearch(request, searchSources).getResponses();
    }
    return createBrowseResult(responses, request, context);
  }

  protected String getAnchorValue(BrowseRequest request, BrowseContext ctx) {
    return searchRepository.analyze(ctx.getAnchor(), request.targetField(), request.resource(), request.tenantId());
  }

  /**
   * Provides anchor search query for the given {@link BrowseRequest} and {@link BrowseContext} objects.
   *
   * @param request - {@link BrowseRequest} object with inputs from a user
   * @param context - {@link BrowseContext} with necessary information for browsing.
   * @return created Elasticsearch query as {@link SearchSourceBuilder} object
   */
  protected abstract SearchSourceBuilder getAnchorSearchQuery(BrowseRequest request, BrowseContext context);

  /**
   * Provides search query for the given {@link BrowseRequest} and {@link BrowseContext} objects.
   *
   * @param request           - {@link BrowseRequest} object with inputs from a user
   * @param context           - {@link BrowseContext} with necessary information for browsing.
   * @param isBrowsingForward - direction of browsing.
   * @return created Elasticsearch query as {@link SearchSourceBuilder} object
   */
  protected abstract SearchSourceBuilder getSearchQuery(
    BrowseRequest request, BrowseContext context, boolean isBrowsingForward);

  /**
   * Provides empty browse item if highlight match is specified for browsing around.
   *
   * @param context - {@link BrowseContext} with necessary information for browsing.
   * @return empty browse item
   */
  protected abstract T getEmptyBrowseItem(BrowseContext context);

  /**
   * Maps received {@link SearchResult} object to the {@link BrowseResult} value.
   *
   * @param searchResult - converted Elasticsearch response as {@link SearchResult} value.
   * @param isAnchor     - defines if the given result is anchor or not.
   * @return created {@link BrowseResult} object
   */
  protected abstract BrowseResult<T> mapToBrowseResult(BrowseContext context, SearchResult<R> searchResult,
                                                       boolean isAnchor);

  private Item processAnchorResponse(BrowseRequest request, BrowseContext context, SearchSourceBuilder precedingQuery,
                                     SearchSourceBuilder succeedingQuery) {
    var anchorSearchQuery = getAnchorSearchQuery(request, context);
    var anchorResponse = searchRepository.msearch(request, List.of(anchorSearchQuery)).getResponses()[0];
    SearchResponse response = anchorResponse.getResponse();
    if (response == null) {
      throw new IllegalStateException("Failed to determine the browsing result");
    }
    var hits = response.getHits();
    if (hits != null && hits.getHits().length != 0) {
      updateSearchAfterValuesForQuery(precedingQuery, hits.getAt(0).getSortValues());
      updateSearchAfterValuesForQuery(succeedingQuery, hits.getAt(hits.getHits().length - 1).getSortValues());
    }
    return anchorResponse;
  }

  private void updateSearchAfterValuesForQuery(SearchSourceBuilder query, Object[] sortValues) {
    if (sortValues != null && ArrayUtils.isNotEmpty(sortValues)) {
      query.searchAfter(sortValues);
    }
  }

  private BrowseResult<T> createBrowseResult(Item[] responses, BrowseRequest request, BrowseContext context) {
    var precedingResult = documentConverter.convertToSearchResult(responses[0].getResponse(), browseResponseClass);
    var succeedingResult = documentConverter.convertToSearchResult(responses[1].getResponse(), browseResponseClass);

    browseResultPostProcessing(browseResponseClass, precedingResult);
    browseResultPostProcessing(browseResponseClass, succeedingResult);

    var anchorRecords = getAnchorSearchResult(request, context, responses).getRecords();
    var precedingRecords = reverse(mapToBrowseResult(context, precedingResult, false).getRecords());
    var succeedingRecords = mergeSafelyToList(anchorRecords, mapToBrowseResult(context, succeedingResult, false)
      .getRecords());

    return new BrowseResult<T>()
      .totalRecords(precedingResult.getTotalRecords())
      .prev(getPrevBrowsingValue(precedingRecords, context, false))
      .next(getNextBrowsingValue(succeedingRecords, context, true))
      .records(mergeSafelyToList(trim(precedingRecords, context, false), trim(succeedingRecords, context, true)));
  }

  private BrowseResult<T> getAnchorSearchResult(BrowseRequest request, BrowseContext context, Item[] responses) {
    var isAnchorHighlighted = isTrue(request.highlightMatch());
    if (!context.isAnchorIncluded(true)) {
      return isAnchorHighlighted
             ? BrowseResult.of(0, singletonList(getEmptyBrowseItem(context)))
             : BrowseResult.empty();
    }

    var anchorResult = documentConverter.convertToSearchResult(responses[2].getResponse(), browseResponseClass);
    browseResultPostProcessing(browseResponseClass, anchorResult);

    return isAnchorHighlighted && anchorResult.getTotalRecords() == 0
           ? BrowseResult.of(1, singletonList(getEmptyBrowseItem(context)))
           : mapToBrowseResult(context, anchorResult, isAnchorHighlighted);
  }

  private BrowseResult<T> getSearchResultWithAnchor(BrowseRequest request, BrowseContext context) {
    var anchorQuery = getAnchorSearchQuery(request, context);
    var isBrowsingForward = context.isBrowsingForward();
    var searchSource = getSearchQuery(request, context, isBrowsingForward);
    var responses = searchRepository.msearch(request, List.of(searchSource, anchorQuery)).getResponses();
    var browseResult = documentConverter.convertToSearchResult(responses[0].getResponse(), browseResponseClass);
    var anchorResult = documentConverter.convertToSearchResult(responses[1].getResponse(), browseResponseClass);

    browseResultPostProcessing(browseResponseClass, browseResult);
    browseResultPostProcessing(browseResponseClass, anchorResult);

    var records = mergeSafelyToList(anchorResult.getRecords(), browseResult.getRecords());
    return getBrowseResult(SearchResult.of(browseResult.getTotalRecords(), records), context);
  }

  private BrowseResult<T> getSearchResultWithoutAnchor(BrowseRequest request, BrowseContext context) {
    var isBrowsingForward = context.isBrowsingForward();
    var searchSource = getSearchQuery(request, context, isBrowsingForward);
    var searchResponse = searchRepository.search(request, searchSource);
    var searchResult = documentConverter.convertToSearchResult(searchResponse, browseResponseClass);

    browseResultPostProcessing(browseResponseClass, searchResult);
    return getBrowseResult(searchResult, context);
  }

  private BrowseResult<T> getBrowseResult(SearchResult<R> result, BrowseContext context) {
    var isBrowsingForward = context.isBrowsingForward();
    var browseResult = mapToBrowseResult(context, result, false);
    var records = isBrowsingForward ? browseResult.getRecords() : reverse(browseResult.getRecords());
    return new BrowseResult<T>()
      .totalRecords(browseResult.getTotalRecords())
      .prev(getPrevBrowsingValue(records, context, isBrowsingForward))
      .next(getNextBrowsingValue(records, context, isBrowsingForward))
      .records(trim(records, context, isBrowsingForward));
  }

  private void logBrowseRequest(BrowseRequest request, String functionName) {
    log.debug("{}:: by [tenant: {}, query: {}]", functionName, request.tenantId(), request.query());
  }

  private void logMultiSearchRequest(BrowseRequest request, int size) {
    log.debug("browseAround:: Attempting to multi-search request [tenant: {}, searchSource.size: {}]",
      request.tenantId(), size);
  }
}
