package org.folio.search.service.converter;

import static java.lang.Integer.MAX_VALUE;
import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.index.query.QueryBuilders.matchQuery;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.search.aggregations.AggregationBuilders.filter;
import static org.opensearch.search.aggregations.AggregationBuilders.terms;
import static org.folio.search.model.types.SearchType.FACET;
import static org.folio.search.utils.SearchUtils.SELECTED_AGG_PREFIX;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.array;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.opensearch.search.aggregations.bucket.terms.IncludeExclude;
import org.folio.search.cql.FacetQueryBuilder;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.service.CqlFacetRequest;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.utils.TestUtils;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FacetQueryBuilderTest {

  private static final String FIELD = "field";
  private static final String FACET_ALIAS = "facet.alias";

  @InjectMocks private FacetQueryBuilder facetQueryBuilder;
  @Mock private SearchFieldProvider searchFieldProvider;

  @BeforeEach
  void setUp() {
    when(searchFieldProvider.getModifiedField(any(), any())).thenAnswer(f -> f.getArguments()[0]);
  }

  @Test
  void getFacetAggregations_positive_queryWithoutFilters() {
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));
    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FIELD), matchAllQuery());
    assertThat(actual).containsExactly(terms(FIELD).field(FIELD).size(MAX_VALUE));
  }

  @Test
  void getFacetAggregations_positive_modifyFacetName() {
    var modifiedField = "newField";
    when(searchFieldProvider.getModifiedField(FIELD, RESOURCE_NAME)).thenReturn(modifiedField);
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, modifiedField)).thenReturn(of(keywordField(FACET)));
    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FIELD), matchAllQuery());
    assertThat(actual).containsExactly(terms(modifiedField).field(modifiedField).size(MAX_VALUE));
  }

  @Test
  void getFacetAggregations_positive_searchAlias() {
    when(searchFieldProvider.getFields(RESOURCE_NAME, FACET_ALIAS)).thenReturn(List.of(FIELD));
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));
    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FACET_ALIAS), matchAllQuery());
    assertThat(actual).containsExactly(terms(FACET_ALIAS).field(FIELD).size(MAX_VALUE));
  }

  @Test
  void getFacetAggregations_positive_limitedFacetWithQueryWithoutFilters() {
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));
    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FIELD + ":5"), matchAllQuery());
    assertThat(actual).containsExactly(terms(FIELD).field(FIELD).size(5));
  }

  @Test
  void getFacetAggregations_positive_limitedFacetWithQueryWithoutFiltersBySearchAlias() {
    when(searchFieldProvider.getFields(RESOURCE_NAME, FACET_ALIAS)).thenReturn(List.of(FIELD));
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));
    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FACET_ALIAS + ":5"), matchAllQuery());
    assertThat(actual).containsExactly(terms(FACET_ALIAS).field(FIELD).size(5));
  }

  @Test
  void getFacetAggregations_positive_boolQueryWithoutFilterByFacet() {
    var query = boolQuery().filter(termQuery("f1", "v1")).filter(termQuery("f2", "v2"));
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));
    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FIELD), query);
    assertThat(actual).containsExactly(filter(FIELD, query)
      .subAggregation(terms("values").field(FIELD).size(MAX_VALUE)));
  }

  @Test
  void getFacetAggregations_positive_boolQueryWithFilterByFacet() {
    var someFilter = termQuery("f1", "v1");
    var filterByFacetField = termQuery(FIELD, "v2");
    var query = boolQuery().filter(someFilter).filter(filterByFacetField);

    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));

    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FIELD), query);

    var include = new IncludeExclude(array("v2"), null);
    var exclude = new IncludeExclude(null, array("v2"));
    var expected = filter(FIELD, boolQuery().filter(someFilter))
      .subAggregation(terms("values").field(FIELD).size(MAX_VALUE - 1).includeExclude(exclude))
      .subAggregation(terms("selected_values").field(FIELD).size(1).includeExclude(include));
    assertThat(actual).containsExactly(expected);
  }

  @Test
  void getFacetAggregations_positive_boolQueryWithFilterByFacetAlias() {
    var someFilter = termQuery("f1", "v1");
    var filterByFacetField = termQuery(FIELD, "v2");
    var query = boolQuery().filter(someFilter).filter(filterByFacetField);

    when(searchFieldProvider.getFields(RESOURCE_NAME, FACET_ALIAS)).thenReturn(List.of(FIELD));
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));

    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FACET_ALIAS), query);

    var include = new IncludeExclude(array("v2"), null);
    var exclude = new IncludeExclude(null, array("v2"));
    var expected = filter(FACET_ALIAS, boolQuery().filter(someFilter))
      .subAggregation(terms("values").field(FIELD).size(MAX_VALUE - 1).includeExclude(exclude))
      .subAggregation(terms("selected_values").field(FIELD).size(1).includeExclude(include));
    assertThat(actual).containsExactly(expected);
  }

  @Test
  void getFacetAggregations_positive_boolQueryWithFilterByFacetOnly() {
    var filterByFacetField = termQuery(FIELD, "v2");
    var query = boolQuery().filter(filterByFacetField);
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));

    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FIELD), query);

    var include = new IncludeExclude(array("v2"), null);
    var exclude = new IncludeExclude(null, array("v2"));
    assertThat(actual).containsExactly(
      terms(FIELD).field(FIELD).size(MAX_VALUE - 1).includeExclude(exclude),
      terms(SELECTED_AGG_PREFIX + FIELD).field(FIELD).size(1).includeExclude(include));
  }

  @Test
  void getFacetAggregations_positive_boolQueryWithFilterByFacetAliasOnly() {
    when(searchFieldProvider.getFields(RESOURCE_NAME, FACET_ALIAS)).thenReturn(List.of(FIELD));
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));

    var filterByFacetField = termQuery(FIELD, "v2");
    var query = boolQuery().filter(filterByFacetField);
    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FACET_ALIAS), query);

    var include = new IncludeExclude(array("v2"), null);
    var exclude = new IncludeExclude(null, array("v2"));
    assertThat(actual).containsExactly(
      terms(FACET_ALIAS).field(FIELD).size(MAX_VALUE - 1).includeExclude(exclude),
      terms(SELECTED_AGG_PREFIX + FACET_ALIAS).field(FIELD).size(1).includeExclude(include));
  }

  @Test
  void getFacetAggregations_positive_disjunctionFilterBoolQuery() {
    var query = boolQuery().filter(boolQuery().should(termQuery(FIELD, "v1")).should(termQuery(FIELD, "v2")));
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));

    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FIELD), query);

    var include = new IncludeExclude(array("v1", "v2"), null);
    var exclude = new IncludeExclude(null, array("v1", "v2"));
    assertThat(actual).containsExactly(
      terms(FIELD).field(FIELD).size(MAX_VALUE - 2).includeExclude(exclude),
      terms(SELECTED_AGG_PREFIX + FIELD).field(FIELD).size(2).includeExclude(include));
  }

  @Test
  void getFacetAggregations_positive_disjunctionFilterQueryWithTermMoreThanSize() {
    var filterQuery = boolQuery();
    var values = array("v1", "v2", "v3", "v4", "v5");
    Arrays.stream(values).map(value -> termQuery(FIELD, value)).forEach(filterQuery::should);
    var query = boolQuery().filter(filterQuery);
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));

    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FIELD + ":5"), query);

    var include = new IncludeExclude(values, null);
    assertThat(actual).containsExactly(terms(SELECTED_AGG_PREFIX + FIELD).field(FIELD).size(5).includeExclude(include));
  }

  @Test
  void getFacetAggregations_positive_filterWithRangeQuery() {
    var query = boolQuery().filter(rangeQuery(FIELD).lt(0));
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));
    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FIELD + ":5"), query);
    assertThat(actual).containsExactly(terms(FIELD).field(FIELD).size(5));
  }

  @Test
  void getFacetAggregation_negative_notRecognizedFilterByFacetField() {
    var query = boolQuery().filter(matchQuery(FIELD, "v2"));
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));
    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FIELD), query);
    assertThat(actual).containsExactly(filter(FIELD, query)
      .subAggregation(terms("values").field(FIELD).size(MAX_VALUE)));
  }

  @Test
  void getFacetAggregations_negative_invalidFacetName() {
    var request = facetRequest(FIELD);
    var query = matchAllQuery();
    assertThatThrownBy(() -> facetQueryBuilder.getFacetAggregations(request, query))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid facet value");
  }

  @Test
  void getFacetAggregations_negative_invalidFacetValue() {
    var request = facetRequest(FIELD + ":10.123");
    var query = matchAllQuery();
    assertThatThrownBy(() -> facetQueryBuilder.getFacetAggregations(request, query))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid facet name format, must be '{facetName}' or '{facetName}:{facetLimit}'");
  }

  @Test
  void getFacetAggregations_negative_nullValue() {
    var request = facetRequest((String) null);
    var query = matchAllQuery();
    assertThatThrownBy(() -> facetQueryBuilder.getFacetAggregations(request, query))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Facet name cannot be null");
  }

  private static CqlFacetRequest facetRequest(String... facets) {
    return TestUtils.facetServiceRequest(RESOURCE_NAME, TENANT_ID, facets);
  }
}
