package org.folio.search.cql;

import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.search.utils.CollectionUtils.findFirst;
import static org.folio.search.utils.SearchQueryUtils.isBoolQuery;
import static org.folio.search.utils.SearchQueryUtils.isDisjunctionFilterQuery;
import static org.folio.search.utils.SearchQueryUtils.isFilterQuery;
import static org.folio.search.utils.SearchUtils.SELECTED_AGG_PREFIX;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.search.aggregations.AggregationBuilders.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.Pair;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.service.CqlFacetRequest;
import org.folio.search.model.types.ResourceType;
import org.folio.search.model.types.SearchType;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.NestedQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.search.aggregations.AggregationBuilder;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.bucket.terms.IncludeExclude;
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
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
   * @param query   elasticsearch query as {@link QueryBuilder}
   * @return {@link List} with elasticsearch {@link AggregationBuilder} values
   */
  public List<AggregationBuilder> getFacetAggregations(CqlFacetRequest request, QueryBuilder query) {
    return request.getFacet().stream()
      .map(facet -> searchFieldProvider.getModifiedField(facet, request.getResource()))
      .map(facet -> getFacetFieldAndLimitAsPair(request.getResource(), facet))
      .map(facet -> getFacetAggregation(request, query, facet))
      .flatMap(Collection::stream)
      .toList();
  }

  private List<AggregationBuilder> getFacetAggregation(CqlFacetRequest request,
                                                       QueryBuilder query, Facet facet) {
    var field = facet.field();
    validateFacetField(facet, request.getResource());
    var filterAndFacetTerms = getFilterQueryAndFacetTerms(field, query);
    return filterAndFacetTerms.getFirst() != null
           ? singletonList(getFilterAggregation(filterAndFacetTerms, facet))
           : getTermsAggs(facet.aggregationName(), facet, filterAndFacetTerms.getSecond());
  }

  private void validateFacetField(Facet facet, ResourceType resource) {
    var facetField = facet.field();
    var facetFieldDescription = findFirst(searchFieldProvider.getFields(resource, facetField))
      .flatMap(fieldName -> getPlainFieldByPath(resource, fieldName))
      .or(() -> getPlainFieldByPath(resource, facetField))
      .orElse(null);

    if (facetFieldDescription == null) {
      throw new RequestValidationException("Invalid facet value", FACET_KEY, facetField);
    }
  }

  private Optional<PlainFieldDescription> getPlainFieldByPath(ResourceType resource, String fieldName) {
    return searchFieldProvider.getPlainFieldByPath(resource, fieldName)
      .filter(fieldDescription -> fieldDescription.hasType(SearchType.FACET));
  }

  private Facet getFacetFieldAndLimitAsPair(ResourceType resource, String facet) {
    if (facet == null) {
      throw new RequestValidationException("Facet name cannot be null", FACET_KEY, null);
    }

    var matcher = FACET_FORMAT_REGEX.matcher(facet.trim());
    if (matcher.matches()) {
      var facetName = matcher.group(1);
      var field = findFirst(searchFieldProvider.getFields(resource, facetName)).orElse(facetName);
      var size = matcher.group(3) == null ? DEFAULT_FACET_SIZE : parseInt(matcher.group(3));
      return new Facet(field, facetName, size);
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

    List<QueryBuilder> filters = new ArrayList<>(((BoolQueryBuilder) query).filter());
    var musts = ((BoolQueryBuilder) query).must();
    for (var must : musts) {
      if (must instanceof NestedQueryBuilder nestedQueryBuilder
          && nestedQueryBuilder.query() instanceof BoolQueryBuilder boolQueryBuilder) {
        filters.addAll(boolQueryBuilder.filter());
      }
    }
    for (var filterQuery : filters) {
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

  private static AggregationBuilder getFilterAggregation(
    Pair<BoolQueryBuilder, List<String>> filterAndTerms, Facet facet) {
    var filterAggregation = filter(facet.aggregationName(), filterAndTerms.getFirst());
    getTermsAggs(NESTED_TERMS_AGG_NAME, facet, filterAndTerms.getSecond()).forEach(filterAggregation::subAggregation);
    return filterAggregation;
  }

  private static List<AggregationBuilder> getTermsAggs(String name, Facet facet, List<String> terms) {
    var size = facet.size();
    var field = facet.field();
    if (isEmpty(terms)) {
      return singletonList(termsAgg(name, field, size));
    }

    var termsArray = terms.toArray(String[]::new);
    var termsSize = termsArray.length;
    var includeTerms = new IncludeExclude(termsArray, null);
    if (size <= termsSize) {
      return singletonList(termsAgg(SELECTED_AGG_PREFIX + name, field, size).includeExclude(includeTerms));
    }

    return List.of(
      termsAgg(name, field, size - termsSize).includeExclude(new IncludeExclude(null, termsArray)),
      termsAgg(SELECTED_AGG_PREFIX + name, field, termsSize).includeExclude(includeTerms));
  }

  private static TermsAggregationBuilder termsAgg(String name, String field, int size) {
    return AggregationBuilders.terms(name).field(field).size(size);
  }

  private static Optional<String> getValueFromFilerQuery(QueryBuilder query) {
    return query instanceof TermQueryBuilder ? ofNullable((String) ((TermQueryBuilder) query).value()) : empty();
  }

  private record Facet(String field, String aggregationName, Integer size) { }
}
