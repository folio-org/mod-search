package org.folio.search.service.consortium;

import static org.folio.search.utils.SearchUtils.SHARED_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.TENANT_ID_FIELD_NAME;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.nestedQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.folio.search.model.index.CallNumberResource;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.types.ResourceType;
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

  public static final String INSTANCES_PREFIX = "instances.";
  private static final Logger logger = LoggerFactory.getLogger(ConsortiumSearchHelper.class);
  private static final String BROWSE_SHARED_FILTER_KEY = "instances.shared";
  private static final String BROWSE_TENANT_FILTER_KEY = "instances.tenantId";
  private static final String BROWSE_LOCATION_FILTER_KEY = "instances.locationId";
  private final FolioExecutionContext folioExecutionContext;
  private final ConsortiumTenantService consortiumTenantService;

  public QueryBuilder filterQueryForActiveAffiliation(QueryBuilder query, ResourceType resource) {
    var contextTenantId = folioExecutionContext.getTenantId();
    var queryBuilder = filterQueryForActiveAffiliation(query, resource, contextTenantId);
    if (resource == ResourceType.INSTANCE_CALL_NUMBER) {
      modifyForCallNumbers(queryBuilder);
    }
    return queryBuilder;
  }

  /**
   * Modifies query to support both 'shared' filter and Active Affiliation.
   * Active Affiliation have precedence over 'shared' filter so in case of member tenant
   * modified query will have member 'tenantId' filter with shared=true).
   */
  public QueryBuilder filterQueryForActiveAffiliation(QueryBuilder query, ResourceType resource,
                                                      String contextTenantId) {
    var centralTenantId = consortiumTenantService.getCentralTenant(contextTenantId);
    if (centralTenantId.isEmpty() || resource.isLinkedDataResource() && centralTenantId.get().equals(contextTenantId)) {
      return query;
    }

    return filterQueryForActiveAffiliation(query, contextTenantId, centralTenantId.get(), resource);
  }

  public QueryBuilder filterQueryForActiveAffiliation(QueryBuilder query, String tenantId,
                                                      String centralTenantId, ResourceType resource) {
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
                                                            ResourceType resource) {
    logger.debug("Filtering browse query for {}", resource);
    var contextTenantId = folioExecutionContext.getTenantId();
    var centralTenantId = consortiumTenantService.getCentralTenant(contextTenantId);
    var sharedFilter = getBrowseSharedFilter(browseContext);
    if (centralTenantId.isEmpty()) {
      sharedFilter.ifPresent(filter -> browseContext.getFilters().remove(filter));
      return query;
    }

    QueryBuilder queryBuilder;

    removeOriginalSharedFilterFromQuery(query);

    var boolQuery = prepareBoolQueryForActiveAffiliation(query);
    if (boolQuery.should().isEmpty()) {
      boolQuery.minimumShouldMatch(null);
    }

    var shared = sharedFilter.map(this::sharedFilterValue).orElse(null);
    if (shared == null) {
      queryBuilder = filterQueryForActiveAffiliation(query, contextTenantId, centralTenantId.get(), resource);
    } else if (!shared) {
      boolQuery.must(termQuery(BROWSE_TENANT_FILTER_KEY, contextTenantId));
      sharedFilter
        .map(this::sharedFilterValue)
        .ifPresent(sharedValue -> boolQuery.must(termQuery(BROWSE_SHARED_FILTER_KEY, sharedValue)));
      queryBuilder = boolQuery;
    } else {
      sharedFilter
        .map(this::sharedFilterValue)
        .ifPresent(sharedValue -> boolQuery.must(termQuery(BROWSE_SHARED_FILTER_KEY, sharedValue)));
      queryBuilder = boolQuery;
    }

    if (resource == ResourceType.INSTANCE_CALL_NUMBER) {
      modifyForCallNumbers(queryBuilder);
    }

    return queryBuilder;
  }

  private void modifyForCallNumbers(QueryBuilder queryBuilder) {
    if (queryBuilder instanceof BoolQueryBuilder bqb) {
      var should = bqb.should().stream()
        .filter(TermQueryBuilder.class::isInstance)
        .filter(qb -> ((TermQueryBuilder) qb).fieldName().startsWith(INSTANCES_PREFIX))
        .toList();
      var filter = bqb.filter().stream()
        .filter(TermQueryBuilder.class::isInstance)
        .filter(qb -> ((TermQueryBuilder) qb).fieldName().startsWith(INSTANCES_PREFIX))
        .toList();
      var must = bqb.must().stream()
        .filter(TermQueryBuilder.class::isInstance)
        .filter(qb -> ((TermQueryBuilder) qb).fieldName().startsWith(INSTANCES_PREFIX))
        .toList();
      if (should.size() + filter.size() > 1) {
        var innerBoolQuery = boolQuery();
        innerBoolQuery.minimumShouldMatch(1);
        should.forEach(innerBoolQuery::should);
        filter.forEach(innerBoolQuery::filter);
        filter.forEach(queryBuilder1 -> bqb.filter().remove(queryBuilder1));
        should.forEach(queryBuilder1 -> bqb.should().remove(queryBuilder1));
        if (bqb.should().isEmpty()) {
          bqb.minimumShouldMatch(null);
        }
        bqb.must(nestedQuery("instances", innerBoolQuery, ScoreMode.None));
      } else if (must.size() + filter.size() > 1) {
        var innerBoolQuery = boolQuery();
        must.forEach(innerBoolQuery::must);
        filter.forEach(innerBoolQuery::filter);
        filter.forEach(queryBuilder1 -> bqb.filter().remove(queryBuilder1));
        must.forEach(queryBuilder1 -> bqb.must().remove(queryBuilder1));
        bqb.must(nestedQuery("instances", innerBoolQuery, ScoreMode.None));
      }
    }
  }

  public <T> Set<InstanceSubResource> filterSubResourcesForConsortium(
    BrowseContext context, T resource,
    Function<T, Set<InstanceSubResource>> subResourceExtractor) {

    var subResources = subResourceExtractor.apply(resource);
    var contextTenantId = folioExecutionContext.getTenantId();
    var centralTenantId = consortiumTenantService.getCentralTenant(contextTenantId);
    if (centralTenantId.isEmpty()) {
      return subResources.stream()
        .filter(instanceSubResource -> filterForCallNumbers(context, resource, instanceSubResource))
        .collect(Collectors.toSet());
    } else if (contextTenantId.equals(centralTenantId.get())) {
      return subResources.stream()
        .filter(InstanceSubResource::getShared)
        .collect(Collectors.toSet());
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
      .filter(instanceSubResource -> filterForCallNumbers(context, resource, instanceSubResource))
      .collect(Collectors.toSet());
  }

  protected Optional<TermQueryBuilder> getBrowseFilter(BrowseContext context, String filterKey) {
    return context.getFilters().stream()
      .map(filter -> getTermFilterForKey(filter, filterKey))
      .filter(Objects::nonNull)
      .findFirst();
  }

  private <T> boolean filterForCallNumbers(BrowseContext context, T resource, InstanceSubResource instanceSubResource) {
    if (resource instanceof CallNumberResource) {
      var locationIds = context.getFilters().stream()
        .map(filter -> getTermFilterForKey(filter, BROWSE_LOCATION_FILTER_KEY))
        .filter(Objects::nonNull)
        .map(TermQueryBuilder::value)
        .map(String::valueOf)
        .filter(StringUtils::isNotBlank)
        .toList();
      return locationIds.isEmpty() || locationIds.contains(instanceSubResource.getLocationId());
    }
    return true;
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
    boolQuery.minimumShouldMatch(1);
    return boolQuery;
  }

  private void addActiveAffiliationClauses(BoolQueryBuilder boolQuery, String contextTenantId,
                                           String centralTenantId, ResourceType resource) {
    var affiliationShouldClauses = getAffiliationShouldClauses(contextTenantId, centralTenantId, resource);
    if (boolQuery.should().isEmpty()) {
      affiliationShouldClauses.forEach(boolQuery::should);
    } else {
      var innerBoolQuery = boolQuery();
      affiliationShouldClauses.forEach(innerBoolQuery::should);
      boolQuery.must(innerBoolQuery);
    }
  }

  private LinkedList<QueryBuilder> getAffiliationShouldClauses(String contextTenantId, String centralTenantId,
                                                               ResourceType resource) {
    var affiliationShouldClauses = new LinkedList<QueryBuilder>();
    addTenantIdAffiliationShouldClause(contextTenantId, centralTenantId, affiliationShouldClauses,
      resource);
    addSharedAffiliationShouldClause(affiliationShouldClauses, resource);
    return affiliationShouldClauses;
  }

  private void addTenantIdAffiliationShouldClause(String contextTenantId, String centralTenantId,
                                                  LinkedList<QueryBuilder> affiliationShouldClauses,
                                                  ResourceType resource) {
    if (!contextTenantId.equals(centralTenantId)) {
      affiliationShouldClauses.add(termQuery(getFieldForResource(TENANT_ID_FIELD_NAME, resource), contextTenantId));
    }
  }

  private void addSharedAffiliationShouldClause(LinkedList<QueryBuilder> affiliationShouldClauses,
                                                ResourceType resource) {
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

  private boolean sharedFilterValue(TermQueryBuilder sharedQuery) {
    return sharedQuery.value() instanceof Boolean boolValue && boolValue
           || sharedQuery.value() instanceof String stringValue && Boolean.parseBoolean(stringValue);
  }

  private String getFieldForResource(String fieldName, ResourceType resourceName) {
    if (resourceName.equals(ResourceType.INSTANCE_CONTRIBUTOR)
        || resourceName.equals(ResourceType.INSTANCE_SUBJECT)
        || resourceName.equals(ResourceType.INSTANCE_CALL_NUMBER)
        || resourceName.equals(ResourceType.INSTANCE_CLASSIFICATION)) {
      return INSTANCES_PREFIX + fieldName;
    }
    return fieldName;
  }

  private String tenantFilterValue(TermQueryBuilder tenantQuery) {
    return String.valueOf(tenantQuery.value());
  }
}
