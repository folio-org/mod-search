package org.folio.search.service.browse;

import static java.util.Collections.emptyMap;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.elasticsearch.search.sort.SortBuilders.fieldSort;
import static org.elasticsearch.search.sort.SortOrder.ASC;
import static org.elasticsearch.search.sort.SortOrder.DESC;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.getPathToFulltextPlainValue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubjectBrowseService extends AbstractBrowseServiceBySearchAfter<SubjectBrowseItem, SubjectBrowseItem> {

  @Override
  protected SearchResult<SubjectBrowseItem> browseAround(BrowseRequest request, BrowseContext context) {
    var searchResult = super.browseAround(request, context);
    var records = getSubjectBrowseItems(request, context, searchResult.getRecords());
    return SearchResult.of(searchResult.getTotalRecords(), records);
  }

  @Override
  protected SearchResult<SubjectBrowseItem> browseInOneDirection(BrowseRequest request, BrowseContext context) {
    var searchResult = super.browseInOneDirection(request, context);
    var subjectBrowseItems = getSubjectBrowseItems(request, context, searchResult.getRecords());
    return SearchResult.of(searchResult.getTotalRecords(), subjectBrowseItems);
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

  @Override
  protected SubjectBrowseItem getEmptyBrowseItem(BrowseContext context) {
    return new SubjectBrowseItem().subject((String) context.getAnchor()).totalRecords(0).isAnchor(true);
  }

  @Override
  protected SearchResult<SubjectBrowseItem> mapToBrowseResult(
    SearchResult<SubjectBrowseItem> result, boolean isAnchor) {
    return result;
  }

  @Override
  protected SearchSourceBuilder getAnchorSearchQuery(BrowseRequest request, BrowseContext context) {
    return searchSource().query(termQuery(request.getTargetField(), context.getAnchor())).from(0).size(1);
  }

  @Override
  protected SearchSourceBuilder getSearchQuery(BrowseRequest req, BrowseContext ctx, boolean isBrowsingForward) {
    return searchSource().query(matchAllQuery())
      .searchAfter(new Object[] {((String) ctx.getAnchor()).toLowerCase(ROOT)})
      .sort(fieldSort(req.getTargetField()).order(isBrowsingForward ? ASC : DESC))
      .size(ctx.getLimit(isBrowsingForward))
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
