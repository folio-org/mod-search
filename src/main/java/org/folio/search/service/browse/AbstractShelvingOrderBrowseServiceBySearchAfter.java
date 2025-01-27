package org.folio.search.service.browse;

import static java.util.Locale.ROOT;
import static org.folio.search.utils.SearchUtils.BROWSE_FIELDS_MAP;
import static org.folio.search.utils.SearchUtils.DEFAULT_SHELVING_ORDER_BROWSING_FIELD;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;
import static org.opensearch.search.sort.SortBuilders.fieldSort;
import static org.opensearch.search.sort.SortOrder.ASC;
import static org.opensearch.search.sort.SortOrder.DESC;

import java.util.Set;
import java.util.function.Function;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.domain.dto.BrowseConfig;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.service.consortium.BrowseConfigServiceDecorator;
import org.folio.search.service.consortium.ConsortiumSearchHelper;
import org.folio.search.utils.ShelvingOrderCalculationHelper;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;

@Log4j2
public abstract class AbstractShelvingOrderBrowseServiceBySearchAfter<T, R>
  extends AbstractBrowseServiceBySearchAfter<T, R> {

  protected final ConsortiumSearchHelper consortiumSearchHelper;
  private final BrowseConfigServiceDecorator configService;

  protected AbstractShelvingOrderBrowseServiceBySearchAfter(ConsortiumSearchHelper consortiumSearchHelper,
                                                            BrowseConfigServiceDecorator configService) {
    this.consortiumSearchHelper = consortiumSearchHelper;
    this.configService = configService;
  }

  @Override
  protected SearchSourceBuilder getAnchorSearchQuery(BrowseRequest req, BrowseContext ctx) {
    log.debug("getAnchorSearchQuery:: by [request: {}]", req);
    var config = configService.getConfig(getBrowseType(), req.getBrowseOptionType());

    var browseField = getBrowseField(config);
    var termQueryBuilder = getQuery(ctx, config, termQuery(req.getTargetField(), ctx.getAnchor()));
    var query = consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(ctx, termQueryBuilder, req.getResource());
    var sortOrder = ctx.isBrowsingForward() ? ASC : DESC;
    return searchSource().query(query)
      .sort(fieldSort(browseField).order(sortOrder))
      .sort(fieldSort(req.getTargetField()).order(sortOrder))
      .size(ctx.getLimit(ctx.isBrowsingForward()))
      .from(0);
  }

  @Override
  protected SearchSourceBuilder getSearchQuery(BrowseRequest req, BrowseContext ctx, boolean isBrowsingForward) {
    log.debug("getSearchQuery:: by [request: {}, isBrowsingForward: {}]", req, isBrowsingForward);
    var config = configService.getConfig(getBrowseType(), req.getBrowseOptionType());

    var browseField = getBrowseField(config);
    var normalizedAnchor = ShelvingOrderCalculationHelper.calculate(ctx.getAnchor(), config.getShelvingAlgorithm());
    var query = consortiumSearchHelper.filterBrowseQueryForActiveAffiliation(ctx, getQuery(ctx, config, null),
      req.getResource());

    var sortOrder = isBrowsingForward ? ASC : DESC;
    return searchSource().query(query)
      .searchAfter(new Object[] {normalizedAnchor.toLowerCase(ROOT), ctx.getAnchor().toLowerCase(ROOT)})
      .sort(fieldSort(browseField).order(sortOrder))
      .sort(fieldSort(req.getTargetField()).order(sortOrder))
      .size(ctx.getLimit(isBrowsingForward) + 1)
      .from(0);
  }

  /**
   * Returns the type of browsing supported by this service.
   *
   * @return the {@link BrowseType} for this service
   */
  protected abstract BrowseType getBrowseType();

  /**
   * Returns the field name used for type identification in the browse service.
   *
   * @return the type ID field name
   */
  protected abstract String getTypeIdField();

  protected Integer getTotalRecords(BrowseContext ctx, R resource, Function<R, Set<InstanceSubResource>> func) {
    return consortiumSearchHelper.filterSubResourcesForConsortium(ctx, resource, func)
      .stream()
      .map(InstanceSubResource::getCount)
      .reduce(0, Integer::sum);
  }

  private QueryBuilder getQuery(BrowseContext ctx, BrowseConfig config, TermQueryBuilder anchorQuery) {
    var typeIds = config.getTypeIds();
    var typeIdsEmpty = CollectionUtils.isEmpty(config.getTypeIds());
    if (typeIdsEmpty && ctx.getFilters().isEmpty()) {
      if (anchorQuery != null) {
        return anchorQuery;
      }
      return matchAllQuery();
    } else {
      var boolQueryMain = boolQuery();
      if (!typeIdsEmpty) {
        var boolQuery = boolQuery();
        for (var typeId : typeIds) {
          boolQuery.should(termQuery(getTypeIdField(), typeId.toString()));
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

}
