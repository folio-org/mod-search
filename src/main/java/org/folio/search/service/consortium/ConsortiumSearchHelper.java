package org.folio.search.service.consortium;

import static org.folio.search.utils.SearchUtils.CONTRIBUTOR_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;
import static org.folio.search.utils.SearchUtils.SHARED_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.TENANT_ID_FIELD_NAME;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.spring.FolioExecutionContext;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConsortiumSearchHelper {

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
    var contextTenantId = folioExecutionContext.getTenantId();
    var centralTenantId = consortiumTenantService.getCentralTenant(contextTenantId);
    var sharedFilter = getBrowseSharedFilter(browseContext);
    if (centralTenantId.isEmpty()) {
      sharedFilter.ifPresent(filter -> browseContext.getFilters().remove(filter));
      return query;
    }

    removeOriginalSharedFilterFromQuery(query);

    var shared = sharedFilter.map(this::sharedFilterValue).orElse(true);
    if (Boolean.TRUE.equals(shared)) {
      return filterQueryForActiveAffiliation(query, contextTenantId, centralTenantId.get(), resource);
    }

    var boolQuery = prepareBoolQueryForActiveAffiliation(query);
    if (boolQuery.should().isEmpty()) {
      boolQuery.minimumShouldMatch(null);
    }
    boolQuery.must(termQuery(BROWSE_TENANT_FILTER_KEY, contextTenantId));

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
        subResourcesFilter = subResourcesFilter.or(InstanceSubResource::getShared);
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
                                           String centralTenantId, String resource) {
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
    return context.getFilters().stream()
      .map(filter ->
        filter instanceof TermQueryBuilder termFilter && termFilter.fieldName().equals(BROWSE_SHARED_FILTER_KEY)
        ? termFilter
        : null)
      .filter(Objects::nonNull)
      .findFirst();
  }

  private Optional<TermQueryBuilder> getBrowseTenantFilter(BrowseContext context) {
    return context.getFilters().stream()
      .map(filter ->
        filter instanceof TermQueryBuilder termFilter && termFilter.fieldName().equals(BROWSE_TENANT_FILTER_KEY)
        ? termFilter
        : null)
      .filter(Objects::nonNull)
      .findFirst();
  }

  private boolean sharedFilterValue(TermQueryBuilder sharedQuery) {
    return sharedQuery.value() instanceof Boolean boolValue && boolValue
      || sharedQuery.value() instanceof String stringValue && Boolean.parseBoolean(stringValue);
  }

  private String getFieldForResource(String fieldName, String resourceName) {
    if (resourceName.equals(CONTRIBUTOR_RESOURCE) || resourceName.equals(INSTANCE_SUBJECT_RESOURCE)) {
      return "instances." + fieldName;
    }
    return fieldName;
  }

  private String tenantFilterValue(TermQueryBuilder tenantQuery) {
    return String.valueOf(tenantQuery.value());
  }
}
