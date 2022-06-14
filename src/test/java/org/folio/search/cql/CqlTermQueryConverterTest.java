package org.folio.search.cql;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.index.query.QueryBuilders.multiMatchQuery;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.index.query.QueryBuilders.wildcardQuery;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.plainField;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.folio.search.cql.builders.TermQueryBuilder;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.service.metadata.LocalSearchFieldProvider;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLTermNode;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CqlTermQueryConverterTest {

  @Mock private LocalSearchFieldProvider searchFieldProvider;
  @Mock private SearchTermProcessor searchTermProcessor;
  @Mock private TermQueryBuilder termQueryBuilder;
  @Mock private TermQueryBuilder wildcardQueryBuilder;
  private CqlTermQueryConverter cqlTermQueryConverter;

  @BeforeEach
  void setUp() {
    lenient().when(searchFieldProvider.getModifiedField(any(), any())).thenAnswer(f -> f.getArguments()[0]);
    when(termQueryBuilder.getSupportedComparators()).thenReturn(Set.of("all"));
    when(wildcardQueryBuilder.getSupportedComparators()).thenReturn(Set.of("wildcard"));
    var searchTermProcessors = Map.of("processor", searchTermProcessor);
    var termQueryBuilders = List.of(termQueryBuilder, wildcardQueryBuilder);
    cqlTermQueryConverter = new CqlTermQueryConverter(searchFieldProvider, termQueryBuilders, searchTermProcessors);
  }

  @ParameterizedTest
  @ValueSource(strings = {"cql.allRecords=1", "cql.allRecords = 1", "keyword=*", "keyword = *", "keyword = \"*\""})
  void getQuery_positive_matchAll(String query) {
    var actual = cqlTermQueryConverter.getQuery(cqlTermNode(query), RESOURCE_NAME);
    assertThat(actual).isEqualTo(matchAllQuery());
  }

  @Test
  void getQuery_positive_fieldsGroupMultiMatch() {
    var expectedQuery = multiMatchQuery("book", "title.*", "subjects.*");
    when(searchFieldProvider.getFields(RESOURCE_NAME, "keyword")).thenReturn(List.of("title.*", "subjects.*"));
    when(termQueryBuilder.getQuery("book", RESOURCE_NAME, "title.*", "subjects.*")).thenReturn(expectedQuery);
    var actual = cqlTermQueryConverter.getQuery(cqlTermNode("keyword all book"), RESOURCE_NAME);
    assertThat(actual).isEqualTo(expectedQuery);
  }

  @Test
  void getQuery_positive_fieldsGroupWildcardQuery() {
    var expectedQuery = wildcardQuery("plain_title", "book*");
    when(searchFieldProvider.getFields(RESOURCE_NAME, "keyword")).thenReturn(List.of("title.*"));
    when(wildcardQueryBuilder.getQuery("book*", RESOURCE_NAME, "title.*")).thenReturn(expectedQuery);
    var actual = cqlTermQueryConverter.getQuery(cqlTermNode("keyword all book*"), RESOURCE_NAME);
    assertThat(actual).isEqualTo(expectedQuery);
  }

  @Test
  void getQuery_positive_callNumberProcessing() {
    var fieldDesc = plainField("long");
    fieldDesc.setSearchTermProcessor("processor");
    var expectedQuery = rangeQuery("callNumber").gt(100L);

    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, "callNumber")).thenReturn(Optional.of(fieldDesc));
    when(searchTermProcessor.getSearchTerm("A")).thenReturn(100L);
    when(termQueryBuilder.getTermLevelQuery(100L, "callNumber", RESOURCE_NAME, "long")).thenReturn(expectedQuery);

    var actual = cqlTermQueryConverter.getQuery(cqlTermNode("callNumber all A"), RESOURCE_NAME);

    assertThat(actual).isEqualTo(expectedQuery);
  }

  @Test
  void getQuery_positive_shouldFindProcessorForSearchAlias() {
    var fieldName = "nameA";
    var fieldSearchAlias = "nameB";
    var expectedQuery = rangeQuery(fieldName).gt(100L);
    var fieldDesc = plainField("long");
    fieldDesc.setSearchTermProcessor("processor");

    when(termQueryBuilder.getQuery(100L, RESOURCE_NAME, fieldSearchAlias)).thenReturn(expectedQuery);
    when(searchFieldProvider.getFields(RESOURCE_NAME, fieldName)).thenReturn(List.of(fieldSearchAlias));
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, fieldSearchAlias)).thenReturn(Optional.of(fieldDesc));
    when(searchTermProcessor.getSearchTerm("value")).thenReturn(100L);

    var actual = cqlTermQueryConverter.getQuery(cqlTermNode(fieldName + " all value"), RESOURCE_NAME);

    assertThat(actual).isEqualTo(expectedQuery);
  }

  @Test
  void getQuery_negative_unsupportedComparator() {
    var termNode = cqlTermNode("field = value");
    assertThatThrownBy(() -> cqlTermQueryConverter.getQuery(termNode, RESOURCE_NAME))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Failed to parse CQL query. Comparator '=' is not supported.");
  }

  @Test
  void getQuery_positive_singleMultilangField() {
    var expectedQuery = multiMatchQuery("book", "subjects.*");
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, "subjects")).thenReturn(Optional.of(multilangField()));
    when(termQueryBuilder.getFulltextQuery("book", "subjects", RESOURCE_NAME, emptyList()))
      .thenReturn(expectedQuery);
    var actual = cqlTermQueryConverter.getQuery(cqlTermNode("subjects all book"), RESOURCE_NAME);
    assertThat(actual).isEqualTo(expectedQuery);
  }

  @Test
  void getQuery_positive_singleKeywordField() {
    var expectedQuery = termQuery("subjects", "book");
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, "subjects")).thenReturn(Optional.of(keywordField()));
    when(termQueryBuilder.getTermLevelQuery("book", "subjects", RESOURCE_NAME, "keyword")).thenReturn(expectedQuery);
    var actual = cqlTermQueryConverter.getQuery(cqlTermNode("subjects all book"), RESOURCE_NAME);
    assertThat(actual).isEqualTo(expectedQuery);
  }

  @Test
  void getQuery_positive_singleFieldWithTermProcessor() {
    var fieldDescription = keywordField();
    fieldDescription.setSearchTermProcessor("processor");
    var expectedQuery = termQuery("subjects", "test");

    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, "subjects")).thenReturn(Optional.of(fieldDescription));
    when(searchTermProcessor.getSearchTerm("book")).thenReturn("test");
    when(termQueryBuilder.getTermLevelQuery("test", "subjects", RESOURCE_NAME, "keyword")).thenReturn(expectedQuery);

    var actual = cqlTermQueryConverter.getQuery(cqlTermNode("subjects all book"), RESOURCE_NAME);

    assertThat(actual).isEqualTo(expectedQuery);
  }

  @Test
  void getQuery_positive_unsupportedSearchField() {
    var termNode = cqlTermNode("subjects all book");
    assertThatThrownBy(() -> cqlTermQueryConverter.getQuery(termNode, RESOURCE_NAME))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid search field provided in the CQL query");
  }

  @Test
  void getQuery_positive_fieldModify() {
    var expectedQuery = termQuery("modifiedField", "book");
    when(searchFieldProvider.getModifiedField("subjects", RESOURCE_NAME)).thenReturn("modifiedField");
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, "modifiedField"))
      .thenReturn(Optional.of(keywordField()));
    when(termQueryBuilder.getTermLevelQuery("book", "modifiedField", RESOURCE_NAME, "keyword"))
      .thenReturn(expectedQuery);

    var actual = cqlTermQueryConverter.getQuery(cqlTermNode("subjects all book"), RESOURCE_NAME);
    assertThat(actual).isEqualTo(expectedQuery);
  }

  @Test
  void constructCqlTermQueryConverter_negative_processorWithSameComparators() {
    var termQueryBuilder = List.of(new QueryBuilder1(), new QueryBuilder2());
    var searchTermProcessors = Collections.<String, SearchTermProcessor>emptyMap();
    assertThatThrownBy(() -> new CqlTermQueryConverter(null, termQueryBuilder, searchTermProcessors))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Multiple TermQueryBuilder objects cannot be responsible for the same comparator."
        + " Found issues: [comparator '=': QueryBuilder1, QueryBuilder2]");
  }

  private static CQLTermNode cqlTermNode(String query) {
    try {
      var node = new CQLParser().parse(query);
      assertThat(node).isInstanceOf(CQLTermNode.class);
      return (CQLTermNode) node;
    } catch (IOException | CQLParseException e) {
      throw new AssertionError("Failed to create cql node from query: " + query);
    }
  }

  private static class QueryBuilder1 implements TermQueryBuilder {

    @Override
    public Set<String> getSupportedComparators() {
      return Set.of("=");
    }
  }

  private static class QueryBuilder2 implements TermQueryBuilder {

    @Override
    public Set<String> getSupportedComparators() {
      return Set.of("=");
    }
  }
}
