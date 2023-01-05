package org.folio.search.service.browse;

import static java.util.Locale.ROOT;
import static org.folio.search.utils.CollectionUtils.toListSafe;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;
import static org.opensearch.search.sort.SortBuilders.fieldSort;
import static org.opensearch.search.sort.SortOrder.ASC;
import static org.opensearch.search.sort.SortOrder.DESC;

import org.folio.search.domain.dto.InstanceContributorBrowseItem;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.SearchResult;
import org.folio.search.model.index.ContributorResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

@Service
public class ContributorBrowseService extends
  AbstractBrowseServiceBySearchAfter<InstanceContributorBrowseItem, ContributorResource> {

  @Override
  protected SearchSourceBuilder getAnchorSearchQuery(BrowseRequest request, BrowseContext context) {
    var boolQuery = boolQuery()
      .must(termQuery(request.getTargetField(), context.getAnchor()));
    context.getFilters().forEach(boolQuery::filter);
    return searchSource().query(boolQuery)
      .size(context.getLimit(context.isBrowsingForward()))
      .from(0);
  }

  @Override
  protected SearchSourceBuilder getSearchQuery(BrowseRequest req, BrowseContext ctx, boolean isBrowsingForward) {
    QueryBuilder query;
    if (ctx.getFilters().isEmpty()) {
      query = matchAllQuery();
    } else {
      query = boolQuery();
      ctx.getFilters().forEach(filter -> ((BoolQueryBuilder) query).filter(filter));
    }
    return searchSource().query(query)
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
        .authorityId(item.getAuthorityId())
        .isAnchor(isAnchor)
        .totalRecords(calculateTotalRecords(item)));
  }

  @Override
  protected String getValueForBrowsing(InstanceContributorBrowseItem browseItem) {
    return browseItem.getName();
  }

  private int calculateTotalRecords(ContributorResource item) {
    return ((Long) item.getInstances().stream().map(id -> id.split("\\|")[0]).distinct().count()).intValue();
  }
}
