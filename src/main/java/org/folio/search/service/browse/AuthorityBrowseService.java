package org.folio.search.service.browse;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.folio.search.model.index.AuthRefType.AUTHORIZED;
import static org.folio.search.model.index.AuthRefType.REFERENCE;
import static org.folio.search.model.types.ResourceType.AUTHORITY;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.index.query.QueryBuilders.termsQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;
import static org.opensearch.search.sort.SortBuilders.fieldSort;
import static org.opensearch.search.sort.SortOrder.ASC;
import static org.opensearch.search.sort.SortOrder.DESC;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthorityBrowseItem;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.service.consortium.ConsortiumSearchHelper;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class AuthorityBrowseService extends AbstractBrowseServiceBySearchAfter<AuthorityBrowseItem, Authority> {

  private static final TermsQueryBuilder FILTER_QUERY = termsQuery("authRefType",
    List.of(AUTHORIZED.getTypeValue(), REFERENCE.getTypeValue()));

  private final SearchFieldProvider searchFieldProvider;
  private final ConsortiumSearchHelper consortiumSearchHelper;

  @Override
  protected SearchSourceBuilder getAnchorSearchQuery(BrowseRequest request, BrowseContext ctx) {
    log.debug("getAnchorSearchQuery:: by [browseRequest.field: {}, filters.size: {}]",
      request.targetField(), collectionToLogMsg(ctx.getFilters(), true));

    var boolQuery = boolQuery().filter(FILTER_QUERY).must(termQuery(request.targetField(), ctx.getAnchor()));
    ctx.getFilters().forEach(boolQuery::filter);
    var query = consortiumSearchHelper.filterQueryForActiveAffiliation(boolQuery, AUTHORITY);
    return searchSource()
      .query(query)
      .from(0)
      .size(ctx.getLimit(ctx.isBrowsingForward()))
      .sort(fieldSort(request.targetField()))
      .fetchSource(getIncludedSourceFields(request), null);
  }

  @Override
  protected SearchSourceBuilder getSearchQuery(BrowseRequest request, BrowseContext ctx, boolean isBrowsingForward) {
    log.debug("getSearchQuery:: by [browseRequest.field: {}, filters.size: {}, isBrowsingForward: {}]",
      request.targetField(), collectionToLogMsg(ctx.getFilters(), true), isBrowsingForward);

    var boolQuery = boolQuery().filter(FILTER_QUERY);
    ctx.getFilters().forEach(boolQuery::filter);
    var query = consortiumSearchHelper.filterQueryForActiveAffiliation(boolQuery, AUTHORITY);
    return searchSource().query(query)
      .searchAfter(new Object[] {getAnchorValue(request, ctx)})
      .sort(fieldSort(request.targetField()).order(isBrowsingForward ? ASC : DESC))
      .size(ctx.getLimit(isBrowsingForward) + 1)
      .fetchSource(getIncludedSourceFields(request), null)
      .from(0);
  }

  @Override
  protected AuthorityBrowseItem getEmptyBrowseItem(BrowseContext ctx) {
    return new AuthorityBrowseItem().isAnchor(true).headingRef(ctx.getAnchor());
  }

  @Override
  protected BrowseResult<AuthorityBrowseItem> mapToBrowseResult(BrowseContext context, SearchResult<Authority> result,
                                                                boolean isAnchor) {
    log.debug("mapToBrowseResult:: by [records.size: {}, isAnchor: {}]",
      result.getTotalRecords(), isAnchor);

    return BrowseResult.of(result).map(authority -> new AuthorityBrowseItem()
      .authority(authority)
      .headingRef(authority.getHeadingRef())
      .isAnchor(isAnchor ? true : null));
  }

  @Override
  protected String getValueForBrowsing(AuthorityBrowseItem browseItem) {
    return browseItem.getHeadingRef();
  }

  private String[] getIncludedSourceFields(BrowseRequest request) {
    return isFalse(request.expandAll())
           ? searchFieldProvider.getSourceFields(request.resource(), request.getIncludeFields())
           : new String[0];
  }
}
