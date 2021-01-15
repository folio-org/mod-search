package org.folio.search.cql;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.folio.cql2pgjson.exception.CQLFeatureUnsupportedException;
import org.folio.cql2pgjson.model.CqlModifiers;
import org.folio.cql2pgjson.model.CqlSort;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.service.CqlSearchRequest;
import org.springframework.stereotype.Component;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.CQLTermNode;

/**
 * Convert a CQL query into a elasticsearch query.
 *
 * <p> Contextual Query Language (CQL) Specification:
 * <a href="https://www.loc.gov/standards/sru/cql/spec.html">https://www.loc.gov/standards/sru/cql/spec.html</a>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CqlSearchQueryConverter {

  private static final String ASTERISKS_SIGN = "*";

  /**
   * Parses {@link CqlSearchRequest} object to the elasticsearch.
   */
  public SearchSourceBuilder convert(CqlSearchRequest request) {
    var cqlQuery = request.getCqlQuery();
    try {
      var cqlNode = new CQLParser().parse(cqlQuery);
      return toCriteria(request, cqlNode);
    } catch (Exception e) {
      throw new SearchServiceException(String.format(
        "Failed to parse cql query [cql: '%s', resource: %s]", cqlQuery, request.getResource()), e);
    }
  }

  private SearchSourceBuilder toCriteria(CqlSearchRequest searchRequest, CQLNode node)
    throws CQLFeatureUnsupportedException {
    var queryBuilder = new SearchSourceBuilder();
    queryBuilder.trackTotalHits(true);

    if (node instanceof CQLSortNode) {
      for (var sortIndex : ((CQLSortNode) node).getSortIndexes()) {
        var modifiers = new CqlModifiers(sortIndex);
        queryBuilder.sort(sortIndex.getBase(), getSortOrder(modifiers.getCqlSort()));
      }
    }

    return queryBuilder
      .query(convertToQuery(node))
      .from(searchRequest.getOffset())
      .size(searchRequest.getLimit());
  }

  private QueryBuilder convertToQuery(CQLNode node) {
    var cqlNode = node;
    if (node instanceof CQLSortNode) {
      cqlNode = ((CQLSortNode) node).getSubtree();
    }
    if (cqlNode instanceof CQLTermNode) {
      return convertToTermQuery((CQLTermNode) cqlNode);
    }
    if (cqlNode instanceof CQLBooleanNode) {
      return convertToBoolQuery((CQLBooleanNode) cqlNode);
    }
    throw new UnsupportedOperationException("Unsupported node: " + node.getClass().getSimpleName());
  }

  private QueryBuilder convertToTermQuery(CQLTermNode node) {
    var fieldName = node.getIndex();
    var term = node.getTerm();
    if (term.contains(ASTERISKS_SIGN)) {
      return wildcardQuery(fieldName, term);
    }
    var comparator = StringUtils.lowerCase(node.getRelation().getBase());
    switch (comparator) {
      case "=":
        return termQuery(fieldName, term);
      case "adj":
      case "all":
      case "any":
      case "==":
        return matchQuery(fieldName, term);
      case "<>":
        return boolQuery().mustNot(termQuery(fieldName, term));
      case "<":
        return rangeQuery(fieldName).lt(term);
      case ">":
        return rangeQuery(fieldName).gt(term);
      case "<=":
        return rangeQuery(fieldName).lte(term);
      case ">=":
        return rangeQuery(fieldName).gte(term);
      default:
        throw unsupportedException(comparator);
    }
  }

  private BoolQueryBuilder convertToBoolQuery(CQLBooleanNode node) {
    var operator = node.getOperator();
    var boolQuery = boolQuery();
    switch (operator) {
      case OR:
        return boolQuery
          .should(convertToQuery(node.getLeftOperand()))
          .should(convertToQuery(node.getRightOperand()));
      case AND:
        return boolQuery
          .must(convertToQuery(node.getLeftOperand()))
          .must(convertToQuery(node.getRightOperand()));
      case NOT:
        return boolQuery
          .must(convertToQuery(node.getLeftOperand()))
          .mustNot(convertToQuery(node.getRightOperand()));
      default:
        throw unsupportedException(operator.name());
    }
  }

  private static SortOrder getSortOrder(CqlSort cqlSort) {
    return cqlSort == CqlSort.DESCENDING ? SortOrder.DESC : SortOrder.ASC;
  }

  private static UnsupportedOperationException unsupportedException(String operator) {
    return new UnsupportedOperationException(String.format("Not implemented yet [operator: %s]", operator));
  }
}
