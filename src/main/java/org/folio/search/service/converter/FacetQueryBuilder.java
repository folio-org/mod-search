package org.folio.search.service.converter;

import static java.lang.Integer.parseInt;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.folio.search.utils.SearchUtils.isBoolQuery;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.folio.search.exception.ValidationException;
import org.folio.search.model.Pair;
import org.folio.search.model.service.CqlFacetServiceRequest;
import org.folio.search.model.types.SearchType;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FacetQueryBuilder {

  private static final String FACET_KEY = "facet";
  private static final String NESTED_TERMS_AGG_NAME = "values";
  private static final int DEFAULT_FACET_SIZE = Integer.MAX_VALUE;
  private static final Pattern FACET_FORMAT_REGEX = Pattern.compile("^([A-Za-z.]+)(:(\\d{1,10}))?$");

  private final SearchFieldProvider searchFieldProvider;

  /**
   * Provides list of aggregations for passed {@link CqlFacetServiceRequest} and elasticsearch query.
   *
   * @param request facet request as {@link CqlFacetServiceRequest}
   * @param query elasticsearch query as {@link QueryBuilder}
   * @return {@link List} with elasticsearch {@link AggregationBuilder} values
   */
  public List<AggregationBuilder> getFacetAggregations(CqlFacetServiceRequest request, QueryBuilder query) {
    return request.getFacet().stream()
      .map(this::getFacetFieldAndLimitAsPair)
      .map(facet -> getFacetAggregation(request, query, facet))
      .collect(toList());
  }

  private AggregationBuilder getFacetAggregation(CqlFacetServiceRequest request,
    QueryBuilder query, Pair<String, Integer> facetAndLimit) {
    var field = facetAndLimit.getLeft();
    validateFacetField(field, request.getResource());
    return getFilterQueryForFacet(field, query)
      .map(filter -> filterAggregation(filter, facetAndLimit))
      .orElse(termsAggregation(field, facetAndLimit));
  }

  private void validateFacetField(String facetField, String resource) {
    var facetFieldDescription = searchFieldProvider.getPlainFieldByPath(resource, facetField)
      .filter(fieldDescription -> fieldDescription.hasType(SearchType.FACET))
      .orElse(null);
    if (facetFieldDescription == null) {
      throw new ValidationException("Invalid facet value", FACET_KEY, facetField);
    }
  }

  private Pair<String, Integer> getFacetFieldAndLimitAsPair(String facet) {
    if (facet == null) {
      throw new ValidationException("Facet name cannot be null", FACET_KEY, null);
    }
    var matcher = FACET_FORMAT_REGEX.matcher(facet.trim());
    if (matcher.matches()) {
      var name = matcher.group(1);
      return matcher.group(3) == null ? Pair.of(name, DEFAULT_FACET_SIZE) : Pair.of(name, parseInt(matcher.group(3)));
    }

    throw new ValidationException(
      "Invalid facet name format, must be '{facetName}' or '{facetName}:{facetLimit}'", FACET_KEY, facet);
  }

  private static Optional<BoolQueryBuilder> getFilterQueryForFacet(String facetField, QueryBuilder query) {
    if (!isBoolQuery(query)) {
      return Optional.empty();
    }
    var facetFilterQuery = boolQuery();
    for (var filterQuery : ((BoolQueryBuilder) query).filter()) {
      if (!isFilterQueryByFacetField(filterQuery, facetField)) {
        facetFilterQuery.filter(filterQuery);
      }
    }
    return isNotEmpty(facetFilterQuery.filter()) ? Optional.of(facetFilterQuery) : Optional.empty();
  }

  private static boolean isFilterQueryByFacetField(QueryBuilder query, String field) {
    return (query instanceof TermQueryBuilder) && Objects.equals(((TermQueryBuilder) query).fieldName(), field);
  }

  private static AggregationBuilder filterAggregation(BoolQueryBuilder filter, Pair<String, Integer> facetAndLimit) {
    return AggregationBuilders.filter(facetAndLimit.getLeft(), filter)
      .subAggregation(termsAggregation(NESTED_TERMS_AGG_NAME, facetAndLimit));
  }

  private static TermsAggregationBuilder termsAggregation(String name, Pair<String, Integer> facetAndLimit) {
    return AggregationBuilders.terms(name)
      .field(facetAndLimit.getLeft())
      .size(facetAndLimit.getRight());
  }
}
