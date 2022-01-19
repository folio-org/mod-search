package org.folio.search.service;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.elasticsearch.search.sort.SortBuilders.fieldSort;
import static org.elasticsearch.search.sort.SortOrder.ASC;
import static org.elasticsearch.search.sort.SortOrder.DESC;
import static org.folio.search.utils.CollectionUtils.mergeSafelyToList;
import static org.folio.search.utils.CollectionUtils.reverse;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.getPathToFulltextPlainValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.MultiSearchResponse.Item;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation.Bucket;
import org.elasticsearch.search.aggregations.bucket.terms.IncludeExclude;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedTerms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.domain.dto.SubjectBrowseItem;
import org.folio.search.model.SearchResult;
import org.folio.search.model.SimpleResourceRequest;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubjectBrowseService extends AbstractBrowseService<SubjectBrowseItem> {

  private final SearchRepository searchRepository;
  private final ElasticsearchDocumentConverter documentConverter;

  @Override
  protected SearchResult<SubjectBrowseItem> browseAround(BrowseRequest request, BrowseContext context) {
    var isAnchorIncluded = context.isAnchorIncluded(true) || context.isAnchorIncluded(false);
    var succeedingQuery = getSearchSource(request, context, true);
    var precedingQuery = getSearchSource(request, context, false);
    var searchSources = isAnchorIncluded
      ? List.of(precedingQuery, succeedingQuery, getAnchorQuery(request, context))
      : List.of(precedingQuery, succeedingQuery);

    var responses = searchRepository.msearch(request, searchSources).getResponses();
    var precedingRes = documentConverter.convertToSearchResult(responses[0].getResponse(), SubjectBrowseItem.class);
    var succeedingRes = documentConverter.convertToSearchResult(responses[1].getResponse(), SubjectBrowseItem.class);
    var anchorRes = isAnchorIncluded
      ? getAnchorSearchResult(request, context, responses)
      : getAnchorSearchResult(request, context);

    var records = new ArrayList<>(reverse(precedingRes.getRecords()));
    records.addAll(anchorRes.getRecords());
    records.addAll(trimSearchResult(anchorRes.isEmpty(), context.getLimit(true), succeedingRes.getRecords()));

    var totalRecords = precedingRes.getTotalRecords();
    return SearchResult.of(totalRecords, getSubjectBrowseItems(request, context, records));
  }

  @Override
  protected SearchResult<SubjectBrowseItem> browseInOneDirection(BrowseRequest request, BrowseContext context) {
    var searchResult = context.isAnchorIncluded(context.isForwardBrowsing())
      ? getSearchResultWithAnchor(request, context)
      : getSearchResultWithoutAnchor(request, context);

    var subjectBrowseItems = getSubjectBrowseItems(request, context, searchResult.getRecords());
    return SearchResult.of(searchResult.getTotalRecords(), subjectBrowseItems);
  }

  private SearchResult<SubjectBrowseItem> getSearchResultWithAnchor(BrowseRequest request, BrowseContext context) {
    var anchorQuery = getAnchorQuery(request, context);
    var searchSource = getSearchSource(request, context, context.isForwardBrowsing());
    var responses = searchRepository.msearch(request, List.of(searchSource, anchorQuery)).getResponses();
    return getMergedSearchResults(context,
      documentConverter.convertToSearchResult(responses[0].getResponse(), SubjectBrowseItem.class),
      documentConverter.convertToSearchResult(responses[1].getResponse(), SubjectBrowseItem.class));
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
    if (isAnchorEmpty) {
      return result;
    }
    return result.size() >= limit ? result.subList(0, limit - 1) : result;
  }

  private SearchResult<SubjectBrowseItem> getSearchResultWithoutAnchor(BrowseRequest request, BrowseContext context) {
    var searchSource = getSearchSource(request, context, context.isForwardBrowsing());
    var searchResponse = searchRepository.search(request, searchSource);
    var searchResult = documentConverter.convertToSearchResult(searchResponse, SubjectBrowseItem.class);
    return context.isForwardBrowsing() ? searchResult : searchResult.withReversedRecords();
  }

  private List<SubjectBrowseItem> getSubjectBrowseItems(BrowseRequest request, BrowseContext context,
    List<SubjectBrowseItem> items) {
    var subjects = items.stream()
      .filter(item -> item.getTotalRecords() == null)
      .map(SubjectBrowseItem::getSubject)
      .collect(toList());

    var subjectCounts = getSubjectCounts(request, subjects);
    for (var item : items) {
      if (item.getTotalRecords() == null) {
        var subjectAsMapKey = item.getSubject().toLowerCase(ROOT);
        if (isHighlightedResult(request, context) && equalsIgnoreCase(subjectAsMapKey, (String) context.getAnchor())) {
          item.isAnchor(true);
        }
        item.totalRecords(subjectCounts.getOrDefault(subjectAsMapKey, 0L).intValue());
      }
    }

    return items;
  }

  private SearchResult<SubjectBrowseItem> getAnchorSearchResult(
    BrowseRequest request, BrowseContext context, Item[] responses) {
    var anchorResult = documentConverter.convertToSearchResult(responses[2].getResponse(), SubjectBrowseItem.class);
    if (isHighlightedResult(request, context) && anchorResult.getTotalRecords() == 0) {
      var emptyBrowseItem = getEmptyBrowseItem(context);
      return SearchResult.of(1, singletonList(emptyBrowseItem));
    }

    return anchorResult;
  }

  private SearchResult<SubjectBrowseItem> getAnchorSearchResult(BrowseRequest request, BrowseContext context) {
    return isHighlightedResult(request, context)
      ? SearchResult.of(0, singletonList(getEmptyBrowseItem(context)))
      : SearchResult.empty();
  }

  private static boolean isHighlightedResult(BrowseRequest request, BrowseContext context) {
    return isTrue(request.getHighlightMatch()) && context.isAroundBrowsing();
  }

  private static SubjectBrowseItem getEmptyBrowseItem(BrowseContext context) {
    return new SubjectBrowseItem().subject((String) context.getAnchor()).totalRecords(0).isAnchor(true);
  }

  private static SearchSourceBuilder getAnchorQuery(BrowseRequest request, BrowseContext context) {
    return searchSource().query(termQuery(request.getTargetField(), context.getAnchor())).from(0).size(1);
  }

  private SearchSourceBuilder getSearchSource(BrowseRequest request, BrowseContext context, boolean isBrowsingForward) {
    return searchSource().query(matchAllQuery())
      .searchAfter(new Object[] {((String) context.getAnchor()).toLowerCase(ROOT)})
      .sort(fieldSort(request.getTargetField()).order(isBrowsingForward ? ASC : DESC))
      .size(context.getLimit(isBrowsingForward))
      .from(0);
  }

  private Map<String, Long> getSubjectCounts(BrowseRequest request, List<String> subjects) {
    if (subjects.isEmpty()) {
      return emptyMap();
    }

    var countSearchSource = getSubjectCountsQuery(subjects);
    var resourceRequest = SimpleResourceRequest.of(INSTANCE_RESOURCE, request.getTenantId());
    var countSearchResult = searchRepository.search(resourceRequest, countSearchSource);
    return Optional.ofNullable(countSearchResult)
      .map(SearchResponse::getAggregations)
      .map(aggregations -> aggregations.get("counts"))
      .filter(ParsedTerms.class::isInstance)
      .map(ParsedTerms.class::cast)
      .map(ParsedTerms::getBuckets)
      .stream()
      .flatMap(Collection::stream)
      .collect(toMap(Bucket::getKeyAsString, Bucket::getDocCount));
  }

  private static SearchSourceBuilder getSubjectCountsQuery(List<String> subjects) {
    var lowercaseSubjects = subjects.stream().map(subject -> subject.toLowerCase(ROOT)).toArray(String[]::new);
    var aggregation = AggregationBuilders.terms("counts")
      .size(subjects.size()).field(getPathToFulltextPlainValue("subjects"))
      .includeExclude(new IncludeExclude(lowercaseSubjects, null));
    return searchSource().query(matchAllQuery()).size(0).from(0).aggregation(aggregation);
  }
}
