package org.folio.search.cql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.elasticsearch.search.sort.SortOrder.ASC;
import static org.elasticsearch.search.sort.SortOrder.DESC;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.service.metadata.MetadataResourceProvider;
import org.folio.search.service.metadata.SearchFieldProvider;
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

@UnitTest
@ExtendWith(MockitoExtension.class)
class CqlSearchQueryConverterTest {

  private static final List<String> TITLE_FIELDS = List.of("title.*", "source.*", "source");
  private static final List<String> SOURCE_FIELDS = List.of("title.*", "source.*");
  private static final String TITLE_SEARCH_TYPE = "title";

  @InjectMocks private CqlSearchQueryConverter cqlSearchQueryConverter;
  @Mock private SearchFieldProvider searchFieldProvider;
  @Mock private MetadataResourceProvider metadataResourceProvider;

  @DisplayName("should parse CQL query")
  @MethodSource("parseCqlQueryDataProvider")
  @ParameterizedTest(name = "[{index}] type={0}, cqlQuery={1}")
  void parseCqlQuery_positive_parameterized(
    @SuppressWarnings("unused") String testName, String cqlQuery, SearchSourceBuilder expected) {
    var request = CqlSearchRequest.of(RESOURCE_NAME, cqlQuery, null, 10, 0, false);
    when(searchFieldProvider.getSourceFields(RESOURCE_NAME)).thenReturn(SOURCE_FIELDS);
    var actual = cqlSearchQueryConverter.convert(request);

    assertThat(actual).isEqualTo(expected.size(10).from(0));
  }

  @DisplayName("should parse CQL query for 'title'")
  @MethodSource("parseCqlQueryTitleDataProvider")
  @ParameterizedTest(name = "[{index}] type={0}, cqlQuery={1}")
  void parseCqlQuery_positive_searchByTitleGroup(
    @SuppressWarnings("unused") String testName, String cqlQuery, SearchSourceBuilder expected) {
    var request = CqlSearchRequest.of(RESOURCE_NAME, cqlQuery, null, 10, 0, false);
    doReturn(TITLE_FIELDS).when(searchFieldProvider).getFields(RESOURCE_NAME, TITLE_SEARCH_TYPE);
    when(searchFieldProvider.getSourceFields(RESOURCE_NAME)).thenReturn(SOURCE_FIELDS);

    var actual = cqlSearchQueryConverter.convert(request);

    assertThat(actual).isEqualTo(expected.size(10).from(0));
    // Should fetch only basic fields, because expandAll is false
    assertThat(actual.fetchSource()).isNotNull();
    assertThat(actual.fetchSource().includes())
      .containsExactlyInAnyOrderElementsOf(SOURCE_FIELDS);
  }

  @Test
  void parseCqlQuery_negative_unsupportedBoolOperator() {
    var cqlQuery = "title all \"test-query\" prox contributors = \"value\"";
    var request = CqlSearchRequest.of(null, cqlQuery, null, 10, 0, false);
    assertThatThrownBy(() -> cqlSearchQueryConverter.convert(request))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage("Failed to parse cql query "
        + "[cql: 'title all \"test-query\" prox contributors = \"value\"', resource: null]")
      .hasCauseInstanceOf(UnsupportedOperationException.class)
      .hasRootCauseMessage("Not implemented yet [operator: PROX]");
  }

  @Test
  void parseCqlQuery_negative_unsupportedComparator() {
    var cqlQuery = "title within  \"test-query\"";
    var request = CqlSearchRequest.of(null, cqlQuery, null, 10, 0, false);
    assertThatThrownBy(() -> cqlSearchQueryConverter.convert(request))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage("Failed to parse cql query [cql: 'title within  \"test-query\"', resource: null]")
      .hasCauseInstanceOf(UnsupportedOperationException.class)
      .hasRootCauseMessage("Not implemented yet [operator: within]");
  }

  @Test
  void parseCqlQuery_negative_unsupportedNode() {
    var cqlQuery = "> dc = \"info:srw/context-sets/1/dc-v1.1\" dc.title any fish";
    var request = CqlSearchRequest.of(null, cqlQuery, null, 10, 0, false);
    assertThatThrownBy(() -> cqlSearchQueryConverter.convert(request))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage("Failed to parse cql query "
        + "[cql: '> dc = \"info:srw/context-sets/1/dc-v1.1\" dc.title any fish', resource: null]")
      .hasCauseInstanceOf(UnsupportedOperationException.class)
      .hasRootCauseMessage("Unsupported node: CQLPrefixNode");
  }

