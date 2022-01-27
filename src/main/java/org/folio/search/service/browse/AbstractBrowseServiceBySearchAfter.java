package org.folio.search.service.browse;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.folio.search.utils.CollectionUtils.mergeSafelyToList;
import static org.folio.search.utils.CollectionUtils.reverse;
import static org.springframework.core.GenericTypeResolver.resolveTypeArguments;

import java.util.List;
import org.elasticsearch.action.search.MultiSearchResponse.Item;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

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
  protected SearchResult<T> browseAround(BrowseRequest request, BrowseContext context) {
    var succeedingQuery = getSearchQuery(request, context, true);
    var precedingQuery = getSearchQuery(request, context, false);
    var searchSources = context.isAnchorIncluded()
      ? List.of(precedingQuery, succeedingQuery, getAnchorSearchQuery(request, context))
      : List.of(precedingQuery, succeedingQuery);

    var responses = searchRepository.msearch(request, searchSources).getResponses();
    var precedingResult = documentConverter.convertToSearchResult(responses[0].getResponse(), browseResponseClass);
    var succeedingResult = documentConverter.convertToSearchResult(responses[1].getResponse(), browseResponseClass);
    var anchorRes = getAnchorSearchResult(request, context, responses);

    var succeedingRecords = mapToBrowseResult(succeedingResult, false).getRecords();
    return SearchResult.of(precedingResult.getTotalRecords(), mergeSafelyToList(
      reverse(mapToBrowseResult(precedingResult, false).getRecords()),
      anchorRes.getRecords(),
      trimSearchResult(anchorRes.isEmpty(), context.getLimit(true), succeedingRecords)
    ));
  }

  @Override
  protected SearchResult<T> browseInOneDirection(BrowseRequest request, BrowseContext context) {
    return context.isAnchorIncluded(context.isForwardBrowsing())
      ? getSearchResultWithAnchor(request, context)
      : getSearchResultWithoutAnchor(request, context);
  }

  private SearchResult<T> getAnchorSearchResult(BrowseRequest request, BrowseContext context, Item[] responses) {
    if (!context.isAnchorIncluded()) {
      return isHighlightedResult(request, context)
        ? SearchResult.of(0, singletonList(getEmptyBrowseItem(context)))
        : SearchResult.empty();
    }

    var anchorResult = documentConverter.convertToSearchResult(responses[2].getResponse(), browseResponseClass);
    return isHighlightedResult(request, context) && anchorResult.getTotalRecords() == 0
      ? SearchResult.of(1, singletonList(getEmptyBrowseItem(context)))
      : mapToBrowseResult(anchorResult, isHighlightedResult(request, context));
  }

  private SearchResult<T> getSearchResultWithAnchor(BrowseRequest request, BrowseContext context) {
    var anchorQuery = getAnchorSearchQuery(request, context);
    var searchSource = getSearchQuery(request, context, context.isForwardBrowsing());
    var responses = searchRepository.msearch(request, List.of(searchSource, anchorQuery)).getResponses();
    return mapToBrowseResult(getMergedSearchResults(context,
      documentConverter.convertToSearchResult(responses[0].getResponse(), browseResponseClass),
      documentConverter.convertToSearchResult(responses[1].getResponse(), browseResponseClass)), false);
  }

  private SearchResult<T> getSearchResultWithoutAnchor(BrowseRequest request, BrowseContext context) {
    var searchSource = getSearchQuery(request, context, context.isForwardBrowsing());
    var searchResponse = searchRepository.search(request, searchSource);
    var searchResult = documentConverter.convertToSearchResult(searchResponse, browseResponseClass);
    var authoritiesSearchResult = context.isForwardBrowsing() ? searchResult : searchResult.withReversedRecords();
    return mapToBrowseResult(authoritiesSearchResult, false);
  }

  private static <T> SearchResult<T> getMergedSearchResults(BrowseContext ctx, SearchResult<T> result,
    SearchResult<T> anchorResult) {
    var records = result.getRecords();
    var totalRecords = result.getTotalRecords();
    var subjectRecords = trimSearchResult(anchorResult.isEmpty(), ctx.getLimit(ctx.isForwardBrowsing()), records);
    return ctx.isForwardBrowsing()
      ? SearchResult.of(totalRecords, mergeSafelyToList(anchorResult.getRecords(), subjectRecords))
      : SearchResult.of(totalRecords, mergeSafelyToList(reverse(subjectRecords), anchorResult.getRecords()));
  }

  private static <T> List<T> trimSearchResult(boolean isAnchorEmpty, int limit, List<T> result) {
    return isAnchorEmpty || result.size() < limit ? result : result.subList(0, limit - 1);
  }

  protected abstract SearchSourceBuilder getAnchorSearchQuery(BrowseRequest request, BrowseContext context);

  protected abstract SearchSourceBuilder getSearchQuery(
    BrowseRequest request, BrowseContext context, boolean isBrowsingForward);

  protected abstract T getEmptyBrowseItem(BrowseContext context);

  protected abstract SearchResult<T> mapToBrowseResult(SearchResult<R> result, boolean isAnchor);

  protected static boolean isHighlightedResult(BrowseRequest request, BrowseContext context) {
    return isTrue(request.getHighlightMatch()) && context.isAroundBrowsing();
  }
}
