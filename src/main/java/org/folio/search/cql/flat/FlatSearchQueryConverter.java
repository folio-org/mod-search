package org.folio.search.cql.flat;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.cql.CqlSortProvider;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.types.ResourceType;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;
import org.z3950.zing.cql.CQLAndNode;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLNotNode;
import org.z3950.zing.cql.CQLOrNode;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.CQLTermNode;

/**
 * Flat-family CQL → OpenSearch query converter.
 * Same CQL parsing as CqlSearchQueryConverter, but uses FlatCqlTermQueryConverter for term conversion.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class FlatSearchQueryConverter {

  private static final String INSTANCE_PREFIX = "instance.";
  private static final String HOLDING_PREFIX = "holding.";
  private static final String ITEM_PREFIX = "item.";

  private final FlatCqlTermQueryConverter flatTermQueryConverter;
  private final CqlSortProvider cqlSortProvider;

  public SearchSourceBuilder convert(String query, ResourceType resource) {
    try {
      var parser = new CQLParser();
      var node = parser.parse(query);
      var searchSourceBuilder = new SearchSourceBuilder();
      if (node instanceof CQLSortNode sortNode) {
        cqlSortProvider.getSort(sortNode, resource, FlatSearchQueryConverter::namespaceSortField)
          .forEach(searchSourceBuilder::sort);
      }
      var queryBuilder = convertNode(node, resource);
      return searchSourceBuilder.query(queryBuilder);
    } catch (Exception e) {
      throw new SearchServiceException("Failed to parse CQL query for flat search: " + query, e);
    }
  }

  private QueryBuilder convertNode(CQLNode node, ResourceType resource) {
    if (node instanceof CQLTermNode termNode) {
      return convertTermNode(termNode, resource);
    }
    if (node instanceof CQLBooleanNode booleanNode) {
      return convertBooleanNode(booleanNode, resource);
    }
    if (node instanceof CQLSortNode sortNode) {
      return convertNode(sortNode.getSubtree(), resource);
    }
    throw new SearchServiceException("Unsupported CQL node type: " + node.getClass().getSimpleName());
  }

  private QueryBuilder convertTermNode(CQLTermNode termNode, ResourceType resource) {
    var index = termNode.getIndex();
    if ("cql.allRecords".equals(index) && "1".equals(termNode.getTerm())) {
      return QueryBuilders.matchAllQuery();
    }
    if ("keyword".equals(index) && "*".equals(termNode.getTerm())) {
      return QueryBuilders.matchAllQuery();
    }
    return flatTermQueryConverter.getQuery(termNode, resource);
  }

  private QueryBuilder convertBooleanNode(CQLBooleanNode booleanNode, ResourceType resource) {
    var leftQuery = convertNode(booleanNode.getLeftOperand(), resource);
    var rightQuery = convertNode(booleanNode.getRightOperand(), resource);
    var boolQuery = new BoolQueryBuilder();

    if (booleanNode instanceof CQLAndNode) {
      boolQuery.must(leftQuery);
      boolQuery.must(rightQuery);
    } else if (booleanNode instanceof CQLOrNode) {
      boolQuery.should(leftQuery);
      boolQuery.should(rightQuery);
      boolQuery.minimumShouldMatch(1);
    } else if (booleanNode instanceof CQLNotNode) {
      boolQuery.must(leftQuery);
      boolQuery.mustNot(rightQuery);
    } else {
      throw new SearchServiceException("Unsupported boolean operator: " + booleanNode.getClass().getSimpleName());
    }

    return boolQuery;
  }

  private static String namespaceSortField(String field) {
    if (field.startsWith("_")
      || field.startsWith(INSTANCE_PREFIX)
      || field.startsWith(HOLDING_PREFIX)
      || field.startsWith(ITEM_PREFIX)) {
      return field;
    }
    if (field.startsWith("holdings.")) {
      return HOLDING_PREFIX + field.substring("holdings.".length());
    }
    if (field.startsWith("items.")) {
      return ITEM_PREFIX + field.substring("items.".length());
    }
    return INSTANCE_PREFIX + field;
  }
}
