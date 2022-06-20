package org.folio.search.service.browse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_BROWSING_FIELD;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;

import java.util.List;
import java.util.stream.Stream;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.index.query.QueryBuilder;

@UnitTest
@ExtendWith(MockitoExtension.class)
class BrowseContextProviderTest {

  @InjectMocks
  private BrowseContextProvider browseContextProvider;
  @Mock
  private CqlSearchQueryConverter cqlSearchQueryConverter;

  @Test
  void get_positive_forward() {
    var rangeQuery = "callNumber > A";
    var succeedingQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gt("A");
    when(cqlSearchQueryConverter.convert(rangeQuery, RESOURCE_NAME)).thenReturn(searchSource().query(succeedingQuery));

    var actual = browseContextProvider.get(request(rangeQuery));

    assertThat(actual).isEqualTo(BrowseContext.builder()
      .succeedingLimit(20).anchor("A").succeedingQuery(succeedingQuery).build());
  }

  @Test
  void get_positive_forwardWithFilters() {
    var rangeQuery = "callNumber > A and location == locationId";
    var filterQuery = termQuery("location", "locationId");
    var succeedingQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gt("A");
    when(cqlSearchQueryConverter.convert(rangeQuery, RESOURCE_NAME)).thenReturn(
      searchSource().query(boolQuery().must(succeedingQuery).filter(filterQuery)));

    var actual = browseContextProvider.get(request(rangeQuery));

    assertThat(actual).isEqualTo(BrowseContext.builder().filters(List.of(filterQuery))
      .succeedingLimit(20).anchor("A").succeedingQuery(succeedingQuery).build());
  }

  @Test
  void get_positive_backward() {
    var query = "callNumber < A";
    var precedingQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt("A");
    when(cqlSearchQueryConverter.convert(query, RESOURCE_NAME)).thenReturn(searchSource().query(precedingQuery));

    var actual = browseContextProvider.get(request(query));

    assertThat(actual).isEqualTo(BrowseContext.builder()
      .precedingLimit(20).anchor("A").precedingQuery(precedingQuery).build());
  }

  @Test
  void get_positive_aroundIncluding() {
    var query = "callNumber < A or callNumber > A";
    var precedingQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt("A");
    var succeedingQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte("A");
    when(cqlSearchQueryConverter.convert(query, RESOURCE_NAME)).thenReturn(
      searchSource().query(boolQuery().should(precedingQuery).should(succeedingQuery)));

    var actual = browseContextProvider.get(request(query));

    assertThat(actual).isEqualTo(BrowseContext.builder().anchor("A")
      .precedingQuery(precedingQuery).precedingLimit(9)
      .succeedingQuery(succeedingQuery).succeedingLimit(11)
      .build());
  }

  @Test
  void get_positive_aroundIncludingReverseDirections() {
    var query = "callNumber > A or callNumber < A";
    var precedingQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt("A");
    var succeedingQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte("A");
    when(cqlSearchQueryConverter.convert(query, RESOURCE_NAME)).thenReturn(
      searchSource().query(boolQuery().should(succeedingQuery).should(precedingQuery)));

    var actual = browseContextProvider.get(request(query));

    assertThat(actual).isEqualTo(BrowseContext.builder().anchor("A")
      .precedingQuery(precedingQuery).precedingLimit(9)
      .succeedingQuery(succeedingQuery).succeedingLimit(11)
      .build());
  }

  @Test
  void get_positive_aroundWithFilters() {
    var query = "(callNumber > A or callNumber < A) and location == locationId";
    var precedingQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt("A");
    var succeedingQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte("A");
    var filterQuery = termQuery("location", "locationId");
    when(cqlSearchQueryConverter.convert(query, RESOURCE_NAME)).thenReturn(searchSource().query(boolQuery()
      .must(boolQuery().should(succeedingQuery).should(precedingQuery)).filter(filterQuery)));

    var actual = browseContextProvider.get(request(query));

    assertThat(actual).isEqualTo(BrowseContext.builder().anchor("A")
      .filters(List.of(filterQuery))
      .precedingQuery(precedingQuery).precedingLimit(9)
      .succeedingQuery(succeedingQuery).succeedingLimit(11)
      .build());
  }

