package org.folio.search.cql.flat;

import static java.util.Collections.unmodifiableMap;
import static org.apache.commons.lang3.StringUtils.lowerCase;
import static org.folio.search.utils.SearchUtils.ASTERISKS_SIGN;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.apache.lucene.search.join.ScoreMode;
import org.folio.search.cql.builders.TermQueryBuilder;
import org.folio.search.cql.flat.FieldLevelClassifier.ResourceLevel;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.join.query.HasChildQueryBuilder;
import org.springframework.stereotype.Component;
import org.z3950.zing.cql.CQLTermNode;
import org.z3950.zing.cql.Modifier;

/**
 * Flat-family equivalent of CqlTermQueryConverter.
 * Instance-level fields use V1's field resolution (search aliases, multilang expansion)
 * with an "instance." namespace prefix.
 * Holding/item fields use simple keyword queries wrapped in has_child.
 */
@Log4j2
@Component
public class FlatCqlTermQueryConverter {

  private static final String MATCH_ALL_CQL = "cql.allRecords = 1";
  private static final String KEYWORD_ALL_CQL = "keyword = *";
  private static final String INSTANCE_PREFIX = "instance.";
  private static final String WILDCARD_COMPARATOR = "wildcard";

  private final FieldLevelClassifier fieldLevelClassifier;
  private final SearchFieldProvider searchFieldProvider;
  private final Map<String, TermQueryBuilder> termQueryBuilders;

  public FlatCqlTermQueryConverter(
      FieldLevelClassifier fieldLevelClassifier,
      SearchFieldProvider searchFieldProvider,
      List<TermQueryBuilder> termQueryBuilders) {
    this.fieldLevelClassifier = fieldLevelClassifier;
    this.searchFieldProvider = searchFieldProvider;
    this.termQueryBuilders = buildTermQueryBuilderMap(termQueryBuilders);
  }

  public QueryBuilder getQuery(CQLTermNode termNode, ResourceType resource) {
    var cql = termNode.toCQL();
    if (MATCH_ALL_CQL.equals(cql) || KEYWORD_ALL_CQL.equals(cql)) {
      return matchAllQuery();
    }

    var fieldName = termNode.getIndex();
    var level = fieldLevelClassifier.classify(fieldName);

    if (level == ResourceLevel.INSTANCE) {
      return buildInstanceFieldQuery(termNode, resource);
    }

    // Holding/Item fields — simple keyword query wrapped in has_child
    var normalizedField = fieldLevelClassifier.normalizeField(fieldName);
    var innerQuery = buildSimpleFieldQuery(normalizedField, termNode);
    var childType = level.getJoinType();

    log.debug("getQuery:: wrapping in has_child [field: {} → {}, childType: {}]",
      fieldName, normalizedField, childType);

    return new HasChildQueryBuilder(childType, innerQuery, ScoreMode.None);
  }

  /**
   * Builds a query for instance-level fields using V1's field resolution.
   * Resolves search aliases and multilang expansions via SearchFieldProvider,
   * then prefixes all resolved field paths with "instance." for the namespaced index.
   */
  private QueryBuilder buildInstanceFieldQuery(CQLTermNode termNode, ResourceType resource) {
    var fieldName = termNode.getIndex();
    var term = termNode.getTerm();
    var comparator = isWildcardQuery(term) ? WILDCARD_COMPARATOR : lowerCase(termNode.getRelation().getBase());

    var modifiedField = searchFieldProvider.getModifiedField(fieldName, resource);
    var fieldsList = searchFieldProvider.getFields(resource, modifiedField);

    if (!fieldsList.isEmpty()) {
      var namespacedFields = fieldsList.stream()
        .map(f -> INSTANCE_PREFIX + f)
        .toArray(String[]::new);

      var queryBuilder = termQueryBuilders.get(comparator);
      if (queryBuilder != null) {
        var modifiers = termNode.getRelation().getModifiers().stream()
          .map(Modifier::getType)
          .toList();
        return queryBuilder.getQuery(term, resource, modifiers, namespacedFields);
      }
    }

    // Fallback for fields without search aliases: simple query on namespaced path
    var namespacedField = INSTANCE_PREFIX + modifiedField;
    log.debug("buildInstanceFieldQuery:: fallback simple query [field: {} → {}]", fieldName, namespacedField);
    return buildSimpleFieldQuery(namespacedField, termNode);
  }

  private QueryBuilder buildSimpleFieldQuery(String field, CQLTermNode termNode) {
    var term = termNode.getTerm();
    var comparator = termNode.getRelation().getBase().toLowerCase();

    if (term.contains(ASTERISKS_SIGN)) {
      return QueryBuilders.wildcardQuery(field, term.toLowerCase());
    }

    return switch (comparator) {
      case "==" -> QueryBuilders.termQuery(field, term);
      case "=" -> QueryBuilders.matchQuery(field, term);
      case "<>" -> new BoolQueryBuilder().mustNot(QueryBuilders.termQuery(field, term));
      case ">" -> QueryBuilders.rangeQuery(field).gt(term);
      case ">=" -> QueryBuilders.rangeQuery(field).gte(term);
      case "<" -> QueryBuilders.rangeQuery(field).lt(term);
      case "<=" -> QueryBuilders.rangeQuery(field).lte(term);
      case "all" -> QueryBuilders.matchQuery(field, term)
        .operator(org.opensearch.index.query.Operator.AND);
      case "any" -> QueryBuilders.matchQuery(field, term);
      default -> QueryBuilders.matchQuery(field, term);
    };
  }

  private static boolean isWildcardQuery(String term) {
    return term.contains(ASTERISKS_SIGN);
  }

  private static Map<String, TermQueryBuilder> buildTermQueryBuilderMap(List<TermQueryBuilder> builders) {
    var map = new LinkedHashMap<String, TermQueryBuilder>();
    for (var builder : builders) {
      for (var comparator : builder.getSupportedComparators()) {
        map.putIfAbsent(comparator, builder);
      }
    }
    return unmodifiableMap(map);
  }
}
