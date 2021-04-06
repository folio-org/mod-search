package org.folio.search.cql;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.elasticsearch.search.sort.SortBuilders.fieldSort;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.filterField;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.model.metadata.PlainFieldDescription;
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
  private static final String TITLE_SEARCH_TYPE = "title";
  private static final String FIELD = "field";

  @InjectMocks private CqlSearchQueryConverter cqlSearchQueryConverter;
  @Mock private SearchFieldProvider searchFieldProvider;
  @Mock private IsbnSearchTermProcessor isbnSearchTermProcessor;
  @Mock private Map<String, SearchTermProcessor> searchTermProcessors;
  @Mock private CqlSortProvider cqlSortProvider;

  @MethodSource("convertCqlQueryDataProvider")
  @DisplayName("convert_positive_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}")
  void convert_positive_parameterized(String cqlQuery, SearchSourceBuilder expected) {
    var actual = cqlSearchQueryConverter.convert(cqlQuery, RESOURCE_NAME);
    assertThat(actual).isEqualTo(expected);
  }

  @ParameterizedTest(name = "[{index}] query={0}")
  @MethodSource("convertCqlQuerySearchGroupDataProvider")
  @DisplayName("convert_positive_parameterizedSearchGroup")
  void convert_positive_parameterizedSearchGroup(String cqlQuery, SearchSourceBuilder expected) {
    when(searchFieldProvider.getFields(RESOURCE_NAME, TITLE_SEARCH_TYPE)).thenReturn(TITLE_FIELDS);
    if (cqlQuery.contains("languages")) {
      when(searchFieldProvider.getFields(RESOURCE_NAME, "languages")).thenReturn(emptyList());
    }

    var actual = cqlSearchQueryConverter.convert(cqlQuery, RESOURCE_NAME);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void convert_negative_unsupportedBoolOperator() {
    var cqlQuery = "title all \"test-query\" prox contributors = \"value\"";
    assertThatThrownBy(() -> cqlSearchQueryConverter.convert(cqlQuery, INSTANCE_RESOURCE))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Failed to parse CQL query. Operator 'PROX' is not supported.");
  }

  @Test
  void convert_negative_unsupportedComparator() {
    var cqlQuery = "title within  \"test-query\"";
    assertThatThrownBy(() -> cqlSearchQueryConverter.convert(cqlQuery, INSTANCE_RESOURCE))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Failed to parse CQL query. Comparator 'within' is not supported.");
  }

  @Test
  void convert_negative_unsupportedNode() {
    var cqlQuery = "> dc = \"info:srw/context-sets/1/dc-v1.1\" dc.title any fish";
    assertThatThrownBy(() -> cqlSearchQueryConverter.convert(cqlQuery, INSTANCE_RESOURCE))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Failed to parse CQL query. Node with type 'CQLPrefixNode' is not supported.");
  }

  @Test
  void convert_positive_multilangSearchField() {
    when(searchFieldProvider.getFields(RESOURCE_NAME, FIELD)).thenReturn(emptyList());
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(Optional.of(multilangField()));

    var actual = cqlSearchQueryConverter.convert(FIELD + " all value", RESOURCE_NAME);

    assertThat(actual).isEqualTo(searchSource().query(multiMatchQuery("value", "field.*")));
  }

  @Test
  void convert_positive_multilangSearchFieldExactMatch() {
    when(searchFieldProvider.getFields(RESOURCE_NAME, FIELD)).thenReturn(emptyList());
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(Optional.of(multilangField()));

    var actual = cqlSearchQueryConverter.convert(FIELD + " == value", RESOURCE_NAME);

    assertThat(actual).isEqualTo(searchSource().query(termQuery(FIELD + ".src", "value")));
  }

  @Test
  void convert_positive_plainSearchField() {
    when(searchFieldProvider.getFields(RESOURCE_NAME, FIELD)).thenReturn(emptyList());
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(Optional.of(keywordField()));

    var actual = cqlSearchQueryConverter.convert(FIELD + " all value", RESOURCE_NAME);

    assertThat(actual).isEqualTo(searchSource().query(matchQuery(FIELD, "value")));
  }

  @Test
  void convert_positive_isbnSearch() {
    when(searchTermProcessors.get("isbnSearchTermProcessor")).thenReturn(isbnSearchTermProcessor);
    when(searchFieldProvider.getFields(RESOURCE_NAME, FIELD)).thenReturn(emptyList());
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(
      Optional.of(keywordFieldWithProcessor("isbnSearchTermProcessor")));
    when(isbnSearchTermProcessor.getSearchTerm("1 23")).thenReturn("123");

    var actual = cqlSearchQueryConverter.convert(FIELD + " = 1 23", RESOURCE_NAME);

    assertThat(actual).isEqualTo(searchSource().query(matchQuery(FIELD, "123")));
  }

  @Test
  void convert_negative_searchTermProcessorNotFound() {
    when(searchTermProcessors.get("termProcessor")).thenReturn(null);
    when(searchFieldProvider.getFields(RESOURCE_NAME, FIELD)).thenReturn(emptyList());
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(
      Optional.of(keywordFieldWithProcessor("termProcessor")));

    var actual = cqlSearchQueryConverter.convert(FIELD + " = 1 23", RESOURCE_NAME);

    assertThat(actual).isEqualTo(searchSource().query(matchQuery(FIELD, "1 23")));
  }

  @Test
  void convert_positive_boolQueryWithFilters() {
    doReturn(Optional.of(keywordField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "title");
    doReturn(Optional.of(filterField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f2");
    doReturn(Optional.of(filterField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f3");
    doReturn(Optional.of(keywordField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f4");

    var cqlQuery = "(title all \"v1\") and f2==\"v2\" and f3 ==\"v3\" and f4==\"v4\"";
    var actual = cqlSearchQueryConverter.convert(cqlQuery, RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(
      boolQuery()
        .must(matchQuery("title", "v1"))
        .must(termQuery("f4", "v4"))
        .filter(termQuery("f2", "v2"))
        .filter(termQuery("f3", "v3"))));
  }

  @Test
  void convert_positive_boolQueryWithDisjunctionFilters() {
    doReturn(Optional.of(keywordField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "title");
    doReturn(Optional.of(filterField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f2");
    doReturn(Optional.of(filterField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f3");

    var cqlQuery = "(title all \"v1\") and f2==(v2 or v3 or v4) and f3==v5";
    var actual = cqlSearchQueryConverter.convert(cqlQuery, RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(
      boolQuery()
        .must(matchQuery("title", "v1"))
        .filter(boolQuery().should(termQuery("f2", "v2")).should(termQuery("f2", "v3")).should(termQuery("f2", "v4")))
        .filter(termQuery("f3", "v5"))));
  }

  @Test
  void convert_positive_boolQueryWithNotFilterQuery() {
    doReturn(Optional.of(keywordField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "title");
    doReturn(Optional.of(filterField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f2");
    doReturn(Optional.of(filterField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f3");
    doReturn(Optional.of(filterField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f4");

    var cqlQuery = "(title all \"v1\") and (f2==v2 or f4==v3) and f3==v4";
    var actual = cqlSearchQueryConverter.convert(cqlQuery, RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(boolQuery()
      .must(matchQuery("title", "v1"))
      .must(boolQuery().should(termQuery("f2", "v2")).should(termQuery("f4", "v3")))
      .filter(termQuery("f3", "v4"))));
  }

  @Test
  void convert_positive_queryWithSingleFilter() {
    doReturn(Optional.of(filterField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f1");
    var cqlQuery = "f1==value";
    var actual = cqlSearchQueryConverter.convert(cqlQuery, RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(boolQuery().filter(termQuery("f1", "value"))));
  }

  @Test
  void convert_positive_boolQueryWithMustNotCondition() {
    doReturn(Optional.of(filterField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f1");
    doReturn(Optional.of(keywordField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f2");
    doReturn(Optional.of(keywordField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f3");

    var cqlQuery = "(f2=v1 not f3=v2) and f1==(v3 or v4)";
    var actual = cqlSearchQueryConverter.convert(cqlQuery, RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(boolQuery()
      .must(boolQuery().must(matchQuery("f2", "v1")).mustNot(matchQuery("f3", "v2")))
      .filter(boolQuery().should(termQuery("f1", "v3")).should(termQuery("f1", "v4")))));
  }

  @Test
  void convert_positive_boolQueryWithMustCondition() {
    doReturn(Optional.of(filterField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f1");
    doReturn(Optional.of(keywordField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f2");

    var cqlQuery = "f2=v1 and f1==(v3 or v4)";
    var actual = cqlSearchQueryConverter.convert(cqlQuery, RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(boolQuery()
      .must(matchQuery("f2", "v1"))
      .filter(boolQuery().should(termQuery("f1", "v3")).should(termQuery("f1", "v4")))));
  }

  @Test
  void convert_positive_boolQueryWithNotCondition() {
    doReturn(Optional.of(filterField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f1");
    doReturn(Optional.of(keywordField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f2");

    var cqlQuery = "f2<>v1 and f1==(v3 or v4)";
    var actual = cqlSearchQueryConverter.convert(cqlQuery, RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(boolQuery()
      .must(boolQuery().mustNot(termQuery("f2", "v1")))
      .filter(boolQuery().should(termQuery("f1", "v3")).should(termQuery("f1", "v4")))));
  }

  @Test
  void convert_positive_disjunctionFilterQuery() {
    doReturn(Optional.of(filterField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f1");
    var actual = cqlSearchQueryConverter.convert("f1==(v3 or v4)", RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(boolQuery()
      .filter(boolQuery().should(termQuery("f1", "v3")).should(termQuery("f1", "v4")))));
  }

  @Test
  void convert_positive_boolQueryWithRangeCondition() {
    doReturn(Optional.of(filterField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f1");
    doReturn(Optional.of(keywordField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f2");

    var cqlQuery = "f2<>v1 and f1 > 2";
    var actual = cqlSearchQueryConverter.convert(cqlQuery, RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(boolQuery()
      .must(boolQuery().mustNot(termQuery("f2", "v1")))
      .filter(rangeQuery("f1").gt("2"))));
  }

  @Test
  void convert_positive_sortQuery() {
    var expectedSort = fieldSort(FIELD);

    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(Optional.of(keywordField()));
    when(cqlSortProvider.getSort(any(), eq(RESOURCE_NAME))).thenReturn(List.of(expectedSort));

    var actual = cqlSearchQueryConverter.convert("(field==value) sortby title", RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(termQuery(FIELD, "value")).sort(expectedSort));
  }

  private static Stream<Arguments> convertCqlQueryDataProvider() {
    var resourceId = randomId();
    return Stream.of(
      arguments("contributors =/@name \"test-query\"",
        searchSource().query(matchQuery("contributors", "test-query"))),

      arguments("id==" + resourceId, searchSource().query(termQuery("id", resourceId))),

      arguments("keyword all \"test-query\"",
        searchSource().query(matchQuery("keyword", "test-query"))),

      arguments("identifiers =/@value \"test-query\"",
        searchSource().query(matchQuery("identifiers", "test-query"))),

      arguments("identifiers ==/@value \"test-query\"",
        searchSource().query(termQuery("identifiers", "test-query"))),

      arguments("identifiers =/@value \"*test-query\"",
        searchSource().query(wildcardQuery("identifiers", "*test-query").rewrite("constant_score"))),

      arguments("num > 2", searchSource().query(rangeQuery("num").gt("2"))),
      arguments("num >= 2", searchSource().query(rangeQuery("num").gte("2"))),
      arguments("num < 2", searchSource().query(rangeQuery("num").lt("2"))),
      arguments("num <= 2", searchSource().query(rangeQuery("num").lte("2"))),
      arguments("num <> 2", searchSource().query(boolQuery().mustNot(termQuery("num", "2")))),

      arguments("f1==v1 and f2==v2 and f3==v3", searchSource().query(boolQuery()
        .must(termQuery("f1", "v1")).must(termQuery("f2", "v2")).must(termQuery("f3", "v3")))),

      arguments("f1==v1 or f2==v2 or f3==v3", searchSource().query(boolQuery()
        .should(termQuery("f1", "v1")).should(termQuery("f2", "v2")).should(termQuery("f3", "v3")))),

      arguments("f1==v1 and f2==v2 or f3==v3", searchSource().query(boolQuery()
        .should(boolQuery().must(termQuery("f1", "v1")).must(termQuery("f2", "v2"))).should(termQuery("f3", "v3")))),

      arguments("(f1==v1 and f2==v2) or (f3==v3 and f4==v4)", searchSource().query(boolQuery()
        .should(boolQuery().must(termQuery("f1", "v1")).must(termQuery("f2", "v2")))
        .should(boolQuery().must(termQuery("f3", "v3")).must(termQuery("f4", "v4"))))),

      arguments("f1==v1 and f2==v2 and f3==v3 and f4==v4", searchSource().query(boolQuery()
        .must(termQuery("f1", "v1")).must(termQuery("f2", "v2"))
        .must(termQuery("f3", "v3")).must(termQuery("f4", "v4"))))
    );
  }

  private static Stream<Arguments> convertCqlQuerySearchGroupDataProvider() {
    return Stream.of(
      arguments("(title all \"test-query\") and languages=(\"eng\" or \"ger\")",
        searchSource().query(boolQuery()
          .must(multiMatchQuery("test-query", TITLE_FIELDS.toArray(String[]::new)))
          .must(boolQuery()
            .should(matchQuery("languages", "eng"))
            .should(matchQuery("languages", "ger"))))),

      arguments("title all \"test-query\" not contributors = \"test-contributor\"",
        searchSource().query(boolQuery()
          .must(multiMatchQuery("test-query", TITLE_FIELDS.toArray(String[]::new)))
          .mustNot(matchQuery("contributors", "test-contributor")))),

      arguments("title all \"test-query\"",
        searchSource().query(multiMatchQuery("test-query", TITLE_FIELDS.toArray(String[]::new)))),

      arguments("title = \"*test-query\"",
        searchSource().query(boolQuery()
          .should(wildcardQuery("title.src", "*test-query").rewrite("constant_score"))
          .should(wildcardQuery("source.src", "*test-query").rewrite("constant_score"))
          .should(wildcardQuery("source", "*test-query").rewrite("constant_score")))),

      arguments("title = \"test-query\"",
        searchSource().query(multiMatchQuery("test-query", "title.*", "source.*", "source")))
    );
  }

  private static SearchSourceBuilder searchSource() {
    return SearchSourceBuilder.searchSource();
  }

  private static PlainFieldDescription keywordFieldWithProcessor(String processorName) {
    var fieldDescription = keywordField();
    fieldDescription.setSearchTermProcessor(processorName);
    return fieldDescription;
  }
}
