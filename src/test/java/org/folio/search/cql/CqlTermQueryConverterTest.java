package org.folio.search.cql;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.model.types.ResourceType.UNKNOWN;
import static org.folio.support.TestConstants.EMPTY_TERM_MODIFIERS;
import static org.folio.support.utils.TestUtils.keywordField;
import static org.folio.support.utils.TestUtils.multilangField;
import static org.folio.support.utils.TestUtils.plainField;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.index.query.QueryBuilders.multiMatchQuery;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.index.query.QueryBuilders.wildcardQuery;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.folio.search.cql.builders.TermQueryBuilder;
import org.folio.search.cql.searchterm.SearchTermProcessor;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.exception.ValidationException;
import org.folio.search.service.metadata.LocalSearchFieldProvider;
import org.folio.spring.testing.type.UnitTest;
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

  @Mock
  private LocalSearchFieldProvider searchFieldProvider;
  @Mock
  private SearchTermProcessor searchTermProcessor;
  @Mock
  private TermQueryBuilder termQueryBuilder;
  @Mock
  private TermQueryBuilder wildcardQueryBuilder;
  private CqlTermQueryConverter cqlTermQueryConverter;

  @BeforeEach
  void setUp() {
    lenient().when(searchFieldProvider.getModifiedField(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    when(termQueryBuilder.getSupportedComparators()).thenReturn(Set.of("all"));
    when(wildcardQueryBuilder.getSupportedComparators()).thenReturn(Set.of("wildcard"));
    var searchTermProcessors = Map.of("processor", searchTermProcessor);
    var termQueryBuilders = List.of(termQueryBuilder, wildcardQueryBuilder);
    cqlTermQueryConverter = new CqlTermQueryConverter(searchFieldProvider, termQueryBuilders, searchTermProcessors);
  }

  @ParameterizedTest
  @ValueSource(strings = {"cql.allRecords=1", "cql.allRecords = 1", "keyword=*", "keyword = *", "keyword = \"*\""})
  void getQuery_positive_matchAll(String query) {
    var actual = cqlTermQueryConverter.getQuery(cqlTermNode(query), UNKNOWN);
    assertThat(actual).isEqualTo(matchAllQuery());
  }

  @Test
  void getQuery_positive_fieldsGroupMultiMatch() {
    var expectedQuery = multiMatchQuery("book", "title.*", "subjects.*");
    when(searchFieldProvider.getFields(UNKNOWN, "keyword")).thenReturn(List.of("title.*", "subjects.*"));
    when(termQueryBuilder.getQuery("book", UNKNOWN,
      EMPTY_TERM_MODIFIERS, "title.*", "subjects.*")).thenReturn(expectedQuery);
    var actual = cqlTermQueryConverter.getQuery(cqlTermNode("keyword all book"), UNKNOWN);
    assertThat(actual).isEqualTo(expectedQuery);
  }

  @Test
  void getQuery_positive_fieldsGroupWildcardQuery() {
    var expectedQuery = wildcardQuery("plain_title", "book*");
    when(searchFieldProvider.getFields(UNKNOWN, "keyword")).thenReturn(List.of("title.*"));
    when(wildcardQueryBuilder.getQuery("book*", UNKNOWN,
      EMPTY_TERM_MODIFIERS, "title.*")).thenReturn(expectedQuery);
    var actual = cqlTermQueryConverter.getQuery(cqlTermNode("keyword all book*"), UNKNOWN);
    assertThat(actual).isEqualTo(expectedQuery);
  }

  @Test
  void getQuery_positive_callNumberProcessing() {
    var fieldDesc = plainField("long");
    fieldDesc.setSearchTermProcessor("processor");
    var expectedQuery = rangeQuery("callNumber").gt(100L);

    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, "callNumber")).thenReturn(Optional.of(fieldDesc));
    when(searchTermProcessor.getSearchTerm("A")).thenReturn(100L);
    when(termQueryBuilder.getTermLevelQuery(100L, "callNumber",
      UNKNOWN, "long")).thenReturn(expectedQuery);

    var actual = cqlTermQueryConverter.getQuery(cqlTermNode("callNumber all A"), UNKNOWN);

    assertThat(actual).isEqualTo(expectedQuery);
  }

  @Test
  void getQuery_positive_shouldFindProcessorForSearchAlias() {
    var fieldName = "nameA";
    var fieldSearchAlias = "nameB";
    var expectedQuery = rangeQuery(fieldName).gt(100L);
    var fieldDesc = plainField("long");
    fieldDesc.setSearchTermProcessor("processor");

    when(termQueryBuilder.getQuery(100L, UNKNOWN,
      EMPTY_TERM_MODIFIERS, fieldSearchAlias)).thenReturn(expectedQuery);
    when(searchFieldProvider.getFields(UNKNOWN, fieldName)).thenReturn(List.of(fieldSearchAlias));
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, fieldSearchAlias)).thenReturn(Optional.of(fieldDesc));
    when(searchTermProcessor.getSearchTerm("value")).thenReturn(100L);

    var actual = cqlTermQueryConverter.getQuery(cqlTermNode(fieldName + " all value"), UNKNOWN);

    assertThat(actual).isEqualTo(expectedQuery);
  }

  @Test
  void getQuery_negative_unsupportedComparator() {
    var termNode = cqlTermNode("field = value");
    assertThatThrownBy(() -> cqlTermQueryConverter.getQuery(termNode, UNKNOWN))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Failed to parse CQL query. Comparator '=' is not supported.");
  }

  @Test
  void getQuery_positive_singleMultilangField() {
    var expectedQuery = multiMatchQuery("book", "subjects.*");
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN,
      "subjects")).thenReturn(Optional.of(multilangField()));
    when(termQueryBuilder.getFulltextQuery("book", "subjects", UNKNOWN, emptyList()))
      .thenReturn(expectedQuery);
    var actual = cqlTermQueryConverter.getQuery(cqlTermNode("subjects all book"), UNKNOWN);
    assertThat(actual).isEqualTo(expectedQuery);
  }

  @Test
  void getQuery_positive_singleKeywordField() {
    var expectedQuery = termQuery("subjects", "book");
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN,
      "subjects")).thenReturn(Optional.of(keywordField()));
    when(termQueryBuilder.getTermLevelQuery("book", "subjects",
      UNKNOWN, "keyword")).thenReturn(expectedQuery);
    var actual = cqlTermQueryConverter.getQuery(cqlTermNode("subjects all book"), UNKNOWN);
    assertThat(actual).isEqualTo(expectedQuery);
  }

  @Test
  void getQuery_positive_singleFieldWithTermProcessor() {
    var fieldDescription = keywordField();
    fieldDescription.setSearchTermProcessor("processor");
    var expectedQuery = termQuery("subjects", "test");

    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN,
      "subjects")).thenReturn(Optional.of(fieldDescription));
    when(searchTermProcessor.getSearchTerm("book")).thenReturn("test");
    when(termQueryBuilder.getTermLevelQuery("test", "subjects",
      UNKNOWN, "keyword")).thenReturn(expectedQuery);

    var actual = cqlTermQueryConverter.getQuery(cqlTermNode("subjects all book"), UNKNOWN);

    assertThat(actual).isEqualTo(expectedQuery);
  }

  @Test
  void getQuery_positive_unsupportedSearchField() {
    var termNode = cqlTermNode("subjects all book");
    assertThatThrownBy(() -> cqlTermQueryConverter.getQuery(termNode, UNKNOWN))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid search field provided in the CQL query");
  }

  @Test
  void getQuery_positive_fieldModify() {
    var expectedQuery = termQuery("modifiedField", "book");
    when(searchFieldProvider.getModifiedField("subjects", UNKNOWN)).thenReturn("modifiedField");
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, "modifiedField"))
      .thenReturn(Optional.of(keywordField()));
    when(termQueryBuilder.getTermLevelQuery("book", "modifiedField", UNKNOWN, "keyword"))
      .thenReturn(expectedQuery);

    var actual = cqlTermQueryConverter.getQuery(cqlTermNode("subjects all book"), UNKNOWN);
    assertThat(actual).isEqualTo(expectedQuery);
  }

  @Test
  void getQuery_negative_invalidDateFormat() {
    var termNode = cqlTermNode("subjects all book");
    when(searchFieldProvider.getModifiedField("subjects", UNKNOWN)).thenReturn("modifiedField");
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, "modifiedField"))
      .thenReturn(Optional.of(plainField("date")));

    assertThatThrownBy(() -> cqlTermQueryConverter.getQuery(termNode, UNKNOWN))
      .isInstanceOf(ValidationException.class)
      .hasMessage("Invalid date format");
  }

  @Test
  void constructCqlTermQueryConverter_negative_processorWithSameComparators() {
    var queryBuilders = List.of(new QueryBuilder1(), new QueryBuilder2());
    var searchTermProcessors = Collections.<String, SearchTermProcessor>emptyMap();
    assertThatThrownBy(() -> new CqlTermQueryConverter(null, queryBuilders, searchTermProcessors))
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

  private static final class QueryBuilder1 implements TermQueryBuilder {

    @Override
    public Set<String> getSupportedComparators() {
      return Set.of("=");
    }
  }

  private static final class QueryBuilder2 implements TermQueryBuilder {

    @Override
    public Set<String> getSupportedComparators() {
      return Set.of("=");
    }
  }
}
