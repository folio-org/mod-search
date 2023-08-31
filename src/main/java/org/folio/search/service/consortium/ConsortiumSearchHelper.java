package org.folio.search.service.consortium;

import static org.apache.commons.lang3.StringUtils.EMPTY;
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

  private final FolioExecutionContext folioExecutionContext;
  private final ConsortiumTenantService consortiumTenantService;

  public QueryBuilder filterQueryForActiveAffiliation(QueryBuilder query) {
    var contextTenantId = folioExecutionContext.getTenantId();
    var centralTenantId = consortiumTenantService.getCentralTenant(contextTenantId);
    if (centralTenantId.isEmpty()) {
      return query;
    }

    return filterQueryForActiveAffiliation(query, contextTenantId, centralTenantId.get());
  }

  public QueryBuilder filterQueryForActiveAffiliation(QueryBuilder query, String tenantId, String centralTenantId) {
    return filterQueryForActiveAffiliation(EMPTY, query, tenantId, centralTenantId);
  }

  public QueryBuilder filterQueryForActiveAffiliation(String fieldPrefix, QueryBuilder query, String tenantId,
                                                      String centralTenantId) {
    var boolQuery = prepareBoolQueryForActiveAffiliation(query);
    addActiveAffiliationClauses(fieldPrefix, boolQuery, tenantId, centralTenantId);

    return boolQuery;
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

  private void addActiveAffiliationClauses(String fieldPrefix, BoolQueryBuilder boolQuery, String contextTenantId,
                                           String centralTenantId) {
    var affiliationShouldClauses = getAffiliationShouldClauses(fieldPrefix, contextTenantId, centralTenantId);
    if (boolQuery.should().isEmpty()) {
      affiliationShouldClauses.forEach(boolQuery::should);
    } else {
      var innerBoolQuery = boolQuery();
      affiliationShouldClauses.forEach(innerBoolQuery::should);
      boolQuery.must(innerBoolQuery);
    }
  }

  private LinkedList<QueryBuilder> getAffiliationShouldClauses(String fieldPrefix,
                                                               String contextTenantId, String centralTenantId) {
    var affiliationShouldClauses = new LinkedList<QueryBuilder>();
    addTenantIdAffiliationShouldClause(fieldPrefix, contextTenantId, centralTenantId, affiliationShouldClauses);
    addSharedAffiliationShouldClause(fieldPrefix, affiliationShouldClauses);
    return affiliationShouldClauses;
  }

  private void addTenantIdAffiliationShouldClause(String fieldPrefix, String contextTenantId, String centralTenantId,
                                                  LinkedList<QueryBuilder> affiliationShouldClauses) {
    if (!contextTenantId.equals(centralTenantId)) {
      affiliationShouldClauses.add(termQuery(fieldPrefix + TENANT_ID_FIELD_NAME, contextTenantId));
    }
  }

  private void addSharedAffiliationShouldClause(String fieldPrefix, LinkedList<QueryBuilder> affiliationShouldClauses) {
    affiliationShouldClauses.add(termQuery(fieldPrefix + SHARED_FIELD_NAME, true));
  }

  /**
   * Modifies query to support both 'instances.shared' filter and Active Affiliation.
   * 'instances.shared' filter have precedence over Active Affiliation so in case of 'false' value - only local records
   * will be returned (original query have only 'tenantId' additional filter).
  * */
  public QueryBuilder filterBrowseQueryForActiveAffiliation(BrowseContext browseContext, QueryBuilder query) {
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
      return filterQueryForActiveAffiliation("instances.", query, contextTenantId, centralTenantId.get());
    }

    var boolQuery = prepareBoolQueryForActiveAffiliation(query);
    if (boolQuery.should().isEmpty()) {
      boolQuery.minimumShouldMatch(null);
    }
    boolQuery.must(termQuery("instances.tenantId", contextTenantId));

    return boolQuery;
  }

  private void removeOriginalSharedFilterFromQuery(QueryBuilder queryBuilder) {
    if (queryBuilder instanceof BoolQueryBuilder bqb) {
      bqb.filter().removeIf(filter -> filter instanceof TermQueryBuilder tqb
        && tqb.fieldName().equals(BROWSE_SHARED_FILTER_KEY));
    }
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
    Predicate<InstanceSubResource> subResourcesFilter =
      sharedFilter.isPresent() && !sharedFilterValue(sharedFilter.get())
        ? subResource -> subResource.getTenantId().equals(contextTenantId)
        : subResource -> subResource.getTenantId().equals(contextTenantId) || subResource.getShared();
    return subResources.stream()
      .filter(subResourcesFilter)
      .collect(Collectors.toSet());
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

  private boolean sharedFilterValue(TermQueryBuilder sharedQuery) {
    return sharedQuery.value() instanceof Boolean boolValue && boolValue
      || sharedQuery.value() instanceof String stringValue && Boolean.parseBoolean(stringValue);
  }
}
