package org.folio.search.service.consortium;

import static org.folio.search.utils.SearchUtils.CONTRIBUTOR_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_CLASSIFICATION_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;
import static org.folio.search.utils.SearchUtils.SHARED_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.TENANT_ID_FIELD_NAME;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.nestedQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.search.join.ScoreMode;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.spring.FolioExecutionContext;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConsortiumSearchHelper {

  private static final Logger logger = LoggerFactory.getLogger(ConsortiumSearchHelper.class);
  private static final String BROWSE_SHARED_FILTER_KEY = "instances.shared";
  private static final String BROWSE_TENANT_FILTER_KEY = "instances.tenantId";

  private final FolioExecutionContext folioExecutionContext;
  private final ConsortiumTenantService consortiumTenantService;

  public QueryBuilder filterQueryForActiveAffiliation(QueryBuilder query, String resource) {
    var contextTenantId = folioExecutionContext.getTenantId();
    var centralTenantId = consortiumTenantService.getCentralTenant(contextTenantId);
    if (centralTenantId.isEmpty()) {
      return query;
    }

    return filterQueryForActiveAffiliation(query, contextTenantId, centralTenantId.get(), resource);
  }

  public QueryBuilder filterQueryForActiveAffiliation(QueryBuilder query, String tenantId,
                                                      String centralTenantId, String resource) {
    var boolQuery = prepareBoolQueryForActiveAffiliation(query);
    addActiveAffiliationClauses(boolQuery, tenantId, centralTenantId, resource);

    return boolQuery;
  }

  /**
   * Modifies query to support both 'instances.shared' filter and Active Affiliation.
   * 'instances.shared' filter have precedence over Active Affiliation so in case of 'false' value - only local records
   * will be returned (original query have only 'tenantId' additional filter).
   */
  public QueryBuilder filterBrowseQueryForActiveAffiliation(BrowseContext browseContext, QueryBuilder query,
                                                            String resource) {
    logger.debug("Filtering browse query for {}", resource);
    var contextTenantId = folioExecutionContext.getTenantId();
    var centralTenantId = consortiumTenantService.getCentralTenant(contextTenantId);
    var sharedFilter = getBrowseSharedFilter(browseContext);
    if (centralTenantId.isEmpty()) {
      sharedFilter.ifPresent(filter -> browseContext.getFilters().remove(filter));
      return query;
    }

    removeOriginalSharedFilterFromQuery(query);

    var boolQuery = prepareBoolQueryForActiveAffiliation(query);
    if (boolQuery.should().isEmpty()) {
      boolQuery.minimumShouldMatch(null);
    }
    var nestedBoolQuery = boolQuery();
    var nestedQuery = nestedQuery("instances", nestedBoolQuery, ScoreMode.None);
    boolQuery.must(nestedQuery);

    var shared = sharedFilter.map(this::sharedFilterValue).orElse(null);
    if (shared == null) {
      return filterQueryForActiveAffiliation(query, contextTenantId, centralTenantId.get(), resource);
    } else if (!shared) {
      nestedBoolQuery.must(termQuery(BROWSE_TENANT_FILTER_KEY, contextTenantId));
    }

    sharedFilter
      .map(this::sharedFilterValue)
      .ifPresent(sharedValue -> nestedBoolQuery.must(termQuery(BROWSE_SHARED_FILTER_KEY, sharedValue)));

    return boolQuery;
  }

  public <T> Set<InstanceSubResource> filterSubResourcesForConsortium(
    BrowseContext context, T resource,
    Function<T, Set<InstanceSubResource>> subResourceExtractor) {

    var subResources = subResourceExtractor.apply(resource);
    var contextTenantId = folioExecutionContext.getTenantId();
    var centralTenantId = consortiumTenantService.getCentralTenant(contextTenantId);
    if (centralTenantId.isEmpty() || contextTenantId.equals(centralTenantId.get())) {
      return subResources;
    }

    var sharedFilter = getBrowseSharedFilter(context);
    var tenantFilter = getBrowseTenantFilter(context);

    Predicate<InstanceSubResource> subResourcesFilter =
      subResource -> subResource.getTenantId().equals(contextTenantId);
    if (sharedFilter.isPresent()) {
      if (sharedFilterValue(sharedFilter.get())) {
        subResourcesFilter = InstanceSubResource::getShared;
      } else {
        subResourcesFilter = subResourcesFilter.and(instanceSubResource -> !instanceSubResource.getShared());
      }
    } else {
      if (tenantFilter.isEmpty()) {
        subResourcesFilter = subResourcesFilter.or(InstanceSubResource::getShared);
      } else {
        subResourcesFilter = subResource -> subResource.getTenantId().equals(tenantFilterValue(tenantFilter.get()));
      }
    }
    return subResources.stream()
      .filter(subResourcesFilter)
      .collect(Collectors.toSet());
  }

  public static Optional<TermQueryBuilder> getBrowseFilter(BrowseContext context, String filterKey) {
    return context.getFilters().stream()
      .map(filter -> getTermFilterForKey(filter, filterKey))
      .filter(Objects::nonNull)
      .findFirst();
  }

  public static List<Object> getBrowseFilterValues(BrowseContext context, String filterKey) {
    return context.getFilters().stream()
      .flatMap(filter -> getTermFiltersForKey(filter, filterKey))
      .filter(Objects::nonNull)
      .map(TermQueryBuilder::value)
      .toList();
  }

  private BoolQueryBuilder prepareBoolQueryForActiveAffiliation(QueryBuilder query) {
    BoolQueryBuilder boolQuery;
    if (query instanceof MatchAllQueryBuilder) {
      boolQuery = boolQuery();
    } else if (query instanceof BoolQueryBuilder bq) {
      boolQuery = bq;
    } else {
      boolQuery = boolQuery().must(query);
    }
    return boolQuery;
  }

  private void addActiveAffiliationClauses(BoolQueryBuilder boolQuery, String contextTenantId,
                                           String centralTenantId, String resource) {
    var affiliationShouldClauses = getAffiliationShouldClauses(contextTenantId, centralTenantId, resource);
    if (boolQuery.should().isEmpty() && boolQuery.filter().isEmpty()) {
      if (resource.equals(INSTANCE_SUBJECT_RESOURCE)) {
        var nestedBoolQuery = boolQuery();
        nestedBoolQuery.minimumShouldMatch(1);
        var nestedQuery = nestedQuery("instances", nestedBoolQuery, ScoreMode.None);
        boolQuery.must(nestedQuery);
        affiliationShouldClauses.forEach(nestedBoolQuery::should);
      } else {
        affiliationShouldClauses.forEach(boolQuery::should);
      }
    } else {
      var innerBoolQuery = boolQuery();
      if (resource.equals(INSTANCE_SUBJECT_RESOURCE)) {
        if (!boolQuery.filter().isEmpty()) {
          var nestedBoolQuery = boolQuery();
          var nestedQuery = nestedQuery("instances", nestedBoolQuery, ScoreMode.None);
          boolQuery.must(nestedQuery);
          boolQuery.filter().forEach(nestedBoolQuery::filter);
          if (boolQuery.filter().size() == 1
              && ((TermQueryBuilder) boolQuery.filter().get(0)).fieldName().equals("instances.shared")) {
            if (((TermQueryBuilder) boolQuery.filter().get(0)).value() == Boolean.FALSE) {
              nestedBoolQuery.filter(termQuery(getFieldForResource(TENANT_ID_FIELD_NAME, resource), contextTenantId));
            }
          }
          boolQuery.filter().clear();
        }
      } else {
        affiliationShouldClauses.forEach(innerBoolQuery::should);
      }
      boolQuery.must(innerBoolQuery);
    }
  }

  private LinkedList<QueryBuilder> getAffiliationShouldClauses(String contextTenantId, String centralTenantId,
                                                               String resource) {
    var affiliationShouldClauses = new LinkedList<QueryBuilder>();
    addTenantIdAffiliationShouldClause(contextTenantId, centralTenantId, affiliationShouldClauses,
      resource);
    addSharedAffiliationShouldClause(affiliationShouldClauses, resource);
    return affiliationShouldClauses;
  }

  private void addTenantIdAffiliationShouldClause(String contextTenantId, String centralTenantId,
                                                  LinkedList<QueryBuilder> affiliationShouldClauses, String resource) {
    if (!contextTenantId.equals(centralTenantId)) {
      affiliationShouldClauses.add(termQuery(getFieldForResource(TENANT_ID_FIELD_NAME, resource), contextTenantId));
    }
  }

  private void addSharedAffiliationShouldClause(LinkedList<QueryBuilder> affiliationShouldClauses,
                                                String resource) {
    affiliationShouldClauses.add(termQuery(getFieldForResource(SHARED_FIELD_NAME, resource), true));
  }

  private void removeOriginalSharedFilterFromQuery(QueryBuilder queryBuilder) {
    if (queryBuilder instanceof BoolQueryBuilder bqb) {
      bqb.filter().removeIf(filter -> filter instanceof TermQueryBuilder tqb
                                      && tqb.fieldName().equals(BROWSE_SHARED_FILTER_KEY));
    }
  }

  private Optional<TermQueryBuilder> getBrowseSharedFilter(BrowseContext context) {
    return getBrowseFilter(context, BROWSE_SHARED_FILTER_KEY);
  }

  private Optional<TermQueryBuilder> getBrowseTenantFilter(BrowseContext context) {
    return getBrowseFilter(context, BROWSE_TENANT_FILTER_KEY);
  }

  private static TermQueryBuilder getTermFilterForKey(QueryBuilder filter, String filterKey) {
    return filter instanceof TermQueryBuilder termFilter && termFilter.fieldName().equals(filterKey)
           ? termFilter
           : null;
  }

  private static Stream<TermQueryBuilder> getTermFiltersForKey(QueryBuilder filter, String filterKey) {
    if (filter instanceof TermQueryBuilder termFilter && termFilter.fieldName().equals(filterKey)) {
      return Stream.of(termFilter);
    } else if (filter instanceof BoolQueryBuilder boolFilter) {
      return boolFilter.should().stream().map(shouldFilter -> getTermFilterForKey(shouldFilter, filterKey));
    }
    return null;
  }

  private boolean sharedFilterValue(TermQueryBuilder sharedQuery) {
    return sharedQuery.value() instanceof Boolean boolValue && boolValue
           || sharedQuery.value() instanceof String stringValue && Boolean.parseBoolean(stringValue);
  }

  private String getFieldForResource(String fieldName, String resourceName) {
    if (resourceName.equals(CONTRIBUTOR_RESOURCE)
        || resourceName.equals(INSTANCE_SUBJECT_RESOURCE)
        || resourceName.equals(INSTANCE_CLASSIFICATION_RESOURCE)) {
      return "instances." + fieldName;
    }
    return fieldName;
  }

  private String tenantFilterValue(TermQueryBuilder tenantQuery) {
    return String.valueOf(tenantQuery.value());
  }
}
