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

  private int calculateTotalRecords(ContributorResource item) {
    return ((Long) item.getInstances().stream().map(id -> id.split("\\|")[0]).distinct().count()).intValue();
  }

  @Override
  protected SearchSourceBuilder getAnchorSearchQuery(BrowseRequest request, BrowseContext context) {
    var boolQuery = boolQuery().filter(FILTER_QUERY)
      .must(termQuery(request.getTargetField(), context.getAnchor()));
    context.getFilters().forEach(boolQuery::filter);
    return searchSource().query(termQuery(request.getTargetField(), context.getAnchor()))
      .size(context.getLimit(context.isBrowsingForward()))
      .from(0);
  }

  @Override
  protected SearchSourceBuilder getSearchQuery(BrowseRequest req, BrowseContext ctx, boolean isBrowsingForward) {
    var boolQuery = boolQuery().filter(FILTER_QUERY);
    ctx.getFilters().forEach(boolQuery::filter);
    return searchSource().query(boolQuery)
      .searchAfter(new Object[] {ctx.getAnchor().toLowerCase(ROOT)})
      .sort(fieldSort(req.getTargetField()).order(isBrowsingForward ? ASC : DESC))
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
        .totalRecords(calculateTotalRecords(item)));
  }

  @Override
  protected String getValueForBrowsing(InstanceContributorBrowseItem browseItem) {
    return browseItem.getName();
  }
}
