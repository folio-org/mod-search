package org.folio.search.cql;

import static java.util.Collections.emptyList;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.folio.search.utils.SearchUtils.updatePathForMultilangField;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.WildcardQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.folio.cql2pgjson.exception.CQLFeatureUnsupportedException;
import org.folio.cql2pgjson.model.CqlModifiers;
import org.folio.cql2pgjson.model.CqlSort;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.service.metadata.SearchFieldProvider;
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
@Log4j2
@Component
@RequiredArgsConstructor
public class CqlSearchQueryConverter {

  private static final String ASTERISKS_SIGN = "*";

  private final SearchFieldProvider searchFieldProvider;
  private final Map<String, SearchTermProcessor> searchTermProcessors;

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
        queryBuilder.sort("sort_" + sortIndex.getBase(), getSortOrder(modifiers.getCqlSort()));
      }
    }

    if (!searchRequest.isExpandAll()) {
      final String[] includes = searchFieldProvider
        .getSourceFields(searchRequest.getResource())
        .toArray(String[]::new);

      queryBuilder.fetchSource(includes, null);
    }

    return queryBuilder
      .query(convertToQuery(searchRequest, node))
      .from(searchRequest.getOffset())
      .size(searchRequest.getLimit());
  }

  private QueryBuilder convertToQuery(CqlSearchRequest request, CQLNode node) {
    var cqlNode = node;
    if (node instanceof CQLSortNode) {
      cqlNode = ((CQLSortNode) node).getSubtree();
    }
    if (cqlNode instanceof CQLTermNode) {
      return convertToTermQuery(request, (CQLTermNode) cqlNode);
    }
    if (cqlNode instanceof CQLBooleanNode) {
      return convertToBoolQuery(request, (CQLBooleanNode) cqlNode);
    }
    throw new UnsupportedOperationException("Unsupported node: " + node.getClass().getSimpleName());
  }

  private QueryBuilder convertToTermQuery(CqlSearchRequest request, CQLTermNode node) {
    var fieldName = node.getIndex();
    var resource = request.getResource();
    var fieldsGroup = searchFieldProvider.getFields(resource, fieldName);
    var fieldList = fieldsGroup.isEmpty() ? getFieldsForMultilangField(request, fieldName) : fieldsGroup;

    String term = getSearchTerm(node.getTerm(), fieldName, resource);

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
        throw unsupportedException(comparator);
    }
  }

  private String getSearchTerm(String term, String fieldName, String resource) {
    return searchFieldProvider.getFieldByPath(resource, fieldName)
      .filter(fieldDescription -> fieldDescription instanceof PlainFieldDescription)
      .map(PlainFieldDescription.class::cast)
      .map(PlainFieldDescription::getSearchTermProcessor)
      .map(searchTermProcessors::get)
      .map(searchTermProcessor -> searchTermProcessor.getSearchTerm(term))
      .orElse(term);
  }

  private List<String> getFieldsForMultilangField(CqlSearchRequest request, String fieldName) {
    return searchFieldProvider.getFieldByPath(request.getResource(), fieldName)
      .filter(fieldDescription -> fieldDescription instanceof PlainFieldDescription)
      .map(PlainFieldDescription.class::cast)
      .filter(PlainFieldDescription::isMultilang)
      .map(plainFieldDescription -> updatePathForMultilangField(fieldName))
      .map(Collections::singletonList)
      .orElse(emptyList());
  }

  private BoolQueryBuilder convertToBoolQuery(CqlSearchRequest request, CQLBooleanNode node) {
    var operator = node.getOperator();
    var boolQuery = boolQuery();
    switch (operator) {
      case OR:
        return boolQuery
          .should(convertToQuery(request, node.getLeftOperand()))
          .should(convertToQuery(request, node.getRightOperand()));
      case AND:
        return boolQuery
          .must(convertToQuery(request, node.getLeftOperand()))
          .must(convertToQuery(request, node.getRightOperand()));
      case NOT:
        return boolQuery
          .must(convertToQuery(request, node.getLeftOperand()))
          .mustNot(convertToQuery(request, node.getRightOperand()));
      default:
        throw unsupportedException(operator.name());
    }
  }

  private static QueryBuilder prepareElasticsearchQuery(List<String> fieldsGroup,
    Function<List<String>, QueryBuilder> groupQueryProducer, Supplier<QueryBuilder> defaultQuery) {
    return CollectionUtils.isNotEmpty(fieldsGroup) ? groupQueryProducer.apply(fieldsGroup) : defaultQuery.get();
  }

  private static QueryBuilder prepareQueryForFieldsGroup(List<String> fieldsGroup,
    Function<String, QueryBuilder> innerQueryProvider) {
    var boolQueryBuilder = boolQuery();
    if (fieldsGroup.size() == 1) {
      return innerQueryProvider.apply(updateMultilangFieldPath(fieldsGroup.get(0)));
    }
    fieldsGroup.forEach(field -> boolQueryBuilder.should(innerQueryProvider.apply(updateMultilangFieldPath(field))));
    return boolQueryBuilder;
  }

  private static String updateMultilangFieldPath(String field) {
    return (field.endsWith(".*")) ? field.substring(0, field.length() - 2) + ".src" : field;
  }

  private static WildcardQueryBuilder prepareWildcardQuery(String fieldName, String term) {
    return wildcardQuery(fieldName, term).rewrite("constant_score");
  }

  private static SortOrder getSortOrder(CqlSort cqlSort) {
    return cqlSort == CqlSort.DESCENDING ? SortOrder.DESC : SortOrder.ASC;
  }

  private static UnsupportedOperationException unsupportedException(String operator) {
    return new UnsupportedOperationException(String.format("Not implemented yet [operator: %s]", operator));
  }
}
