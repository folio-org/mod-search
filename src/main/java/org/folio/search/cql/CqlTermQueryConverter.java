package org.folio.search.cql;

import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.lowerCase;
import static org.folio.search.utils.SearchUtils.ASTERISKS_SIGN;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.folio.search.cql.builders.TermQueryBuilder;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.exception.ValidationException;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.service.metadata.LocalSearchFieldProvider;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.utils.SearchUtils;
import org.opensearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.z3950.zing.cql.CQLTermNode;
import org.z3950.zing.cql.Modifier;

@Component
public class CqlTermQueryConverter {

  public static final String WILDCARD_OPERATOR = "wildcard";
  public static final String STRING_MODIFIER = "string";
  private static final String MATCH_ALL_CQL_QUERY = "cql.allRecords = 1";
  private static final String KEYWORD_ALL_CQL_QUERY = "keyword = *";

  private static final List<DateTimeFormatter> SUPPORTED_DATE_FORMATS = List.of(
    DateTimeFormatter.ISO_DATE,
    DateTimeFormatter.ISO_DATE_TIME,
    DateTimeFormatter.ISO_LOCAL_DATE,
    DateTimeFormatter.ISO_LOCAL_DATE_TIME,
    DateTimeFormatter.ISO_OFFSET_DATE,
    DateTimeFormatter.ISO_OFFSET_DATE_TIME,
    DateTimeFormatter.ISO_INSTANT
  );

  private final SearchFieldProvider searchFieldProvider;
  private final Map<String, TermQueryBuilder> termQueryBuilders;
  private final Map<String, SearchTermProcessor> searchTermProcessors;

  /**
   * Used by dependency injection framework.
   *
   * @param termQueryBuilders        - list with {@link TermQueryBuilder} beans
   * @param localSearchFieldProvider - {@link SearchFieldProvider} bean
   * @param searchTermProcessors     - map with {@link SearchTermProcessor} beans
   */
  public CqlTermQueryConverter(
    LocalSearchFieldProvider localSearchFieldProvider,
    List<TermQueryBuilder> termQueryBuilders,
    Map<String, SearchTermProcessor> searchTermProcessors) {
    this.termQueryBuilders = getTermQueryProvidersAsMap(termQueryBuilders);
    this.searchFieldProvider = localSearchFieldProvider;
    this.searchTermProcessors = searchTermProcessors;
  }

  /**
   * Provides Elasticsearch {@link QueryBuilder} object for the given termNode and resource name.
   *
   * @param termNode - CQL term node as {@link CQLTermNode} object
   * @param resource - resource name as {@link String} value
   * @return created Elasticsearch {@link QueryBuilder} object
   */
  public QueryBuilder getQuery(CQLTermNode termNode, String resource) {
    if (isMatchAllQuery(termNode.toCQL())) {
      return matchAllQuery();
    }

    var fieldIndex = searchFieldProvider.getModifiedField(termNode.getIndex(), resource);
    var fieldsList = searchFieldProvider.getFields(resource, fieldIndex);
    var fieldName = fieldsList.size() == 1 ? fieldsList.get(0) : fieldIndex;
    var optionalPlainFieldByPath = searchFieldProvider.getPlainFieldByPath(resource, fieldName);
    var searchTerm = getSearchTerm(termNode.getTerm(), optionalPlainFieldByPath);
    var comparator = isWildcardQuery(searchTerm) ? WILDCARD_OPERATOR : lowerCase(termNode.getRelation().getBase());

    var termQueryBuilder = termQueryBuilders.get(comparator);
    if (termQueryBuilder == null) {
      throw new UnsupportedOperationException(String.format(
        "Failed to parse CQL query. Comparator '%s' is not supported.", comparator));
    }

    var modifiers = termNode.getRelation().getModifiers().stream()
      .map(Modifier::getType)
      .toList();

    if (CollectionUtils.isNotEmpty(fieldsList)) {
      if (modifiers.contains(STRING_MODIFIER)) {
        var updatedFieldsList = getUpdatedFields(fieldsList);
        return termQueryBuilder.getQuery(searchTerm, resource, updatedFieldsList.toArray(String[]::new));
      }

      return termQueryBuilder.getQuery(searchTerm, resource, fieldsList.toArray(String[]::new));
    }

    var plainFieldByPath = optionalPlainFieldByPath.orElseThrow(() -> new RequestValidationException(
      "Invalid search field provided in the CQL query", "field", fieldName));
    var index = plainFieldByPath.getIndex();
    validateIndexFormat(index, termNode);

    return plainFieldByPath.hasFulltextIndex()
           ? termQueryBuilder.getFulltextQuery(searchTerm, fieldName, resource, modifiers)
           : termQueryBuilder.getTermLevelQuery(searchTerm, fieldName, resource, index);
  }

