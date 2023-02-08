package org.folio.search.service.browse;

import static java.util.Locale.ROOT;
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
import org.folio.search.model.index.SubjectResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class SubjectBrowseService extends AbstractBrowseServiceBySearchAfter<SubjectBrowseItem, SubjectResource> {

  @Override
  protected SearchSourceBuilder getAnchorSearchQuery(BrowseRequest request, BrowseContext context) {
    return searchSource().query(termQuery(request.getTargetField(), context.getAnchor())).from(0).size(1);
  }

  @Override
  protected SearchSourceBuilder getSearchQuery(BrowseRequest req, BrowseContext ctx, boolean isBrowsingForward) {
    log.debug("getSearchQuery:: by [request: {}, isBrowsingForward: {}]", req, isBrowsingForward);
    QueryBuilder query;
    if (ctx.getFilters().isEmpty()) {
      query = matchAllQuery();
    } else {
      query = boolQuery();
      ctx.getFilters().forEach(filter -> ((BoolQueryBuilder) filter).filter(filter));
    }
    return searchSource().query(query)
      .searchAfter(new Object[] {ctx.getAnchor().toLowerCase(ROOT)})
      .sort(fieldSort(req.getTargetField()).order(isBrowsingForward ? ASC : DESC))
      .size(ctx.getLimit(isBrowsingForward) + 1)
      .from(0);
  }

  @Override
  protected SubjectBrowseItem getEmptyBrowseItem(BrowseContext context) {
    return new SubjectBrowseItem().value(context.getAnchor()).totalRecords(0).isAnchor(true);
  }

  @Override
  protected BrowseResult<SubjectBrowseItem> mapToBrowseResult(SearchResult<SubjectResource> res, boolean isAnchor) {
    return BrowseResult.of(res)
      .map(subjectResource -> new SubjectBrowseItem()
        .value(subjectResource.getValue())
        .authorityId(subjectResource.getAuthorityId())
        .isAnchor(isAnchor ? true : null)
        .totalRecords(subjectResource.getInstances().size()));
  }

  @Override
  protected String getValueForBrowsing(SubjectBrowseItem browseItem) {
    return browseItem.getValue();
  }
}
