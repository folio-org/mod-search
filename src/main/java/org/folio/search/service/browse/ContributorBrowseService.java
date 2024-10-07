package org.folio.search.service.browse;

import static java.util.Objects.nonNull;
import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.MISSING_LAST_PROP;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;
import static org.opensearch.search.sort.SortBuilders.fieldSort;
import static org.opensearch.search.sort.SortOrder.ASC;
import static org.opensearch.search.sort.SortOrder.DESC;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.InstanceContributorBrowseItem;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.SearchResult;
import org.folio.search.model.index.ContributorResource;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.service.consortium.ConsortiumSearchHelper;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortMode;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ContributorBrowseService extends
  AbstractBrowseServiceBySearchAfter<InstanceContributorBrowseItem, ContributorResource> {

  private static final String CONTRIBUTOR_NAME_TYPE_ID_FIELD = "contributorNameTypeId";
  private static final String CONTRIBUTOR_TYPE_ID_FIELD = "instances.typeId";

  private final ConsortiumSearchHelper consortiumSearchHelper;

  @Override
  protected SearchSourceBuilder getAnchorSearchQuery(BrowseRequest request, BrowseContext context) {
    log.debug("getAnchorSearchQuery:: by [request: {}]", request);
    var boolQuery = boolQuery().must(termQuery(request.getTargetField(), context.getAnchor()));
    context.getFilters().forEach(boolQuery::filter);
    var query = consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(context, boolQuery, request.getResource());
    return searchSource().query(query)
      .sort(fieldSort(request.getTargetField()))
      .sort(fieldSort(AUTHORITY_ID_FIELD).missing(MISSING_LAST_PROP))
      .sort(fieldSort(CONTRIBUTOR_NAME_TYPE_ID_FIELD).missing(MISSING_LAST_PROP))
      .sort(fieldSort(CONTRIBUTOR_TYPE_ID_FIELD).missing(MISSING_LAST_PROP).sortMode(SortMode.MAX))
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
    query = consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(ctx, query, req.getResource());
    return searchSource().query(query)
      .searchAfter(new Object[] {getAnchorValue(req, ctx), null, null, null})
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
  protected BrowseResult<InstanceContributorBrowseItem> mapToBrowseResult(BrowseContext context,
                                                                          SearchResult<ContributorResource> res,
                                                                          boolean isAnchor) {
    return BrowseResult.of(res)
      .map(item -> {
        var filteredInstanceResources = consortiumSearchHelper.filterSubResourcesForConsortium(context, item,
          ContributorResource::instances);
        var typeIds = filteredInstanceResources.stream()
          .map(InstanceSubResource::getTypeId)
          .filter(typeId -> nonNull(typeId) && !typeId.equals("null"))
          .distinct()
          .sorted()
          .toList();

        return new InstanceContributorBrowseItem()
          .name(item.name())
          .contributorTypeId(typeIds)
          .contributorNameTypeId(item.contributorNameTypeId())
          .authorityId(item.authorityId())
          .isAnchor(isAnchor)
          .totalRecords(getTotalRecords(filteredInstanceResources));
      });
  }

  @Override
  protected String getValueForBrowsing(InstanceContributorBrowseItem browseItem) {
    return browseItem.getName();
  }

  private Integer getTotalRecords(Set<InstanceSubResource> filteredInstanceResources) {
    return filteredInstanceResources.stream()
      .map(InstanceSubResource::getInstanceId)
      .filter(instanceId -> nonNull(instanceId) && !instanceId.equals("null"))
      .distinct()
      .map(e -> 1)
      .reduce(0, Integer::sum);
  }
}
