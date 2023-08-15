package org.folio.search.cql;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.CONSORTIUM_TENANT_ID;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.filterField;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.MultiMatchQueryBuilder.Type.CROSS_FIELDS;
import static org.opensearch.index.query.MultiMatchQueryBuilder.Type.PHRASE;
import static org.opensearch.index.query.Operator.AND;
import static org.opensearch.index.query.Operator.OR;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.existsQuery;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.index.query.QueryBuilders.matchQuery;
import static org.opensearch.index.query.QueryBuilders.multiMatchQuery;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.search.sort.SortBuilders.fieldSort;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.folio.search.cql.CqlSearchQueryConverterTest.ConverterTestConfiguration;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.search.service.metadata.LocalSearchFieldProvider;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@UnitTest
@SpringBootTest(classes = {CqlSearchQueryConverter.class, ConverterTestConfiguration.class}, webEnvironment = NONE)
class CqlSearchQueryConverterTest {

  private static final String[] TITLE_FIELDS = new String[] {"title.*", "source.*", "source"};
  private static final String TITLE_SEARCH_TYPE = "title";
  private static final String FIELD = "field";

  @Autowired
  private CqlSearchQueryConverter cqlSearchQueryConverter;
  @MockBean
  private LocalSearchFieldProvider searchFieldProvider;
  @MockBean
  private CqlSortProvider cqlSortProvider;
  @MockBean
  private FolioExecutionContext folioExecutionContext;
  @MockBean
  private ConsortiumTenantService consortiumTenantService;

  @BeforeEach
  void setUp() {
    when(searchFieldProvider.getModifiedField(any(), any())).thenAnswer(f -> f.getArguments()[0]);
  }

  @MethodSource("convertCqlQueryDataProvider")
  @DisplayName("convert_positive_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}")
  void convert_positive_parameterized(String cqlQuery, SearchSourceBuilder expected) {
    when(searchFieldProvider.getPlainFieldByPath(eq(RESOURCE_NAME), any())).thenReturn(Optional.of(keywordField()));
    var actual = cqlSearchQueryConverter.convert(cqlQuery, RESOURCE_NAME);
    assertThat(actual).isEqualTo(expected);
  }

