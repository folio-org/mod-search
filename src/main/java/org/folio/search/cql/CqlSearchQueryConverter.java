package org.folio.search.cql;

import static org.folio.search.utils.SearchQueryUtils.isBoolQuery;
import static org.folio.search.utils.SearchQueryUtils.isDisjunctionFilterQuery;
import static org.folio.search.utils.SearchQueryUtils.isFilterQuery;
import static org.folio.search.utils.SearchUtils.SHARED_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.TENANT_ID_FIELD_NAME;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.types.SearchType;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.spring.FolioExecutionContext;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.CQLTermNode;

/**
 * Convert a CQL query into a elasticsearch query.
 *
 * <p> Contextual Query Language (CQL) Specification:
 * <a href="https://www.loc.gov/standards/sru/cql/spec.html">https://www.loc.gov/standards/sru/cql/spec.html</a>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class CqlSearchQueryConverter {

  private final CqlQueryParser cqlQueryParser;
  private final CqlSortProvider cqlSortProvider;
  private final SearchFieldProvider searchFieldProvider;
  private final CqlTermQueryConverter cqlTermQueryConverter;
  private final FolioExecutionContext folioExecutionContext;
  private final ConsortiumTenantService consortiumTenantService;

  /**
   * Converts given CQL search query value to the elasticsearch {@link SearchSourceBuilder} object.
   *
   * @param query    cql query to parse
   * @param resource resource name
   * @return search source as {@link SearchSourceBuilder} object with query and sorting conditions
   */
  public SearchSourceBuilder convert(String query, String resource) {
    var cqlNode = cqlQueryParser.parseCqlQuery(query, resource);
    var queryBuilder = new SearchSourceBuilder();

    if (cqlNode instanceof CQLSortNode cqlSortNode) {
      cqlSortProvider.getSort(cqlSortNode, resource).forEach(queryBuilder::sort);
    }

    var boolQuery = convertToQuery(cqlNode, resource);
    var enhancedQuery = enhanceQuery(boolQuery, resource);
    return queryBuilder.query(enhancedQuery);
  }

  //todo(MSEARCH-576): may be reworked after implemented for browse/streamIds.
  // Implemented separately because it crashes 'browse/streamIds' functionality.

  /**
   * Converts given CQL search query value to the elasticsearch {@link SearchSourceBuilder} object.
   * Wraps base 'convert' and adds tenantId+shared filter in case of consortia mode
   *
   * @param query    cql query to parse
   * @param resource resource name
   * @return search source as {@link SearchSourceBuilder} object with query and sorting conditions
   */
  public SearchSourceBuilder convertForConsortia(String query, String resource) {
    var sourceBuilder = convert(query, resource);
    var queryBuilder = filterForActiveAffiliation(sourceBuilder.query());

    return sourceBuilder.query(queryBuilder);
  }

  /**
   * Converts given CQL search query value to the {@link CQLTermNode} object.
   * If query contains boolean operator then return the left term node
   *
   * @param query    cql query to parse
   * @param resource resource name
   * @return term node as {@link CQLTermNode} object with term value
   */
  public CQLTermNode convertToTermNode(String query, String resource) {
    var cqlNode = cqlQueryParser.parseCqlQuery(query, resource);
    return convertToTermNode(cqlNode);
  }

  private CQLTermNode convertToTermNode(CQLNode cqlNode) {
    if (cqlNode instanceof CQLBooleanNode cqlBooleanNode) {
      var leftNode = cqlBooleanNode.getLeftOperand();
      return convertToTermNode(leftNode);
    }
    return (CQLTermNode) cqlNode;
  }

  private QueryBuilder convertToQuery(CQLNode node, String resource) {
    var cqlNode = node;
    if (node instanceof CQLSortNode cqlSortNode) {
      cqlNode = cqlSortNode.getSubtree();
    }
    if (cqlNode instanceof CQLTermNode cqlTermNode) {
      return cqlTermQueryConverter.getQuery(cqlTermNode, resource);
    }
    if (cqlNode instanceof CQLBooleanNode cqlBooleanNode) {
      return convertToBoolQuery(cqlBooleanNode, resource);
    }
    throw new UnsupportedOperationException(String.format(
      "Failed to parse CQL query. Node with type '%s' is not supported.", node.getClass().getSimpleName()));
  }

  private BoolQueryBuilder convertToBoolQuery(CQLBooleanNode node, String resource) {
    var operator = node.getOperator();
    return switch (operator) {
      case OR -> flattenBoolQuery(node, resource, BoolQueryBuilder::should);
      case AND -> flattenBoolQuery(node, resource, BoolQueryBuilder::must);
      case NOT -> boolQuery()
        .must(convertToQuery(node.getLeftOperand(), resource))
        .mustNot(convertToQuery(node.getRightOperand(), resource));
      default -> throw new UnsupportedOperationException(String.format(
        "Failed to parse CQL query. Operator '%s' is not supported.", operator.name()));
    };
  }

  private BoolQueryBuilder flattenBoolQuery(CQLBooleanNode node, String resource,
                                            Function<BoolQueryBuilder, List<QueryBuilder>> conditionProvider) {
    var rightOperandQuery = convertToQuery(node.getRightOperand(), resource);
    var leftOperandQuery = convertToQuery(node.getLeftOperand(), resource);

    var boolQuery = boolQuery();
    if (isBoolQuery(leftOperandQuery)) {
      var leftOperandBoolQuery = (BoolQueryBuilder) leftOperandQuery;
      var leftBoolQueryConditions = conditionProvider.apply(leftOperandBoolQuery);
      if (leftBoolQueryConditions.size() >= 2) {
        var resultBoolQueryConditions = conditionProvider.apply(boolQuery);
        resultBoolQueryConditions.addAll(leftBoolQueryConditions);
        resultBoolQueryConditions.add(rightOperandQuery);
        return boolQuery;
      }
    }

    var conditions = conditionProvider.apply(boolQuery);
    conditions.add(leftOperandQuery);
    conditions.add(rightOperandQuery);
    return boolQuery;
  }

  private QueryBuilder filterForActiveAffiliation(QueryBuilder query) {
    var contextTenantId = folioExecutionContext.getTenantId();
    var centralTenantId = consortiumTenantService.getCentralTenant(contextTenantId);
    if (centralTenantId.isEmpty()) {
      return query;
    }

    var boolQuery = prepareBoolQueryForActiveAffiliation(query);
    addActiveAffiliationClauses(boolQuery, contextTenantId, centralTenantId.get());

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

  private void addActiveAffiliationClauses(BoolQueryBuilder boolQuery, String contextTenantId, String centralTenantId) {
    var affiliationShouldClauses = getAffiliationShouldClauses(contextTenantId, centralTenantId);
    if (boolQuery.should().isEmpty()) {
      affiliationShouldClauses.forEach(boolQuery::should);
    } else {
      var innerBoolQuery = boolQuery();
      affiliationShouldClauses.forEach(innerBoolQuery::should);
      boolQuery.must(innerBoolQuery);
    }
  }

  private LinkedList<QueryBuilder> getAffiliationShouldClauses(String contextTenantId, String centralTenantId) {
    var affiliationShouldClauses = new LinkedList<QueryBuilder>();
    addTenantIdAffiliationShouldClause(contextTenantId, centralTenantId, affiliationShouldClauses);
    addSharedAffiliationShouldClause(affiliationShouldClauses);
    return affiliationShouldClauses;
  }

  private void addTenantIdAffiliationShouldClause(String contextTenantId, String centralTenantId,
                                                  LinkedList<QueryBuilder> affiliationShouldClauses) {
    if (!contextTenantId.equals(centralTenantId)) {
      affiliationShouldClauses.add(termQuery(TENANT_ID_FIELD_NAME, contextTenantId));
    }
  }

  private void addSharedAffiliationShouldClause(LinkedList<QueryBuilder> affiliationShouldClauses) {
    affiliationShouldClauses.add(termQuery(SHARED_FIELD_NAME, true));
  }

  private QueryBuilder enhanceQuery(QueryBuilder query, String resource) {
    Predicate<String> filterFieldCheck = field -> isFilterField(field, resource);
    if (isDisjunctionFilterQuery(query, filterFieldCheck)) {
      return boolQuery().filter(query);
    }
    if (isBoolQuery(query)) {
      return enhanceBoolQuery((BoolQueryBuilder) query, filterFieldCheck);
    }

    return isFilterQuery(query, filterFieldCheck) ? boolQuery().filter(query) : query;
  }

  private BoolQueryBuilder enhanceBoolQuery(BoolQueryBuilder query, Predicate<String> filterFieldPredicate) {
    var mustQueryConditions = new ArrayList<QueryBuilder>();
    var mustConditions = query.must();
    for (var innerQuery : mustConditions) {
      if (isFilterInnerQuery(innerQuery, filterFieldPredicate)) {
        query.filter(innerQuery);
      } else {
        mustQueryConditions.add(innerQuery);
      }
    }
    mustConditions.clear();
    mustConditions.addAll(mustQueryConditions);
    return query;
  }

  private boolean isFilterInnerQuery(QueryBuilder query, Predicate<String> filterFieldPredicate) {
    return isFilterQuery(query, filterFieldPredicate) || isDisjunctionFilterQuery(query, filterFieldPredicate);
  }

  private boolean isFilterField(String fieldName, String resource) {
    return searchFieldProvider.getPlainFieldByPath(resource, fieldName)
      .filter(fieldDescription -> fieldDescription.hasType(SearchType.FILTER))
      .isPresent();
  }
}