  private static List<String> getUpdatedFields(List<String> fieldsList) {
    return fieldsList.stream()
      .map(SearchUtils::updatePathForTermQueries)
      .toList();
  }

  private Object getSearchTerm(String term, Optional<PlainFieldDescription> plainFieldDescription) {
    return plainFieldDescription
      .map(PlainFieldDescription::getSearchTermProcessor)
      .map(searchTermProcessors::get)
      .map(searchTermProcessor -> searchTermProcessor.getSearchTerm(term))
      .orElse(term);
  }

  private static boolean isWildcardQuery(Object query) {
    return query instanceof String string && string.contains(ASTERISKS_SIGN);
  }

  private static Map<String, TermQueryBuilder> getTermQueryProvidersAsMap(List<TermQueryBuilder> termQueryBuilders) {
    var queryBuildersMap = new LinkedHashMap<String, TermQueryBuilder>();
    var errors = new LinkedHashMap<String, List<TermQueryBuilder>>();
    for (var queryBuilder : termQueryBuilders) {
      var supportedComparators = queryBuilder.getSupportedComparators();
      Assert.notEmpty(supportedComparators, String.format(
        "Supported comparators are not specified for query builder: %s", queryBuilder.getClass().getSimpleName()));
      for (var comparator : supportedComparators) {
        var termQueryBuilder = queryBuildersMap.get(comparator);
        if (termQueryBuilder != null) {
          errors.computeIfAbsent(comparator, v -> new ArrayList<>(List.of(termQueryBuilder))).add(queryBuilder);
        } else {
          queryBuildersMap.put(comparator, queryBuilder);
        }
      }
    }

    if (MapUtils.isNotEmpty(errors)) {
      var stringJoiner = new StringJoiner(", ");
      errors.forEach((c, v) -> {
        var buildersAsString = v.stream().map(e -> e.getClass().getSimpleName()).collect(joining(", "));
        stringJoiner.add(String.format("comparator '%s': %s", c, buildersAsString));
      });
      throw new IllegalStateException(String.format("Multiple TermQueryBuilder objects cannot be responsible "
        + "for the same comparator. Found issues: [%s]", stringJoiner));
    }

    return unmodifiableMap(queryBuildersMap);
  }

  private void validateIndexFormat(String index, CQLTermNode termNode) {
    var value = termNode.getTerm();
    if (index.equals("date") && !isValidDate(value)) {
      throw new ValidationException("Invalid date format", termNode.getIndex(), value);
    }
  }

  private boolean isValidDate(String value) {
    for (DateTimeFormatter dateFormat : SUPPORTED_DATE_FORMATS) {
      try {
        dateFormat.parse(value);
        return true;
      } catch (Exception ignored) {
        // do nothing
      }
    }
    return false;
  }

  private static boolean isMatchAllQuery(String cqlQuery) {
    return MATCH_ALL_CQL_QUERY.equals(cqlQuery) || KEYWORD_ALL_CQL_QUERY.equals(cqlQuery);
  }
}
