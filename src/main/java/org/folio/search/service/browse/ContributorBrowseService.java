package org.folio.search.service.browse;

import static java.util.Locale.ROOT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.elasticsearch.search.sort.SortBuilders.fieldSort;
import static org.elasticsearch.search.sort.SortOrder.ASC;
import static org.elasticsearch.search.sort.SortOrder.DESC;
import static org.folio.search.utils.CollectionUtils.toListSafe;

import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.domain.dto.InstanceContributorBrowseItem;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.SearchResult;
import org.folio.search.model.index.ContributorResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.springframework.stereotype.Service;

@Service
public class ContributorBrowseService extends
  AbstractBrowseServiceBySearchAfter<InstanceContributorBrowseItem, ContributorResource> {

  private static final ExistsQueryBuilder FILTER_QUERY = existsQuery("instances");

//  @Override
//  protected BrowseResult<InstanceContributorBrowseItem> browseAround(BrowseRequest request, BrowseContext context) {
//    var succeedingQuery = getSearchQuery(request, context, true);
//    var precedingQuery = getSearchQuery(request, context, false);
//    var multiSearchResponse = searchRepository.msearch(request, List.of(precedingQuery, succeedingQuery));
//
//    var responses = multiSearchResponse.getResponses();
//    var precedingResult = documentConverter.convertToSearchResult(responses[0].getResponse(), browseResponseClass);
//    var succeedingResult = documentConverter.convertToSearchResult(responses[1].getResponse(), browseResponseClass);
//    var precedingRecords = mapToBrowseResult(precedingResult, false);
//    var succeedingRecords = mapToBrowseResult(succeedingResult, false);
//    if (TRUE.equals(request.getHighlightMatch())) {
//      highlightMatchingCallNumber(context, succeedingRecords);
//    }
//
//
//    return new BrowseResult<InstanceContributorBrowseItem>()
//      .totalRecords(precedingResult.getTotalRecords() + succeedingResult.getTotalRecords())
//      .prev(getNextValueForBrowsing(precedingRecords.getRecords(), context.getPrecedingLimit()))
//      .next(getNextValueForBrowsing(succeedingRecords.getRecords(), context.getSucceedingLimit()))
//      .records(mergeSafelyToList(
//        trim(reverse(precedingRecords.getRecords()), context, false),
//        trim(succeedingRecords.getRecords(), context, true)));
//
//  }
//
//  private void highlightMatchingCallNumber(BrowseContext ctx, BrowseResult<InstanceContributorBrowseItem> result) {
//    var items = result.getRecords();
//    var anchor = ctx.getAnchor();
//
//    if (isEmpty(items)) {
//      result.setRecords(singletonList(getEmptyBrowseItem(ctx)));
//      return;
//    }
//
//    var firstBrowseItem = items.get(0);
//    if (!StringUtils.equals(firstBrowseItem.getName(), anchor)) {
//      var browseItemsWithEmptyValue = new ArrayList<InstanceContributorBrowseItem>();
//      browseItemsWithEmptyValue.add(getEmptyBrowseItem(ctx));
//      browseItemsWithEmptyValue.addAll(items);
//      result.setRecords(browseItemsWithEmptyValue);
//      return;
//    }
//
//    firstBrowseItem.setIsAnchor(true);
//  }

  @Override
  protected SearchSourceBuilder getAnchorSearchQuery(BrowseRequest request, BrowseContext context) {
    var boolQuery = boolQuery().filter(FILTER_QUERY)
      .must(termQuery(request.getTargetField(), context.getAnchor()));
    context.getFilters().forEach(boolQuery::filter);
    return searchSource().query(termQuery(request.getTargetField(), context.getAnchor())).from(0).size(1);
//      .size(context.getLimit(context.isBrowsingForward()));
  }

  @Override
  protected SearchSourceBuilder getSearchQuery(BrowseRequest req, BrowseContext ctx, boolean isBrowsingForward) {
    var boolQuery = boolQuery().filter(FILTER_QUERY);
    ctx.getFilters().forEach(boolQuery::filter);
    return searchSource().query(boolQuery)
      .searchAfter(new Object[] {ctx.getAnchor().toLowerCase(ROOT)})
//      .searchAfter(new Object[]{ctx.getAnchor().toLowerCase(ROOT), ""})
      .sort(fieldSort(req.getTargetField()).order(isBrowsingForward ? ASC : DESC))
//      .sort(fieldSort("contributorNameTypeId").order(isBrowsingForward ? ASC : DESC).missing("_first"))
      .size(ctx.getLimit(isBrowsingForward) + 1)
      .from(0);
  }

  @Override
  protected InstanceContributorBrowseItem getEmptyBrowseItem(BrowseContext context) {
    return new InstanceContributorBrowseItem().name(context.getAnchor()).totalRecords(0).isAnchor(true);
  }

  @Override
  protected BrowseResult<InstanceContributorBrowseItem> mapToBrowseResult(SearchResult<ContributorResource> res,
                                                                          boolean isAnchor) {
    return BrowseResult.of(res)
      .map(item -> new InstanceContributorBrowseItem()
        .name(item.getName())
        .contributorTypeId(toListSafe(item.getContributorTypeId()))
        .contributorNameTypeId(item.getContributorNameTypeId())
        .isAnchor(isAnchor)
        .totalRecords(
          ((Long) item.getInstances().stream().map(id -> id.split("\\|")[0]).distinct().count()).intValue()));
  }

  @Override
  protected String getValueForBrowsing(InstanceContributorBrowseItem browseItem) {
    return browseItem.getName();
  }
}
