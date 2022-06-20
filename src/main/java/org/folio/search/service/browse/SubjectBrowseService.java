package org.folio.search.service.browse;

import static java.util.Collections.emptyMap;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.toList;
import static org.folio.search.utils.SearchQueryUtils.getSubjectCountsQuery;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;
import static org.opensearch.search.sort.SortBuilders.fieldSort;
import static org.opensearch.search.sort.SortOrder.ASC;
import static org.opensearch.search.sort.SortOrder.DESC;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.SubjectBrowseItem;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.SearchResult;
import org.folio.search.model.SimpleResourceRequest;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.utils.SearchUtils;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubjectBrowseService extends AbstractBrowseServiceBySearchAfter<SubjectBrowseItem, SubjectBrowseItem> {

  @Override
  protected BrowseResult<SubjectBrowseItem> browseAround(BrowseRequest request, BrowseContext context) {
    var browseResult = super.browseAround(request, context);
    var records = getSubjectBrowseItems(request, browseResult.getRecords());
    return browseResult.records(records);
  }

  @Override
  protected BrowseResult<SubjectBrowseItem> browseInOneDirection(BrowseRequest request, BrowseContext context) {
    var browseResult = super.browseInOneDirection(request, context);
    var subjectBrowseItems = getSubjectBrowseItems(request, browseResult.getRecords());
    return browseResult.records(subjectBrowseItems);
  }

  private List<SubjectBrowseItem> getSubjectBrowseItems(BrowseRequest request, List<SubjectBrowseItem> items) {
    var subjects = items.stream()
      .filter(item -> item.getTotalRecords() == null)
      .map(SubjectBrowseItem::getSubject)
      .collect(toList());

    var subjectCounts = getSubjectCounts(request, subjects);
    for (var item : items) {
      if (item.getTotalRecords() == null) {
        var subjectAsMapKey = item.getSubject().toLowerCase(ROOT);
        item.totalRecords(subjectCounts.getOrDefault(subjectAsMapKey, 0L).intValue());
      }
    }

    return items;
  }

  @Override
  protected SubjectBrowseItem getEmptyBrowseItem(BrowseContext context) {
    return new SubjectBrowseItem().subject(context.getAnchor()).totalRecords(0).isAnchor(true);
  }

  @Override
  protected BrowseResult<SubjectBrowseItem> mapToBrowseResult(SearchResult<SubjectBrowseItem> res, boolean isAnchor) {
    var browseResult = BrowseResult.of(res);
    return isAnchor ? browseResult.map(item -> item.isAnchor(true)) : browseResult;
  }

  @Override
  protected SearchSourceBuilder getAnchorSearchQuery(BrowseRequest request, BrowseContext context) {
    return searchSource().query(termQuery(request.getTargetField(), context.getAnchor())).from(0).size(1);
  }

  @Override
  protected SearchSourceBuilder getSearchQuery(BrowseRequest req, BrowseContext ctx, boolean isBrowsingForward) {
    return searchSource().query(matchAllQuery())
      .searchAfter(new Object[] {ctx.getAnchor().toLowerCase(ROOT)})
      .sort(fieldSort(req.getTargetField()).order(isBrowsingForward ? ASC : DESC))
      .size(ctx.getLimit(isBrowsingForward) + 1)
      .from(0);
  }

  @Override
  protected String getValueForBrowsing(SubjectBrowseItem browseItem) {
    return browseItem.getSubject();
  }

  private Map<String, Long> getSubjectCounts(BrowseRequest request, List<String> subjects) {
    if (subjects.isEmpty()) {
      return emptyMap();
    }

    var resourceRequest = SimpleResourceRequest.of(INSTANCE_RESOURCE, request.getTenantId());
    var countSearchResult = searchRepository.search(resourceRequest, getSubjectCountsQuery(subjects));
    return SearchUtils.getSubjectCounts(countSearchResult);
  }
}
