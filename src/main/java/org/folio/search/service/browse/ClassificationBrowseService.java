package org.folio.search.service.browse;

import static java.util.Locale.ROOT;
import static org.folio.search.utils.SearchUtils.BROWSE_FIELDS_MAP;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_TYPE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.DEFAULT_SHELVING_ORDER_BROWSING_FIELD;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;
import static org.opensearch.search.sort.SortBuilders.fieldSort;
import static org.opensearch.search.sort.SortOrder.ASC;
import static org.opensearch.search.sort.SortOrder.DESC;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.BrowseConfig;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.domain.dto.ClassificationNumberBrowseItem;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.SearchResult;
import org.folio.search.model.index.ClassificationResource;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.service.consortium.BrowseConfigServiceDecorator;
import org.folio.search.service.consortium.ConsortiumSearchHelper;
import org.folio.search.utils.ShelvingOrderCalculationHelper;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ClassificationBrowseService
  extends AbstractBrowseServiceBySearchAfter<ClassificationNumberBrowseItem, ClassificationResource> {

  private final ConsortiumSearchHelper consortiumSearchHelper;
  private final BrowseConfigServiceDecorator configService;

  @Override
  protected String getValueForBrowsing(ClassificationNumberBrowseItem browseItem) {
    return browseItem.getClassificationNumber();
  }

  @Override
  protected SearchSourceBuilder getAnchorSearchQuery(BrowseRequest req, BrowseContext ctx) {
    log.debug("getAnchorSearchQuery:: by [request: {}]", req);
    var config = configService.getConfig(BrowseType.INSTANCE_CLASSIFICATION, req.getBrowseOptionType());
    var termQueryBuilder = getQuery(ctx, config, termQuery(req.getTargetField(), ctx.getAnchor()));
    var query = consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(ctx,
      termQueryBuilder, req.getResource());
    return searchSource().query(query)
      .size(ctx.getLimit(ctx.isBrowsingForward()))
      .from(0);
  }

  @Override
  protected SearchSourceBuilder getSearchQuery(BrowseRequest req, BrowseContext ctx, boolean isBrowsingForward) {
    log.debug("getSearchQuery:: by [request: {}, isBrowsingForward: {}]", req, isBrowsingForward);
    var config = configService.getConfig(BrowseType.INSTANCE_CLASSIFICATION, req.getBrowseOptionType());

    var browseField = getBrowseField(config);
    var normalizedAnchor = ShelvingOrderCalculationHelper.calculate(ctx.getAnchor(), config.getShelvingAlgorithm());
    var query = consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(ctx, getQuery(ctx, config, null),
      req.getResource());

    return searchSource().query(query)
      .searchAfter(new Object[] {normalizedAnchor.toLowerCase(ROOT)})
      .sort(fieldSort(browseField).order(isBrowsingForward ? ASC : DESC))
      .size(ctx.getLimit(isBrowsingForward) + 1)
      .from(0);
  }

  @Override
  protected ClassificationNumberBrowseItem getEmptyBrowseItem(BrowseContext context) {
    return new ClassificationNumberBrowseItem()
      .classificationNumber(context.getAnchor())
      .totalRecords(0)
      .isAnchor(true);
  }

  @Override
  protected BrowseResult<ClassificationNumberBrowseItem> mapToBrowseResult(BrowseContext ctx,
                                                                           SearchResult<ClassificationResource> res,
                                                                           boolean isAnchor) {
    return BrowseResult.of(res)
      .map(resource -> new ClassificationNumberBrowseItem()
        .classificationNumber(resource.number())
        .classificationTypeId(resource.typeId())
        .isAnchor(isAnchor ? true : null)
        .totalRecords(getTotalRecords(ctx, resource)));
  }

  private static QueryBuilder getQuery(BrowseContext ctx, BrowseConfig config, TermQueryBuilder anchorQuery) {
    var typeIds = config.getTypeIds();
    if (config.getTypeIds().isEmpty() && ctx.getFilters().isEmpty()) {
      if (anchorQuery != null) {
        return anchorQuery;
      }
      return matchAllQuery();
    } else {
      var boolQueryMain = boolQuery();
      if (!config.getTypeIds().isEmpty()) {
        var boolQuery = boolQuery();
        for (var typeId : typeIds) {
          boolQuery.should(termQuery(CLASSIFICATION_TYPE_ID_FIELD, typeId.toString()));
        }
        boolQueryMain.must(boolQuery);
      }
      if (!ctx.getFilters().isEmpty()) {
        ctx.getFilters().forEach(boolQueryMain::filter);
      }
      if (anchorQuery != null) {
        boolQueryMain.must(anchorQuery);
      }
      return boolQueryMain;
    }
  }

  private static String getBrowseField(BrowseConfig config) {
    return BROWSE_FIELDS_MAP.getOrDefault(config.getShelvingAlgorithm(), DEFAULT_SHELVING_ORDER_BROWSING_FIELD);
  }

  private Integer getTotalRecords(BrowseContext ctx, ClassificationResource classificationResource) {
    return consortiumSearchHelper.filterSubResourcesForConsortium(ctx, classificationResource,
        ClassificationResource::instances).stream()
      .map(InstanceSubResource::getInstanceId)
      .distinct()
      .map(e -> 1)
      .reduce(0, Integer::sum);
  }

}
