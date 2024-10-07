package org.folio.search.cql;

import static org.folio.search.utils.SearchQueryUtils.isBoolQuery;
import static org.folio.search.utils.SearchQueryUtils.isDisjunctionFilterQuery;
import static org.folio.search.utils.SearchQueryUtils.isFilterQuery;
import static org.opensearch.index.query.QueryBuilders.boolQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.types.SearchType;
import org.folio.search.service.consortium.ConsortiumSearchHelper;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.RangeQueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;
import org.z3950.zing.cql.CQLBoolean;
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
  private final ConsortiumSearchHelper consortiumSearchHelper;

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

  /**
   * Converts given CQL search query value to the elasticsearch {@link SearchSourceBuilder} object.
   * Wraps base 'convert' and adds tenantId+shared filter in case of consortia mode
   *
   * @param query    cql query to parse
   * @param resource resource name
   * @return search source as {@link SearchSourceBuilder} object with query and sorting conditions
   */
  public SearchSourceBuilder convertForConsortia(String query, String resource) {
    return convertForConsortia(query, resource, false);
  }

  /**
   * Converts given CQL search query value to the elasticsearch {@link SearchSourceBuilder} object.
   * Wraps base 'convert' and adds active affiliation tenantId filter in case of consortia mode
   *
   * @param query    cql query to parse
   * @param resource resource name
   * @param tenantId active affiliation member tenant name
   * @return search source as {@link SearchSourceBuilder} object with query and sorting conditions
   */
  public SearchSourceBuilder convertForConsortia(String query, String resource, String tenantId) {
    var sourceBuilder = convert(query, resource);
    var queryBuilder = consortiumSearchHelper
      .filterQueryForActiveAffiliation(sourceBuilder.query(), resource, tenantId);
    return sourceBuilder.query(queryBuilder);
  }

  public SearchSourceBuilder convertForConsortia(String query, String resource, boolean consortiumConsolidated) {
    var sourceBuilder = convert(query, resource);
    if (consortiumConsolidated) {
      return sourceBuilder;
    }

    var queryBuilder = consortiumSearchHelper.filterQueryForActiveAffiliation(sourceBuilder.query(), resource);
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
      case OR -> flattenBoolQuery(node, resource, operator, BoolQueryBuilder::should);
      case AND -> flattenBoolQuery(node, resource, operator, BoolQueryBuilder::must);
      case NOT -> boolQuery()
        .must(convertToQuery(node.getLeftOperand(), resource))
        .mustNot(convertToQuery(node.getRightOperand(), resource));
      default -> throw new UnsupportedOperationException(String.format(
        "Failed to parse CQL query. Operator '%s' is not supported.", operator.name()));
    };
  }

  private BoolQueryBuilder flattenBoolQuery(CQLBooleanNode node, String resource, CQLBoolean operator,
                                            Function<BoolQueryBuilder, List<QueryBuilder>> conditionProvider) {
    var rightOperandQuery = convertToQuery(node.getRightOperand(), resource);
    var leftOperandQuery = convertToQuery(node.getLeftOperand(), resource);

    if (CQLBoolean.AND.equals(operator)
      && leftOperandQuery instanceof RangeQueryBuilder lr
      && rightOperandQuery instanceof RangeQueryBuilder rr
      && lr.fieldName().equals(rr.fieldName())) {
      return collapseRangeQueries(lr, rr, conditionProvider);
    }

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

  private BoolQueryBuilder collapseRangeQueries(RangeQueryBuilder leftRange, RangeQueryBuilder rightRange,
                                            Function<BoolQueryBuilder, List<QueryBuilder>> conditionProvider) {
    var rangeQuery = QueryBuilders.rangeQuery(leftRange.fieldName());

    if (leftRange.from() != null) {
      rangeQuery.from(leftRange.from())
        .includeLower(leftRange.includeLower())
        .to(rightRange.to())
        .includeUpper(rightRange.includeUpper());
    } else {
      rangeQuery.from(rightRange.from())
        .includeLower(rightRange.includeLower())
        .to(leftRange.to())
        .includeUpper(leftRange.includeUpper());
    }

    var boolQuery = boolQuery();
    var conditions = conditionProvider.apply(boolQuery);
    conditions.add(rangeQuery);
    return boolQuery;
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
