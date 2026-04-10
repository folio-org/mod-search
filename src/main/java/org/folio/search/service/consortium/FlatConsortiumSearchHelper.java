package org.folio.search.service.consortium;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.springframework.stereotype.Component;

/**
 * Flat-family consortium filtering. In the flat model, tenantId and shared are top-level fields,
 * so filtering uses simple term queries instead of nested queries.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class FlatConsortiumSearchHelper {

  private final ConsortiumTenantProvider consortiumTenantProvider;

  public QueryBuilder addConsortiumFilter(QueryBuilder query, String tenantId) {
    if (!consortiumTenantProvider.isConsortiumTenant(tenantId)) {
      return query;
    }

    var boolQuery = new BoolQueryBuilder();
    boolQuery.must(query);

    if (consortiumTenantProvider.isMemberTenant(tenantId)) {
      // Member tenants see their own records + shared records
      var tenantFilter = new BoolQueryBuilder()
        .should(QueryBuilders.termQuery("tenantId", tenantId))
        .should(QueryBuilders.termQuery("shared", true))
        .minimumShouldMatch(1);
      boolQuery.filter(tenantFilter);
    }
    // Central tenant sees all records (no filter needed)

    return boolQuery;
  }
}
