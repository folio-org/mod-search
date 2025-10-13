package org.folio.search.cql;

import static org.folio.search.utils.SearchQueryUtils.isBoolQuery;
import static org.folio.search.utils.SearchQueryUtils.isDisjunctionFilterQuery;
import static org.folio.search.utils.SearchQueryUtils.isFilterQuery;
import static org.opensearch.index.query.QueryBuilders.boolQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.types.ResourceType;
import org.folio.search.model.types.SearchType;
import org.folio.search.service.consortium.ConsortiumSearchHelper;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.RangeQueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.CQLTermNode;

/**
 * Convert a CQL query into a elasticsearch query.
 * Contextual Query Language (CQL) Specification: <a href="https://www.loc.gov/standards/sru/cql/spec.html">https://www.loc.gov/standards/sru/cql/spec.html</a>
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
  public SearchSourceBuilder convert(String query, ResourceType resource) {
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
  public SearchSourceBuilder convertForConsortia(String query, ResourceType resource) {
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
  public SearchSourceBuilder convertForConsortia(String query, ResourceType resource, String tenantId) {
    var sourceBuilder = convert(query, resource);
    var queryBuilder = consortiumSearchHelper
      .filterQueryForActiveAffiliation(sourceBuilder.query(), resource, tenantId);
    return sourceBuilder.query(queryBuilder);
  }

  public SearchSourceBuilder convertForConsortia(String query, ResourceType resource, boolean consortiumConsolidated) {
    var sourceBuilder = convert(query, resource);
    if (consortiumConsolidated) {
      return sourceBuilder;
    }

    var queryBuilder = consortiumSearchHelper.filterQueryForActiveAffiliation(sourceBuilder.query(), resource);
    return sourceBuilder.query(queryBuilder);
  }

  private QueryBuilder convertToQuery(CQLNode node, ResourceType resource) {
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

  private BoolQueryBuilder convertToBoolQuery(CQLBooleanNode node, ResourceType resource) {
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

  private BoolQueryBuilder flattenBoolQuery(CQLBooleanNode node, ResourceType resource,
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

  private QueryBuilder enhanceQuery(QueryBuilder query, ResourceType resource) {
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

    return collapseRangeQueries(query);
  }

  private BoolQueryBuilder collapseRangeQueries(BoolQueryBuilder query) {
    //get range queries grouped by field name
    var rangeFilters = query.filter().stream()
      .map(filter -> filter instanceof RangeQueryBuilder rq ? rq : null)
      .filter(Objects::nonNull)
      .collect(Collectors.groupingBy(RangeQueryBuilder::fieldName));

    //join range queries when there are 2 range queries for the same field
    var collapsedRangeFilters = rangeFilters.entrySet().stream()
      .map(entry -> entry.getValue().size() == 2 ? collapseRangeQueries(entry.getKey(), entry.getValue()) : null)
      .filter(Objects::nonNull)
      .toList();

    if (collapsedRangeFilters.isEmpty()) {
      return query;
    }

    //extract filters with field name different from ones that were collapsed
    var resultFilters = query.filter().stream()
      .filter(filter -> !(filter instanceof RangeQueryBuilder rangeFilter)
                        || collapsedRangeFilters.stream()
                          .noneMatch(collapsedFilter -> collapsedFilter.fieldName().equals(rangeFilter.fieldName())))
      .collect(Collectors.toList());
    //add collapsed filters
    resultFilters.addAll(collapsedRangeFilters);

    //clear filters to drop ones that were collapsed
    query.filter().clear();
    query.filter().addAll(resultFilters);
    return query;
  }

  /**
   * Construct new range query having range bounds from two incoming queries.
   *
   * @param fieldName     query field name.
   * @param sourceQueries two range queries with same field name.
   */
  private RangeQueryBuilder collapseRangeQueries(String fieldName, List<RangeQueryBuilder> sourceQueries) {
    var rangeQuery = QueryBuilders.rangeQuery(fieldName);
    var firstRange = sourceQueries.get(0);
    var secondRange = sourceQueries.get(1);

    if (firstRange.from() != null) {
      rangeQuery.from(firstRange.from())
        .includeLower(firstRange.includeLower())
        .to(secondRange.to())
        .includeUpper(secondRange.includeUpper());
    } else {
      rangeQuery.from(secondRange.from())
        .includeLower(secondRange.includeLower())
        .to(firstRange.to())
        .includeUpper(firstRange.includeUpper());
    }

    return rangeQuery;
  }

  private boolean isFilterInnerQuery(QueryBuilder query, Predicate<String> filterFieldPredicate) {
    return isFilterQuery(query, filterFieldPredicate) || isDisjunctionFilterQuery(query, filterFieldPredicate);
  }

  private boolean isFilterField(String fieldName, ResourceType resource) {
    return searchFieldProvider.getPlainFieldByPath(resource, fieldName)
      .filter(fieldDescription -> fieldDescription.hasType(SearchType.FILTER))
      .isPresent();
  }
}