  @Test
  void get_negative_forwardWithSorting() {
    var query = "callNumber > A sortBy title";
    var esQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gt("A");
    when(cqlSearchQueryConverter.convert(query, RESOURCE_NAME)).thenReturn(searchSource().query(esQuery).sort("title"));

    var request = request(query);

    assertThatThrownBy(() -> browseContextProvider.get(request))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid CQL query for browsing, 'sortBy' is not supported")
      .satisfies(error -> {
        var exception = (RequestValidationException) error;
        assertThat(exception.getKey()).isEqualTo("query");
        assertThat(exception.getValue()).isEqualTo(query);
      });
  }

  @Test
  void get_negative_aroundWithDifferentAnchors() {
    var query = "callNumber > A or callNumber > B";
    var precedingQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt("A");
    var succeedingQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gt("B");
    when(cqlSearchQueryConverter.convert(query, RESOURCE_NAME)).thenReturn(searchSource()
      .query(boolQuery().should(precedingQuery).should(succeedingQuery)));

    var request = request(query);

    assertThatThrownBy(() -> browseContextProvider.get(request))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid CQL query for browsing. Anchors must be the same in range conditions.")
      .satisfies(error -> {
        var exception = (RequestValidationException) error;
        assertThat(exception.getKey()).isEqualTo("query");
        assertThat(exception.getValue()).isEqualTo(query);
      });
  }

  @MethodSource("invalidQueriesDataSource")
  @ParameterizedTest(name = "[{index}] {0}")
  @DisplayName("get_negative_parameterized")
  void get_negative_parameterized(String query, QueryBuilder esQuery) {
    when(cqlSearchQueryConverter.convert(query, RESOURCE_NAME)).thenReturn(searchSource().query(esQuery));
    var request = request(query);

    assertThatThrownBy(() -> browseContextProvider.get(request))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid CQL query for browsing.")
      .satisfies(error -> {
        var exception = (RequestValidationException) error;
        assertThat(exception.getKey()).isEqualTo("query");
        assertThat(exception.getValue()).isEqualTo(query);
      });
  }

  public static Stream<Arguments> invalidQueriesDataSource() {
    var filterQuery = termQuery("location", "locationId");
    return Stream.of(
      arguments("callNumber == A", termQuery(CALL_NUMBER_BROWSING_FIELD, "A")),
      arguments("unknown > A", rangeQuery("unknown").gt("A")),
      arguments("unknown > A and unknown < A", boolQuery()
        .must(rangeQuery("unknown").lt("A")).must(rangeQuery("unknown").gt("A"))),
      arguments("callNumber > A or callNumber == A", boolQuery()
        .should(rangeQuery(CALL_NUMBER_BROWSING_FIELD).gt("A")).should(termQuery(CALL_NUMBER_BROWSING_FIELD, "A"))),
      arguments("callNumber > A or callNumber > A", boolQuery()
        .should(rangeQuery(CALL_NUMBER_BROWSING_FIELD).gt("A")).should(rangeQuery(CALL_NUMBER_BROWSING_FIELD).gt("A"))),
      arguments("callNumber < A or callNumber < A", boolQuery()
        .should(rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt("A")).should(rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt("A"))),
      arguments("callNumber == (1 or 2) and location == locationId", boolQuery()
        .must(boolQuery().should(termQuery(CALL_NUMBER_BROWSING_FIELD, "1"))
          .should(termQuery(CALL_NUMBER_BROWSING_FIELD, "2"))).filter(filterQuery)),
      arguments("callNumber == 1 and location == locationId", boolQuery()
        .must(termQuery(CALL_NUMBER_BROWSING_FIELD, "1")).filter(filterQuery)),
      arguments("callNumber == (1 or 2)", boolQuery().must(boolQuery().must(boolQuery()
        .should(termQuery(CALL_NUMBER_BROWSING_FIELD, "1"))
        .should(termQuery(CALL_NUMBER_BROWSING_FIELD, "2")))))
    );
  }

  private static BrowseRequest request(String query) {
    return BrowseRequest.builder().targetField(CALL_NUMBER_BROWSING_FIELD)
      .limit(20).precedingRecordsCount(9).resource(RESOURCE_NAME).query(query).build();
  }
}
