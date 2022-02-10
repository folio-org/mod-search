package org.folio.search.utils;

import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.toMap;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.folio.search.utils.SearchUtils.getPathToFulltextPlainValue;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation.Bucket;
import org.elasticsearch.search.aggregations.bucket.terms.IncludeExclude;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedTerms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLTermNode;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BrowseUtils {

  public static final String CALL_NUMBER_BROWSING_FIELD = "callNumber";
  public static final String SUBJECT_BROWSING_FIELD = "subject";
  public static final String AUTHORITY_BROWSING_FIELD = "headingRef";
  public static final String SUBJECT_AGGREGATION_NAME = "subjects";

  /**
   * Extracts anchor call number from {@link CQLNode} object.
   *
   * @param node - {@link CQLNode} object to analyze
   * @return anchor call-number as {@link String} value
   */
  public static String getAnchorCallNumber(CQLNode node) {
    if (node instanceof CQLTermNode) {
      var termNode = (CQLTermNode) node;
      if (CALL_NUMBER_BROWSING_FIELD.equals(termNode.getIndex())) {
        return termNode.getTerm();
      }
    }

    if (node instanceof CQLBooleanNode) {
      var boolNode = (CQLBooleanNode) node;
      var rightAnchorCallNumber = getAnchorCallNumber(boolNode.getLeftOperand());
      return rightAnchorCallNumber != null ? rightAnchorCallNumber : getAnchorCallNumber(boolNode.getRightOperand());
    }

    return null;
  }

  /**
   * Get subject count from count search request.
   *
   * @param searchResponse - search response as {@link SearchResponse} object.
   * @return map with key as the subject name, value as the related subject count.
   */
  public static Map<String, Long> getSubjectCounts(SearchResponse searchResponse) {
    return Optional.ofNullable(searchResponse)
      .map(SearchResponse::getAggregations)
      .map(aggregations -> aggregations.get(SUBJECT_AGGREGATION_NAME))
      .filter(ParsedTerms.class::isInstance)
      .map(ParsedTerms.class::cast)
      .map(ParsedTerms::getBuckets)
      .stream()
      .flatMap(Collection::stream)
      .collect(toMap(Bucket::getKeyAsString, Bucket::getDocCount));
  }

  /**
   * Creates subject count query from given subject list.
   *
   * @param subjects - list with subjects
   * @return search source for subject counting
   */
  public static SearchSourceBuilder getSubjectCountsQuery(Collection<String> subjects) {
    var lowercaseSubjects = subjects.stream().map(subject -> subject.toLowerCase(ROOT)).toArray(String[]::new);
    var aggregation = AggregationBuilders.terms(SUBJECT_AGGREGATION_NAME)
      .size(subjects.size()).field(getPathToFulltextPlainValue(SUBJECT_AGGREGATION_NAME))
      .includeExclude(new IncludeExclude(lowercaseSubjects, null));
    return searchSource().query(matchAllQuery()).size(0).from(0).aggregation(aggregation);
  }
}