  @ParameterizedTest(name = "[{index}] query={0}")
  @MethodSource("convertCqlQuerySearchGroupDataProvider")
  @DisplayName("convert_positive_parameterizedSearchGroup")
  void convert_positive_parameterizedSearchGroup(String cqlQuery, SearchSourceBuilder expected) {
    when(searchFieldProvider.getFields(RESOURCE_NAME, TITLE_SEARCH_TYPE)).thenReturn(List.of(TITLE_FIELDS));
    when(searchFieldProvider.getPlainFieldByPath(eq(RESOURCE_NAME), any())).thenReturn(Optional.of(keywordField()));

    var actual = cqlSearchQueryConverter.convert(cqlQuery, RESOURCE_NAME);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void convert_positive_searchByGroupOfOneField() {
    when(searchFieldProvider.getFields(RESOURCE_NAME, "group")).thenReturn(List.of("field"));
    var actual = cqlSearchQueryConverter.convert("group all value", RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(getMultiMatchQuery("value", "field")));
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
  void convert_negative_invalidQuery() {
    var cqlQuery = "> invalidQuery";
    assertThatThrownBy(() -> cqlSearchQueryConverter.convert(cqlQuery, INSTANCE_RESOURCE))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage("Failed to parse cql query [cql: '> invalidQuery', resource: instance]");
  }

  @Test
  void convert_positive_multilangSearchField() {
    when(searchFieldProvider.getFields(RESOURCE_NAME, FIELD)).thenReturn(emptyList());
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(Optional.of(multilangField()));

    var actual = cqlSearchQueryConverter.convert(FIELD + " all value", RESOURCE_NAME);

    assertThat(actual).isEqualTo(searchSource().query(getMultiMatchQuery("value", "field.*")));
  }

  @Test
  void convert_positive_multilangSearchFieldExactMatch() {
    when(searchFieldProvider.getFields(RESOURCE_NAME, FIELD)).thenReturn(emptyList());
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(Optional.of(multilangField()));

    var actual = cqlSearchQueryConverter.convert(FIELD + " == value", RESOURCE_NAME);

    assertThat(actual).isEqualTo(searchSource().query(
      multiMatchQuery("value", FIELD + ".*").type(PHRASE)));
  }

  @Test
  void convert_positive_plainSearchField() {
    when(searchFieldProvider.getFields(RESOURCE_NAME, FIELD)).thenReturn(emptyList());
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(Optional.of(keywordField()));

    var actual = cqlSearchQueryConverter.convert(FIELD + " all value", RESOURCE_NAME);

    assertThat(actual).isEqualTo(searchSource().query(matchQuery(FIELD, "value").operator(AND)));
  }

  @Test
  void convert_positive_isbnSearch() {
    when(searchFieldProvider.getFields(RESOURCE_NAME, FIELD)).thenReturn(emptyList());
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(
      Optional.of(keywordFieldWithProcessor("isbnSearchTermProcessor")));

    var actual = cqlSearchQueryConverter.convert(FIELD + " = 1 23", RESOURCE_NAME);

    assertThat(actual).isEqualTo(searchSource().query(matchQuery(FIELD, "123").operator(AND)));
  }

  @Test
  void convert_positive_oclcSearch() {
    when(searchFieldProvider.getFields(RESOURCE_NAME, FIELD)).thenReturn(emptyList());
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(
      Optional.of(keywordFieldWithProcessor("oclcSearchTermProcessor")));

    var actual = cqlSearchQueryConverter.convert(FIELD + " = 00061712", RESOURCE_NAME);

    assertThat(actual).isEqualTo(searchSource().query(matchQuery(FIELD, "61712").operator(AND)));
  }

  @Test
  void convert_negative_searchTermProcessorNotFound() {
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, FIELD)).thenReturn(
      Optional.of(keywordFieldWithProcessor("termProcessor")));

    var actual = cqlSearchQueryConverter.convert(FIELD + " = 1 23", RESOURCE_NAME);

    assertThat(actual).isEqualTo(searchSource().query(matchQuery(FIELD, "1 23").operator(AND)));
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
        .must(matchQuery("title", "v1").operator(AND))
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
        .must(matchQuery("title", "v1").operator(AND))
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
      .must(matchQuery("title", "v1").operator(AND))
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
      .must(boolQuery()
        .must(matchQuery("f2", "v1").operator(AND))
        .mustNot(matchQuery("f3", "v2").operator(AND)))
      .filter(boolQuery().should(termQuery("f1", "v3")).should(termQuery("f1", "v4")))));
  }

  @Test
  void convert_positive_boolQueryWithMustCondition() {
    doReturn(Optional.of(filterField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f1");
    doReturn(Optional.of(keywordField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f2");

    var cqlQuery = "f2=v1 and f1==(v3 or v4)";
    var actual = cqlSearchQueryConverter.convert(cqlQuery, RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(boolQuery()
      .must(matchQuery("f2", "v1").operator(AND))
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

  @Test
  void convert_positive_groupOfOneField() {
    var field = "contributors.name";
    when(searchFieldProvider.getFields(RESOURCE_NAME, "contributors")).thenReturn(List.of(field));
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, field)).thenReturn(Optional.of(keywordField()));
    var actual = cqlSearchQueryConverter.convert("contributors any joh*", RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(wildcardQuery(field, "joh*")));
  }

  @Test
  void convert_negative_unsupportedField() {
    assertThatThrownBy(() -> cqlSearchQueryConverter.convert("invalid_field all value", RESOURCE_NAME))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid search field provided in the CQL query");
  }

  @Test
  void convertForConsortia_positive_whenConsortiaDisabled() {
    doReturn(Optional.of(filterField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f1");
    var cqlQuery = "f1==value";
    var actual = cqlSearchQueryConverter.convertForConsortia(cqlQuery, RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(
      boolQuery().filter(termQuery("f1", "value"))));
  }

  @Test
  void convertForConsortia_positive() {
    when(folioExecutionContext.getTenantId()).thenReturn(TENANT_ID);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CONSORTIUM_TENANT_ID));
    doReturn(Optional.of(filterField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f1");
    var cqlQuery = "f1==value";
    var actual = cqlSearchQueryConverter.convertForConsortia(cqlQuery, RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(boolQuery()
      .filter(termQuery("f1", "value"))
      .should(termQuery("tenantId", TENANT_ID))
      .should(termQuery("shared", true))
      .minimumShouldMatch(1)));
  }

  @Test
  void convertForConsortia_positive_whenCentralTenant() {
    when(folioExecutionContext.getTenantId()).thenReturn(CONSORTIUM_TENANT_ID);
    when(consortiumTenantService.getCentralTenant(CONSORTIUM_TENANT_ID)).thenReturn(Optional.of(CONSORTIUM_TENANT_ID));
    doReturn(Optional.of(filterField())).when(searchFieldProvider).getPlainFieldByPath(RESOURCE_NAME, "f1");
    var cqlQuery = "f1==value";
    var actual = cqlSearchQueryConverter.convertForConsortia(cqlQuery, RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(
      boolQuery().filter(termQuery("f1", "value"))));
  }

  @Test
  void convertForConsortia_positive_whenOriginalQueryMatchAll() {
    when(folioExecutionContext.getTenantId()).thenReturn(TENANT_ID);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CONSORTIUM_TENANT_ID));
    when(searchFieldProvider.getPlainFieldByPath(eq(RESOURCE_NAME), any())).thenReturn(Optional.of(keywordField()));
    var actual = cqlSearchQueryConverter.convertForConsortia("cql.allRecords = 1", RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(
      boolQuery()
        .should(termQuery("tenantId", TENANT_ID))
        .should(termQuery("shared", true))
        .minimumShouldMatch(1)));
  }

  @Test
  void convertForConsortia_positive_whenOriginalBooleanWithShould() {
    when(folioExecutionContext.getTenantId()).thenReturn(TENANT_ID);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(CONSORTIUM_TENANT_ID));
    when(searchFieldProvider.getPlainFieldByPath(eq(RESOURCE_NAME), any())).thenReturn(Optional.of(keywordField()));
    var actual = cqlSearchQueryConverter.convertForConsortia("f1==v1 or f2==v2", RESOURCE_NAME);
    assertThat(actual).isEqualTo(searchSource().query(
      boolQuery()
        .should(termQuery("f1", "v1"))
        .should(termQuery("f2", "v2"))
        .must(boolQuery()
          .should(termQuery("tenantId", TENANT_ID))
          .should(termQuery("shared", true)))
        .minimumShouldMatch(1)
    ));
  }

  private static Stream<Arguments> convertCqlQueryDataProvider() {
    var resourceId = randomId();
    return Stream.of(
      arguments("(contributors =/@name \"test-query\") sortby title",
        searchSource().query(matchQuery("contributors", "test-query").operator(AND))),

      arguments("id==" + resourceId, searchSource().query(termQuery("id", resourceId))),

      arguments("keyword all \"test-query\"", searchSource().query(matchQuery("keyword", "test-query").operator(AND))),
      arguments("keyword adj \"test-query\"", searchSource().query(matchQuery("keyword", "test-query").operator(AND))),
      arguments("keyword any \"test-query\"", searchSource().query(matchQuery("keyword", "test-query").operator(OR))),
      arguments("keyword == \"test-query\"", searchSource().query(termQuery("keyword", "test-query"))),

      arguments("(identifiers =/@value \"test-query\") sortby title",
        searchSource().query(matchQuery("identifiers", "test-query").operator(AND))),

      arguments("identifiers ==/@value \"test-query\"",
        searchSource().query(termQuery("identifiers", "test-query"))),

      arguments("identifiers =/@value \"*test-query\"",
        searchSource().query(wildcardQuery("identifiers", "*test-query"))),

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
        .must(termQuery("f3", "v3")).must(termQuery("f4", "v4")))),

      arguments("cql.allRecords = 1", searchSource().query(matchAllQuery())),
      arguments("cql.allRecords=1", searchSource().query(matchAllQuery())),
      arguments("cql.allRecords= 1", searchSource().query(matchAllQuery())),
      arguments("cql.allRecords= \"1\"", searchSource().query(matchAllQuery())),
      arguments("cql.allRecords = 1 NOT subjects == english", searchSource().query(boolQuery()
        .must(matchAllQuery()).mustNot(termQuery("subjects", "english")))),

      arguments("title=\"\"", searchSource().query(existsQuery("title"))),
      arguments("title==\"\"", searchSource().query(termQuery("title", ""))),
      arguments("lang = \"\" NOT lang == \"*en*\"", searchSource().query(boolQuery()
        .must(existsQuery("lang")).mustNot(wildcardQuery("lang", "*en*"))))
    );
  }

  private static Stream<Arguments> convertCqlQuerySearchGroupDataProvider() {
    return Stream.of(
      arguments("(title all \"test-query\") sortby title",
        searchSource().query(getMultiMatchQuery("test-query", TITLE_FIELDS))),

      arguments("title any \"test-query\"",
        searchSource().query(multiMatchQuery("test-query", TITLE_FIELDS))),

      arguments("title adj \"test-query\"",
        searchSource().query(getMultiMatchQuery("test-query", TITLE_FIELDS))),

      arguments("title == \"test-query\"",
        searchSource().query(multiMatchQuery("test-query", TITLE_FIELDS).type(PHRASE))),

      arguments("((title all \"test-query\") and languages=(\"eng\" or \"ger\")) sortby title",
        searchSource().query(boolQuery()
          .must(getMultiMatchQuery("test-query", TITLE_FIELDS))
          .must(boolQuery()
            .should(matchQuery("languages", "eng").operator(AND))
            .should(matchQuery("languages", "ger").operator(AND))))),

      arguments("title all \"test-query\" not contributors = \"test-contributor\"",
        searchSource().query(boolQuery()
          .must(getMultiMatchQuery("test-query", TITLE_FIELDS))
          .mustNot(matchQuery("contributors", "test-contributor").operator(AND)))),

      arguments("title all \"test-query\"",
        searchSource().query(getMultiMatchQuery("test-query", TITLE_FIELDS))),

      arguments("title = \"*test-query\"",
        searchSource().query(boolQuery()
          .should(wildcardQuery("plain_title", "*test-query"))
          .should(wildcardQuery("plain_source", "*test-query"))
          .should(wildcardQuery("source", "*test-query")))),

      arguments("title = \"test-query\"",
        searchSource().query(
          getMultiMatchQuery("test-query", "title.*", "source.*", "source")))
    );
  }

  private static SearchSourceBuilder searchSource() {
    return SearchSourceBuilder.searchSource();
  }

  private static QueryBuilder wildcardQuery(String field, String query) {
    return QueryBuilders.wildcardQuery(field, query).rewrite("constant_score");
  }

  private static PlainFieldDescription keywordFieldWithProcessor(String processorName) {
    var fieldDescription = keywordField();
    fieldDescription.setSearchTermProcessor(processorName);
    return fieldDescription;
  }

  private static QueryBuilder getMultiMatchQuery(Object term, String... fieldNames) {
    return multiMatchQuery(term, fieldNames).operator(AND).type(CROSS_FIELDS);
  }

  @TestConfiguration
  @Import({CqlTermQueryConverter.class, CqlQueryParser.class})
  @ComponentScan("org.folio.search.cql.builders")
  static class ConverterTestConfiguration {

    @Bean
    SearchTermProcessor isbnSearchTermProcessor() {
      return inputTerm -> inputTerm.replaceAll("\\s+", "");
    }

    @Bean
    SearchTermProcessor oclcSearchTermProcessor() {
      return inputTerm -> inputTerm.replaceAll("0", "");
    }
  }
}
