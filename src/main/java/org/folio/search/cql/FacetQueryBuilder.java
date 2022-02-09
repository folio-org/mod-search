package org.folio.search.cql;

import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.folio.search.utils.SearchQueryUtils.isBoolQuery;
import static org.folio.search.utils.SearchQueryUtils.isDisjunctionFilterQuery;
import static org.folio.search.utils.SearchQueryUtils.isFilterQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.IncludeExclude;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.Pair;
import org.folio.search.model.service.CqlFacetRequest;
import org.folio.search.model.types.SearchType;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.utils.SearchUtils;
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
   * Provides list of aggregations for passed {@link CqlFacetRequest} and elasticsearch query.
   *
   * @param request facet request as {@link CqlFacetRequest}
   * @param query elasticsearch query as {@link QueryBuilder}
   * @return {@link List} with elasticsearch {@link AggregationBuilder} values
   */
  public List<AggregationBuilder> getFacetAggregations(CqlFacetRequest request, QueryBuilder query) {
    return request.getFacet().stream()
      .map(this::getFacetFieldAndLimitAsPair)
      .map(facet -> getFacetAggregation(request, query, facet))
      .flatMap(Collection::stream)
      .collect(toList());
  }

  private List<AggregationBuilder> getFacetAggregation(CqlFacetRequest request,
    QueryBuilder query, Pair<String, Integer> facetAndLimit) {
    var field = facetAndLimit.getFirst();
    validateFacetField(field, request.getResource());
    var filterAndFacetTerms = getFilterQueryAndFacetTerms(field, query);
    return filterAndFacetTerms.getFirst() != null
      ? singletonList(filterAggregation(filterAndFacetTerms, facetAndLimit))
      : getTermsAggs(field, field, facetAndLimit.getSecond(), filterAndFacetTerms.getSecond());
  }

  private void validateFacetField(String facetField, String resource) {
    var facetFieldDescription = searchFieldProvider.getPlainFieldByPath(resource, facetField)
      .filter(fieldDescription -> fieldDescription.hasType(SearchType.FACET))
      .orElse(null);
    if (facetFieldDescription == null) {
      throw new RequestValidationException("Invalid facet value", FACET_KEY, facetField);
    }
  }

  private Pair<String, Integer> getFacetFieldAndLimitAsPair(String facet) {
    if (facet == null) {
      throw new RequestValidationException("Facet name cannot be null", FACET_KEY, null);
    }
    var matcher = FACET_FORMAT_REGEX.matcher(facet.trim());
    if (matcher.matches()) {
      var name = matcher.group(1);
      return matcher.group(3) == null ? Pair.of(name, DEFAULT_FACET_SIZE) : Pair.of(name, parseInt(matcher.group(3)));
    }

    throw new RequestValidationException(
      "Invalid facet name format, must be '{facetName}' or '{facetName}:{facetLimit}'", FACET_KEY, facet);
  }

  private static Pair<BoolQueryBuilder, List<String>> getFilterQueryAndFacetTerms(String field, QueryBuilder query) {
    if (!isBoolQuery(query)) {
      return Pair.of(null, emptyList());
    }

    var facetFilterQuery = boolQuery();
    var facetTerms = new ArrayList<String>();

    for (var filterQuery : ((BoolQueryBuilder) query).filter()) {
      if (isFilterQuery(filterQuery, field::equals)) {
        getValueFromFilerQuery(filterQuery).ifPresent(facetTerms::add);
      } else if (isDisjunctionFilterQuery(filterQuery, field::equals)) {
        ((BoolQueryBuilder) filterQuery).should().stream()
          .map(FacetQueryBuilder::getValueFromFilerQuery)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .forEach(facetTerms::add);
      } else {
        facetFilterQuery.filter(filterQuery);
      }
    }

    return isNotEmpty(facetFilterQuery.filter()) ? Pair.of(facetFilterQuery, facetTerms) : Pair.of(null, facetTerms);
  }

  private static AggregationBuilder filterAggregation(
    Pair<BoolQueryBuilder, List<String>> filterAndTerms, Pair<String, Integer> facetAndLimit) {
    var field = facetAndLimit.getFirst();
    var filterAggregation = filter(field, filterAndTerms.getFirst());
    getTermsAggs(NESTED_TERMS_AGG_NAME, field, facetAndLimit.getSecond(), filterAndTerms.getSecond())
      .forEach(filterAggregation::subAggregation);
    return filterAggregation;
  }

  private static TermsAggregationBuilder termsAgg(String name, String field, int size) {
    return AggregationBuilders.terms(name).field(field).size(size);
  }

  private static List<AggregationBuilder> getTermsAggs(String name, String field, int size, List<String> terms) {
    if (isEmpty(terms)) {
      return singletonList(termsAgg(name, field, size));
    }

    var termsArray = terms.toArray(String[]::new);
    var termsSize = termsArray.length;
    var includeTerms = new IncludeExclude(termsArray, null);
    if (size <= termsSize) {
      return singletonList(termsAgg(SearchUtils.SELECTED_AGG_PREFIX + name, field, size).includeExclude(includeTerms));
    }

    return List.of(
      termsAgg(name, field, size - termsSize).includeExclude(new IncludeExclude(null, termsArray)),
      termsAgg(SearchUtils.SELECTED_AGG_PREFIX + name, field, termsSize).includeExclude(includeTerms));
  }

  private static Optional<String> getValueFromFilerQuery(QueryBuilder query) {
    return query instanceof TermQueryBuilder ? ofNullable((String) ((TermQueryBuilder) query).value()) : empty();
  }
}
