package org.folio.search.service.browse;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.folio.search.utils.CollectionUtils.mergeSafelyToList;
import static org.folio.search.utils.CollectionUtils.reverse;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.springframework.core.GenericTypeResolver.resolveTypeArguments;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.opensearch.action.search.MultiSearchResponse.Item;
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
  protected BrowseResult<T> browseAround(BrowseRequest request, BrowseContext context) {
    log.debug("browseAround:: by [tenant: {}, query: {}]", request.getTenantId(), request.getQuery());

    var succeedingQuery = getSearchQuery(request, context, true);
    var precedingQuery = getSearchQuery(request, context, false);
    var searchSources = context.isAnchorIncluded(true)
      ? List.of(precedingQuery, succeedingQuery, getAnchorSearchQuery(request, context))
      : List.of(precedingQuery, succeedingQuery);

    log.info("browseAround:: Attempting to multi-search request [tenant: {}, searchSource: {}]",
      request.getTenantId(), collectionToLogMsg(searchSources));
    var responses = searchRepository.msearch(request, searchSources).getResponses();
    return createBrowseResult(responses, request, context);
  }

  @Override
  protected BrowseResult<T> browseInOneDirection(BrowseRequest request, BrowseContext context) {
    log.debug("browseInOneDirection:: by [tenant: {}, query: {}]", request.getTenantId(), request.getQuery());

    return context.isAnchorIncluded(context.isBrowsingForward())
      ? getSearchResultWithAnchor(request, context)
      : getSearchResultWithoutAnchor(request, context);
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
  protected abstract BrowseResult<T> mapToBrowseResult(SearchResult<R> searchResult, boolean isAnchor);

  private BrowseResult<T> createBrowseResult(Item[] responses, BrowseRequest request, BrowseContext context) {
    var precedingResult = documentConverter.convertToSearchResult(responses[0].getResponse(), browseResponseClass);
    var succeedingResult = documentConverter.convertToSearchResult(responses[1].getResponse(), browseResponseClass);

    var anchorRecords = getAnchorSearchResult(request, context, responses).getRecords();
    var precedingRecords = reverse(mapToBrowseResult(precedingResult, false).getRecords());
    var succeedingRecords = mergeSafelyToList(anchorRecords, mapToBrowseResult(succeedingResult, false).getRecords());

    return new BrowseResult<T>()
      .totalRecords(precedingResult.getTotalRecords())
      .prev(getPrevBrowsingValue(precedingRecords, context, false))
      .next(getNextBrowsingValue(succeedingRecords, context, true))
      .records(mergeSafelyToList(trim(precedingRecords, context, false), trim(succeedingRecords, context, true)));
  }

  private BrowseResult<T> getAnchorSearchResult(BrowseRequest request, BrowseContext context, Item[] responses) {
    var isAnchorHighlighted = isTrue(request.getHighlightMatch());
    if (!context.isAnchorIncluded(true)) {
      return isAnchorHighlighted
        ? BrowseResult.of(0, singletonList(getEmptyBrowseItem(context)))
        : BrowseResult.empty();
    }

    var anchorResult = documentConverter.convertToSearchResult(responses[2].getResponse(), browseResponseClass);
    return isAnchorHighlighted && anchorResult.getTotalRecords() == 0
      ? BrowseResult.of(1, singletonList(getEmptyBrowseItem(context)))
      : mapToBrowseResult(anchorResult, isAnchorHighlighted);
  }

  private BrowseResult<T> getSearchResultWithAnchor(BrowseRequest request, BrowseContext context) {
    var anchorQuery = getAnchorSearchQuery(request, context);
    var isBrowsingForward = context.isBrowsingForward();
    var searchSource = getSearchQuery(request, context, isBrowsingForward);
    var responses = searchRepository.msearch(request, List.of(searchSource, anchorQuery)).getResponses();
    var browseResult = documentConverter.convertToSearchResult(responses[0].getResponse(), browseResponseClass);
    var anchorResult = documentConverter.convertToSearchResult(responses[1].getResponse(), browseResponseClass);
    var records = mergeSafelyToList(anchorResult.getRecords(), browseResult.getRecords());
    return getBrowseResult(SearchResult.of(browseResult.getTotalRecords(), records), context);
  }

  private BrowseResult<T> getSearchResultWithoutAnchor(BrowseRequest request, BrowseContext context) {
    var isBrowsingForward = context.isBrowsingForward();
    var searchSource = getSearchQuery(request, context, isBrowsingForward);
    var searchResponse = searchRepository.search(request, searchSource);
    var searchResult = documentConverter.convertToSearchResult(searchResponse, browseResponseClass);
    return getBrowseResult(searchResult, context);
  }

  private BrowseResult<T> getBrowseResult(SearchResult<R> result, BrowseContext context) {
    var isBrowsingForward = context.isBrowsingForward();
    var browseResult = mapToBrowseResult(result, false);
    var records = isBrowsingForward ? browseResult.getRecords() : reverse(browseResult.getRecords());
    return new BrowseResult<T>()
      .totalRecords(browseResult.getTotalRecords())
      .prev(getPrevBrowsingValue(records, context, isBrowsingForward))
      .next(getNextBrowsingValue(records, context, isBrowsingForward))
      .records(trim(records, context, isBrowsingForward));
  }
}
