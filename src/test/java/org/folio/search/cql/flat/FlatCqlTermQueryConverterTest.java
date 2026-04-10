package org.folio.search.cql.flat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.folio.search.cql.builders.TermQueryBuilder;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.index.query.QueryBuilder;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLTermNode;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FlatCqlTermQueryConverterTest {

  @Mock
  private SearchFieldProvider searchFieldProvider;

  private FlatCqlTermQueryConverter converter;

  @BeforeEach
  void setUp() {
    when(searchFieldProvider.getModifiedField(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    converter = new FlatCqlTermQueryConverter(
      new FieldLevelClassifier(new com.fasterxml.jackson.databind.ObjectMapper()),
      searchFieldProvider,
      List.<TermQueryBuilder>of());
  }

  @Test
  void getQuery_resolvesHoldingAliasAndNormalizesCallNumberTerm() throws Exception {
    var termNode = termNode("holdings.normalizedCallNumbers==\"QA76.73.J38 A58 2004\"");
    var fieldDescription = callNumberField();

    when(searchFieldProvider.getFields(INSTANCE, "holdings.normalizedCallNumbers"))
      .thenReturn(List.of("holdingsNormalizedCallNumbers"));
    when(searchFieldProvider.getPlainFieldByPath(INSTANCE, "holdingsNormalizedCallNumbers"))
      .thenReturn(Optional.of(fieldDescription));

    QueryBuilder query = converter.getQuery(termNode, INSTANCE);

    assertThat(query.toString()).contains("\"has_child\"");
    assertThat(query.toString()).contains("\"holding.holdingsNormalizedCallNumbers\"");
    assertThat(query.toString()).contains("\"qa7673j38a582004*\"");
  }

  @Test
  void getQuery_usesNormalizedCallNumberWildcardForBareHoldingField() throws Exception {
    var termNode = termNode("holdingsNormalizedCallNumbers==\"QA76.73.J38 A58 2004\"");
    var fieldDescription = callNumberField();

    when(searchFieldProvider.getFields(INSTANCE, "holdingsNormalizedCallNumbers"))
      .thenReturn(List.of("holdingsNormalizedCallNumbers"));
    when(searchFieldProvider.getPlainFieldByPath(INSTANCE, "holdingsNormalizedCallNumbers"))
      .thenReturn(Optional.of(fieldDescription));

    QueryBuilder query = converter.getQuery(termNode, INSTANCE);

    assertThat(query.toString()).contains("\"holding.holdingsNormalizedCallNumbers\"");
    assertThat(query.toString()).contains("\"qa7673j38a582004*\"");
  }

  @Test
  void getQuery_resolvesItemAliasAndNormalizesCallNumberTerm() throws Exception {
    var termNode = termNode("items.normalizedCallNumbers==\"QA76.73.J38 A58 2004\"");
    var fieldDescription = callNumberField();

    when(searchFieldProvider.getFields(INSTANCE, "items.normalizedCallNumbers"))
      .thenReturn(List.of("itemNormalizedCallNumbers"));
    when(searchFieldProvider.getPlainFieldByPath(INSTANCE, "itemNormalizedCallNumbers"))
      .thenReturn(Optional.of(fieldDescription));

    QueryBuilder query = converter.getQuery(termNode, INSTANCE);

    assertThat(query.toString()).contains("\"item.itemNormalizedCallNumbers\"");
    assertThat(query.toString()).contains("\"qa7673j38a582004*\"");
  }

  @Test
  void getQuery_normalizedCallNumberNotEqualMatchesV1WildcardBehavior() throws Exception {
    var termNode = termNode("holdingsNormalizedCallNumbers<>\"QA76.73.J38 A58 2004\"");
    var fieldDescription = callNumberField();

    when(searchFieldProvider.getFields(INSTANCE, "holdingsNormalizedCallNumbers"))
      .thenReturn(List.of("holdingsNormalizedCallNumbers"));
    when(searchFieldProvider.getPlainFieldByPath(INSTANCE, "holdingsNormalizedCallNumbers"))
      .thenReturn(Optional.of(fieldDescription));

    QueryBuilder query = converter.getQuery(termNode, INSTANCE);

    assertThat(query.toString()).contains("\"qa7673j38a582004*\"");
    assertThat(query.toString()).doesNotContain("must_not");
  }

  private static CQLTermNode termNode(String query) throws Exception {
    return (CQLTermNode) new CQLParser().parse(query);
  }

  private static PlainFieldDescription callNumberField() {
    var description = keywordField();
    description.setSearchTermProcessor("callNumberSearchTermProcessor");
    return description;
  }
}