  @Test
  void shouldNotSetSourceFieldWhenExpandAll() {
    final var request = CqlSearchRequest.builder()
      .cqlQuery("title==title").expandAll(true)
      .build();

    final var searchSourceBuilder = cqlSearchQueryConverter.convert(request);

    assertThat(searchSourceBuilder.fetchSource()).isNull();
    assertThat(searchSourceBuilder)
      .isEqualTo(SearchSourceBuilder.searchSource()
        .trackTotalHits(true)
        .query(termQuery("title", "title"))
        .size(request.getLimit())
        .from(request.getOffset()));
  }

  private static Stream<Arguments> parseCqlQueryDataProvider() {
    var resourceId = randomId();
    return Stream.of(
      arguments("contributors", "(contributors =/@name \"test-query\") sortby title",
        searchSourceSort().query(termQuery("contributors", "test-query"))),

      arguments("id", "id==" + resourceId, searchSource().query(termQuery("id", resourceId))),

      arguments("keyword(title, contributor, identifier)", "(keyword all \"test-query\") sortby title/sort.descending",
        searchSource().query(matchQuery("keyword", "test-query")).sort("sort_title", DESC)),

      arguments("identifier(all)", "(identifiers =/@value \"test-query\") sortby title",
        searchSourceSort().query(termQuery("identifiers", "test-query"))),

      arguments("identifier(all)(wildcard)", "(identifiers =/@value \"*test-query\") sortby title",
        searchSourceSort().query(wildcardQuery("identifiers", "*test-query").rewrite("constant_score"))),

      arguments("num gt 2", "num > 2", searchSource().query(rangeQuery("num").gt("2"))),
      arguments("num gte 2", "num >= 2", searchSource().query(rangeQuery("num").gte("2"))),
      arguments("num lt 2", "num < 2", searchSource().query(rangeQuery("num").lt("2"))),
      arguments("num lte 2", "num <= 2", searchSource().query(rangeQuery("num").lte("2"))),
      arguments("num not 2", "num <> 2", searchSource().query(boolQuery().mustNot(termQuery("num", "2"))))
    );
  }

  private static Stream<Arguments> parseCqlQueryTitleDataProvider() {
    return Stream.of(
      arguments("title(all)", "(title all \"test-query\") sortby title",
        searchSourceSort().query(multiMatchQuery("test-query", TITLE_FIELDS.toArray(String[]::new)))),

      arguments("title(all) with filters",
        "((title all \"test-query\") and languages=(\"eng\" or \"ger\")) sortby title",
        searchSourceSort().query(boolQuery()
          .must(multiMatchQuery("test-query", TITLE_FIELDS.toArray(String[]::new)))
          .must(boolQuery().should(termQuery("languages", "eng")).should(termQuery("languages", "ger"))))),

      arguments("title(all) not contributor", "title all \"test-query\" not contributors = \"test-contributor\"",
        searchSource().query(boolQuery()
          .must(multiMatchQuery("test-query", TITLE_FIELDS.toArray(String[]::new)))
          .mustNot(termQuery("contributors", "test-contributor")))),

      arguments("title(all) sort by relevance", "title all \"test-query\"",
        searchSource().query(multiMatchQuery("test-query", TITLE_FIELDS.toArray(String[]::new)))),

      arguments("title(all) with asterisks sign", "title = \"*test-query\"",
        searchSource().query(boolQuery()
          .should(wildcardQuery("title.src", "*test-query").rewrite("constant_score"))
          .should(wildcardQuery("source.src", "*test-query").rewrite("constant_score"))
          .should(wildcardQuery("source", "*test-query").rewrite("constant_score")))),

      arguments("title(all) term query", "title = \"test-query\"",
        searchSource().query(boolQuery()
          .should(termQuery("title.src", "test-query"))
          .should(termQuery("source.src", "test-query"))
          .should(termQuery("source", "test-query"))))
    );
  }

  private static SearchSourceBuilder searchSource() {
    return SearchSourceBuilder.searchSource().trackTotalHits(true)
      .fetchSource(SOURCE_FIELDS.toArray(String[]::new), null);
  }

  private static SearchSourceBuilder searchSourceSort() {
    return searchSource().sort("sort_title", ASC);
  }
}
