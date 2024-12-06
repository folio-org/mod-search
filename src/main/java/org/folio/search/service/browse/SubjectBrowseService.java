package org.folio.search.service.browse;

import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.MISSING_FIRST_PROP;
import static org.folio.search.utils.SearchUtils.MISSING_LAST_PROP;
import static org.folio.search.utils.SearchUtils.SUBJECT_SOURCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_TYPE_ID_FIELD;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;
import static org.opensearch.search.sort.SortBuilders.fieldSort;
import static org.opensearch.search.sort.SortOrder.ASC;
import static org.opensearch.search.sort.SortOrder.DESC;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.SubjectBrowseItem;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.SearchResult;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.model.index.SubjectResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.service.consortium.ConsortiumSearchHelper;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class SubjectBrowseService extends AbstractBrowseServiceBySearchAfter<SubjectBrowseItem, SubjectResource> {

  private ConsortiumSearchHelper consortiumSearchHelper;

  @Autowired
  public void setConsortiumSearchHelper(ConsortiumSearchHelper consortiumSearchHelper) {
    this.consortiumSearchHelper = consortiumSearchHelper;
  }

  @Override
  protected SearchSourceBuilder getAnchorSearchQuery(BrowseRequest request, BrowseContext context) {
    log.debug("getAnchorSearchQuery:: by [request: {}]", request);
    var boolQuery = boolQuery().must(termQuery(request.getTargetField(), context.getAnchor()));
    context.getFilters().forEach(boolQuery::filter);
    var query = consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(context, boolQuery, request.getResource());
    return searchSource().query(query)
      .sort(fieldSort(request.getTargetField()))
      .sort(fieldSort(AUTHORITY_ID_FIELD).missing(MISSING_LAST_PROP))
      .sort(fieldSort(SUBJECT_SOURCE_ID_FIELD).missing(MISSING_LAST_PROP))
      .sort(fieldSort(SUBJECT_TYPE_ID_FIELD).missing(MISSING_LAST_PROP).sortMode(SortMode.MAX))
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
    var sortOrder = isBrowsingForward ? ASC : DESC;
    var missingProperty = isBrowsingForward || !ctx.isBrowsingForward() ? MISSING_LAST_PROP : MISSING_FIRST_PROP;
    query = consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(ctx, query, req.getResource());
    return searchSource().query(query)
      .searchAfter(new Object[] {getAnchorValue(req, ctx), null, null, null})
      .sort(fieldSort(req.getTargetField()).order(sortOrder))
      .sort(fieldSort(AUTHORITY_ID_FIELD).order(sortOrder).missing(missingProperty))
      .sort(fieldSort(SUBJECT_SOURCE_ID_FIELD).order(sortOrder).missing(missingProperty))
      .sort(fieldSort(SUBJECT_TYPE_ID_FIELD).order(sortOrder).missing(missingProperty).sortMode(SortMode.MAX))
      .size(ctx.getLimit(isBrowsingForward) + 1)
      .from(0);
  }

  @Override
  protected SubjectBrowseItem getEmptyBrowseItem(BrowseContext context) {
    return new SubjectBrowseItem().value(context.getAnchor()).totalRecords(0).isAnchor(true);
  }

  @Override
  protected BrowseResult<SubjectBrowseItem> mapToBrowseResult(BrowseContext context, SearchResult<SubjectResource> res,
                                                              boolean isAnchor) {
    return BrowseResult.of(res)
      .map(subjectResource -> new SubjectBrowseItem()
        .value(subjectResource.value())
        .authorityId(subjectResource.authorityId())
        .sourceId(subjectResource.sourceId())
        .typeId(subjectResource.typeId())
        .isAnchor(isAnchor ? true : null)
        .totalRecords(getTotalRecords(context, subjectResource)));
  }

  @Override
  protected String getValueForBrowsing(SubjectBrowseItem browseItem) {
    return browseItem.getValue();
  }

  private Integer getTotalRecords(BrowseContext context, SubjectResource subjectResource) {
    return consortiumSearchHelper.filterSubResourcesForConsortium(context, subjectResource,
        SubjectResource::instances).stream()
      .map(InstanceSubResource::getCount)
      .reduce(0, Integer::sum);
  }
}
