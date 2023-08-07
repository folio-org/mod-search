package org.folio.search.service.browse;

import static java.util.Locale.ROOT;
import static org.folio.search.utils.CollectionUtils.toListSafe;
import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;
import static org.opensearch.search.sort.SortBuilders.fieldSort;
import static org.opensearch.search.sort.SortOrder.ASC;
import static org.opensearch.search.sort.SortOrder.DESC;

import java.util.regex.Pattern;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.InstanceContributorBrowseItem;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.SearchResult;
import org.folio.search.model.index.ContributorResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortMode;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ContributorBrowseService extends
  AbstractBrowseServiceBySearchAfter<InstanceContributorBrowseItem, ContributorResource> {

  private static final String MISSING_LAST_PROP = "_last";
  private static final String CONTRIBUTOR_NAME_TYPE_ID_FIELD = "contributorNameTypeId";
  private static final String CONTRIBUTOR_TYPE_ID_FIELD = "contributorTypeId";
  private static final Pattern INSTANCES_FIELD_SPLIT_PATTERN = Pattern.compile("\\|");

  @Override
  protected SearchSourceBuilder getAnchorSearchQuery(BrowseRequest request, BrowseContext context) {
    log.debug("getAnchorSearchQuery:: by [request: {}]", request);
    var boolQuery = boolQuery().must(termQuery(request.getTargetField(), context.getAnchor()));
    context.getFilters().forEach(boolQuery::filter);
    return searchSource().query(boolQuery)
      .size(context.getLimit(context.isBrowsingForward()))
      .from(0);
  }

  @Override
  protected SearchSourceBuilder getSearchQuery(BrowseRequest req, BrowseContext ctx, boolean isBrowsingForward) {
    log.debug("getSearchQuery:: by [request: {}, isBrowsingForward: {}]", req, isBrowsingForward);

    QueryBuilder query;
    if (ctx.getFilters().isEmpty()) {
      query = matchAllQuery();
    } else {
      var boolQuery = boolQuery();
      ctx.getFilters().forEach(boolQuery::filter);
      query = boolQuery;
    }
    return searchSource().query(query)
      .searchAfter(new Object[] {ctx.getAnchor().toLowerCase(ROOT), null, null, null})
      .sort(fieldSort(req.getTargetField()).order(isBrowsingForward ? ASC : DESC))
      .sort(fieldSort(AUTHORITY_ID_FIELD).missing(MISSING_LAST_PROP))
      .sort(fieldSort(CONTRIBUTOR_NAME_TYPE_ID_FIELD).missing(MISSING_LAST_PROP))
      .sort(fieldSort(CONTRIBUTOR_TYPE_ID_FIELD).missing(MISSING_LAST_PROP).sortMode(SortMode.MAX))
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
        .contributorTypeId(toListSafe(item.getContributorTypeId(), s -> !s.equals("null")))
        .contributorNameTypeId(item.getContributorNameTypeId())
        .authorityId(item.getAuthorityId())
        .isAnchor(isAnchor)
        .totalRecords(calculateTotalRecords(item)));
  }

  @Override
  protected String getValueForBrowsing(InstanceContributorBrowseItem browseItem) {
    return browseItem.getName();
  }

  private int calculateTotalRecords(ContributorResource item) { //todo: may count another tenant in total
    return ((Long) item.getInstances().stream()
      .map(instance -> INSTANCES_FIELD_SPLIT_PATTERN.split(instance.getInstanceId())[0])
      .distinct()
      .count()
    ).intValue();
  }
}
