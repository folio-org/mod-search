package org.folio.search.service.converter;

import static java.lang.Integer.MAX_VALUE;
import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.folio.search.model.types.SearchType.FACET;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.folio.search.exception.ValidationException;
import org.folio.search.model.service.CqlFacetServiceRequest;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FacetQueryBuilderTest {

  private static final String FIELD = "field";

  @InjectMocks private FacetQueryBuilder facetQueryBuilder;
  @Mock private SearchFieldProvider searchFieldProvider;

  @Test
  void getFacetAggregations_positive_queryWithoutFilters() {
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));
    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FIELD), matchAllQuery());
    assertThat(actual).containsExactly(AggregationBuilders.terms(FIELD).field(FIELD).size(MAX_VALUE));
  }

  @Test
  void getFacetAggregations_positive_limitedFacetWithQueryWithoutFilters() {
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));
    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FIELD + ":5"), matchAllQuery());
    assertThat(actual).containsExactly(AggregationBuilders.terms(FIELD).field(FIELD).size(5));
  }

  @Test
  void getFacetAggregations_positive_boolQueryWithoutFilterByFacet() {
    var query = boolQuery().filter(termQuery("f1", "v1")).filter(termQuery("f2", "v2"));
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));
    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FIELD), query);
    assertThat(actual).containsExactly(AggregationBuilders.filter(FIELD, query)
      .subAggregation(AggregationBuilders.terms("values").field(FIELD).size(MAX_VALUE)));
  }

  @Test
  void getFacetAggregations_positive_boolQueryWithFilterByFacet() {
    var someFilter = termQuery("f1", "v1");
    var filterByFacetField = termQuery(FIELD, "v2");
    var query = boolQuery().filter(someFilter).filter(filterByFacetField);

    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));

    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FIELD), query);

    assertThat(actual).containsExactly(AggregationBuilders.filter(FIELD, boolQuery().filter(someFilter))
      .subAggregation(AggregationBuilders.terms("values").field(FIELD).size(MAX_VALUE)));
  }

  @Test
  void getFacetAggregations_positive_boolQueryWithFilterByFacetOnly() {
    var filterByFacetField = termQuery(FIELD, "v2");
    var query = boolQuery().filter(filterByFacetField);
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));
    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FIELD), query);
    assertThat(actual).containsExactly(AggregationBuilders.terms(FIELD).field(FIELD).size(MAX_VALUE));
  }

  @Test
  void getFacetAggregations_positive_disjunctionFilterBoolQuery() {
    var query = boolQuery().filter(boolQuery().should(termQuery(FIELD, "v1")).should(termQuery(FIELD, "v2")));
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));
    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FIELD), query);
    assertThat(actual).containsExactly(AggregationBuilders.terms(FIELD).field(FIELD).size(MAX_VALUE));
  }

  @Test
  void getFacetAggregation_negative_notRecognizedFilterByFacetField() {
    var query = boolQuery().filter(matchQuery(FIELD, "v2"));
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(of(keywordField(FACET)));
    var actual = facetQueryBuilder.getFacetAggregations(facetRequest(FIELD), query);
    assertThat(actual).containsExactly(AggregationBuilders.filter(FIELD, query)
      .subAggregation(AggregationBuilders.terms("values").field(FIELD).size(MAX_VALUE)));
  }

  @Test
  void getFacetAggregations_negative_invalidFacetName() {
    var request = facetRequest(FIELD);
    var query = matchAllQuery();
    assertThatThrownBy(() -> facetQueryBuilder.getFacetAggregations(request, query))
      .isInstanceOf(ValidationException.class)
      .hasMessage("Invalid facet value");
  }

  @Test
  void getFacetAggregations_negative_invalidFacetValue() {
    var request = facetRequest(FIELD + ":10.123");
    var query = matchAllQuery();
    assertThatThrownBy(() -> facetQueryBuilder.getFacetAggregations(request, query))
      .isInstanceOf(ValidationException.class)
      .hasMessage("Invalid facet name format, must be '{facetName}' or '{facetName}:{facetLimit}'");
  }

  @Test
  void getFacetAggregations_negative_nullValue() {
    var request = facetRequest((String) null);
    var query = matchAllQuery();
    assertThatThrownBy(() -> facetQueryBuilder.getFacetAggregations(request, query))
      .isInstanceOf(ValidationException.class)
      .hasMessage("Facet name cannot be null");
  }

  private static CqlFacetServiceRequest facetRequest(String... facets) {
    var rq = new CqlFacetServiceRequest();
    rq.setFacet(Arrays.asList(facets));
    rq.setTenantId(TENANT_ID);
    rq.setResource(RESOURCE_NAME);
    return rq;
  }
}
