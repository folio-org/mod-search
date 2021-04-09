package org.folio.search.cql;

import static java.util.Collections.emptyList;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.elasticsearch.index.query.MultiMatchQueryBuilder.Type.CROSS_FIELDS;
import static org.elasticsearch.index.query.Operator.AND;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.folio.search.utils.SearchQueryUtils.isBoolQuery;
import static org.folio.search.utils.SearchQueryUtils.isDisjunctionFilterQuery;
import static org.folio.search.utils.SearchQueryUtils.isFilterQuery;
import static org.folio.search.utils.SearchUtils.updatePathForMultilangField;
import static org.folio.search.utils.SearchUtils.updatePathForTermQueries;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.WildcardQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.service.CqlSearchServiceRequest;
import org.folio.search.model.types.SearchType;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.springframework.stereotype.Component;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;
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
@Log4j2
@Component
@RequiredArgsConstructor
public class CqlSearchQueryConverter {

  private static final String ASTERISKS_SIGN = "*";

  private final CqlSortProvider cqlSortProvider;
  private final SearchFieldProvider searchFieldProvider;
  private final Map<String, SearchTermProcessor> searchTermProcessors;

  /**
   * Parses {@link CqlSearchServiceRequest} object to the elasticsearch.
   *
   * @param query cql query to parse
   * @param resource resource name
   * @return search source as {@link SearchSourceBuilder} object with query and sorting conditions
   */
  public SearchSourceBuilder convert(String query, String resource) {
    try {
      var cqlNode = new CQLParser().parse(query);
      return toCriteria(cqlNode, resource);
    } catch (CQLParseException | IOException e) {
      throw new SearchServiceException(String.format(
        "Failed to parse cql query [cql: '%s', resource: %s]", query, resource), e);
    }
  }

  private SearchSourceBuilder toCriteria(CQLNode node, String resource) {
    var queryBuilder = new SearchSourceBuilder();

    if (node instanceof CQLSortNode) {
      cqlSortProvider.getSort((CQLSortNode) node, resource).forEach(queryBuilder::sort);
    }

    return queryBuilder.query(enhanceQuery(convertToQuery(node, resource), resource));
  }

  private QueryBuilder convertToQuery(CQLNode node, String resource) {
    var cqlNode = node;
    if (node instanceof CQLSortNode) {
      cqlNode = ((CQLSortNode) node).getSubtree();
    }
    if (cqlNode instanceof CQLTermNode) {
      return convertToTermQuery((CQLTermNode) cqlNode, resource);
    }
    if (cqlNode instanceof CQLBooleanNode) {
      return convertToBoolQuery((CQLBooleanNode) cqlNode, resource);
    }
    throw new UnsupportedOperationException(String.format(
      "Failed to parse CQL query. Node with type '%s' is not supported.", node.getClass().getSimpleName()));
  }

  private QueryBuilder convertToTermQuery(CQLTermNode node, String resource) {
    var fieldName = node.getIndex();
    var fieldsGroup = searchFieldProvider.getFields(resource, fieldName);
    var fieldList = fieldsGroup.isEmpty() ? getFieldsForMultilangField(fieldName, resource) : fieldsGroup;

    var term = getSearchTerm(node.getTerm(), fieldName, resource);
    if (term.contains(ASTERISKS_SIGN)) {
      return prepareElasticsearchQuery(fieldList,
        fields -> prepareQueryForFieldsGroup(fields, field -> prepareWildcardQuery(field, term)),
        () -> prepareWildcardQuery(fieldName, term));
    }

    var comparator = StringUtils.lowerCase(node.getRelation().getBase());
    switch (comparator) {
      case "==":
        return prepareElasticsearchQuery(fieldList,
          fields -> prepareQueryForFieldsGroup(fields, field -> termQuery(field, term)),
          () -> termQuery(fieldName, term));
      case "=":
      case "adj":
      case "all":
        return prepareElasticsearchQuery(fieldList,
          fields -> multiMatchQuery(term, fields.toArray(String[]::new)).operator(AND).type(CROSS_FIELDS),
          () -> matchQuery(fieldName, term).operator(AND));
      case "any":
        return prepareElasticsearchQuery(fieldList,
          fields -> multiMatchQuery(term, fields.toArray(String[]::new)),
          () -> matchQuery(fieldName, term));
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
        throw new UnsupportedOperationException(String.format(
          "Failed to parse CQL query. Comparator '%s' is not supported.", comparator));
    }
  }

  private BoolQueryBuilder convertToBoolQuery(CQLBooleanNode node, String resource) {
    var operator = node.getOperator();
    switch (operator) {
      case OR:
        return flattenBoolQuery(node, resource, BoolQueryBuilder::should);
      case AND:
        return flattenBoolQuery(node, resource, BoolQueryBuilder::must);
      case NOT:
        return boolQuery()
          .must(convertToQuery(node.getLeftOperand(), resource))
          .mustNot(convertToQuery(node.getRightOperand(), resource));
      default:
        throw new UnsupportedOperationException(String.format(
          "Failed to parse CQL query. Operator '%s' is not supported.", operator.name()));
    }
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

  private static QueryBuilder prepareElasticsearchQuery(List<String> fieldsGroup,
    Function<List<String>, QueryBuilder> groupQueryProducer, Supplier<QueryBuilder> defaultQuery) {
    return isNotEmpty(fieldsGroup) ? groupQueryProducer.apply(fieldsGroup) : defaultQuery.get();
  }

  private static QueryBuilder prepareQueryForFieldsGroup(List<String> fieldsGroup,
    Function<String, QueryBuilder> innerQueryProvider) {
    var boolQueryBuilder = boolQuery();
    if (fieldsGroup.size() == 1) {
      return innerQueryProvider.apply(updatePathForTermQueries(fieldsGroup.get(0)));
    }
    fieldsGroup.forEach(field -> boolQueryBuilder.should(innerQueryProvider.apply(updatePathForTermQueries(field))));
    return boolQueryBuilder;
  }

  private String getSearchTerm(String term, String fieldName, String resource) {
    return searchFieldProvider.getPlainFieldByPath(resource, fieldName)
      .map(PlainFieldDescription::getSearchTermProcessor)
      .map(searchTermProcessors::get)
      .map(searchTermProcessor -> searchTermProcessor.getSearchTerm(term))
      .orElse(term);
  }

  private List<String> getFieldsForMultilangField(String fieldName, String resource) {
    return searchFieldProvider.getPlainFieldByPath(resource, fieldName)
      .filter(PlainFieldDescription::isMultilang)
      .map(plainFieldDescription -> updatePathForMultilangField(fieldName))
      .map(Collections::singletonList)
      .orElse(emptyList());
  }

  private static WildcardQueryBuilder prepareWildcardQuery(String fieldName, String term) {
    return wildcardQuery(fieldName, term).rewrite("constant_score");
  }
}
