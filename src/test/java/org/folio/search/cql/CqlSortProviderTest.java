package org.folio.search.cql;

import static java.util.Arrays.asList;
import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.model.types.ResourceType.UNKNOWN;
import static org.folio.search.model.types.SearchType.SORT;
import static org.folio.search.model.types.SortFieldType.COLLECTION;
import static org.folio.search.model.types.SortFieldType.SINGLE;
import static org.folio.support.utils.TestUtils.keywordField;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.opensearch.search.sort.SortBuilders.fieldSort;
import static org.opensearch.search.sort.SortMode.MAX;
import static org.opensearch.search.sort.SortMode.MIN;
import static org.opensearch.search.sort.SortOrder.ASC;
import static org.opensearch.search.sort.SortOrder.DESC;

import java.util.List;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.SortDescription;
import org.folio.search.model.types.SortFieldType;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLSortNode;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CqlSortProviderTest {

  private static final String FIELD_NAME = "field";

  @InjectMocks
  private CqlSortProvider cqlSortProvider;
  @Mock
  private SearchFieldProvider searchFieldProvider;

  @BeforeEach
  void setUp() {
    when(searchFieldProvider.getModifiedField(any(), any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void getSort_positive_ascOrder() throws Exception {
    var cqlSortNode = sortNode("(keyword all value) sortby field");
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, FIELD_NAME)).thenReturn(of(keywordField(SORT)));
    var sort = cqlSortProvider.getSort(cqlSortNode, UNKNOWN);
    assertThat(sort).isEqualTo(List.of(fieldSort("sort_" + FIELD_NAME).order(ASC)));
  }

  @Test
  void getSort_positive_descOrder() throws Exception {
    var cqlSortNode = sortNode("(keyword all value) sortby field/sort.descending");
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, FIELD_NAME)).thenReturn(of(keywordField(SORT)));
    var sort = cqlSortProvider.getSort(cqlSortNode, UNKNOWN);
    assertThat(sort).isEqualTo(List.of(fieldSort("sort_" + FIELD_NAME).order(DESC)));
  }

  @Test
  void getSort_positive_customFieldName() throws Exception {
    var cqlSortNode = sortNode("(keyword all value) sortby field");
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, FIELD_NAME)).thenReturn(
      of(sortField(sortDescription("customField", SINGLE))));
    var sort = cqlSortProvider.getSort(cqlSortNode, UNKNOWN);
    assertThat(sort).isEqualTo(List.of(fieldSort("customField")));
  }

  @Test
  void getSort_positive_modifyFieldName() throws Exception {
    var cqlSortNode = sortNode("(keyword all value) sortby field");
    when(searchFieldProvider.getModifiedField(FIELD_NAME, UNKNOWN)).thenReturn("modifiedField");
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, "modifiedField")).thenReturn(of(keywordField(SORT)));
    var sort = cqlSortProvider.getSort(cqlSortNode, UNKNOWN);
    assertThat(sort).isEqualTo(List.of(fieldSort("sort_" + "modifiedField").order(ASC)));
  }

  @Test
  void getSort_positive_collectionFieldAscOrder() throws Exception {
    var cqlSortNode = sortNode("(keyword all value) sortby field");
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, FIELD_NAME)).thenReturn(
      of(sortField(sortDescription(FIELD_NAME, COLLECTION))));
    var sort = cqlSortProvider.getSort(cqlSortNode, UNKNOWN);
    assertThat(sort).isEqualTo(List.of(fieldSort(FIELD_NAME).sortMode(MIN)));
  }

  @Test
  void getSort_positive_collectionFieldDescOrder() throws Exception {
    var cqlSortNode = sortNode("(keyword all value) sortby field/sort.descending");
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, FIELD_NAME)).thenReturn(
      of(sortField(sortDescription(FIELD_NAME, COLLECTION))));
    var sort = cqlSortProvider.getSort(cqlSortNode, UNKNOWN);
    assertThat(sort).isEqualTo(List.of(fieldSort(FIELD_NAME).order(DESC).sortMode(MAX)));
  }

  @Test
  void getSort_positive_secondarySort() throws Exception {
    var cqlSortNode = sortNode("(keyword all value) sortby field");
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, FIELD_NAME)).thenReturn(
      of(sortField(sortDescription(FIELD_NAME, COLLECTION, "_score"))));
    var sort = cqlSortProvider.getSort(cqlSortNode, UNKNOWN);
    assertThat(sort).isEqualTo(List.of(
      fieldSort(FIELD_NAME).order(ASC).sortMode(MIN),
      fieldSort("_score").order(ASC).sortMode(MIN)));
  }

  @Test
  void getSort_negative_invalidField() throws Exception {
    var cqlSortNode = sortNode("(keyword all value) sortby field/sort.descending");
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, FIELD_NAME)).thenReturn(of(keywordField()));
    assertThatThrownBy(() -> cqlSortProvider.getSort(cqlSortNode, UNKNOWN))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Sort field not found or cannot be used.");
  }

  @Test
  void getSort_negative_invalidModifier() throws Exception {
    var cqlSortNode = sortNode("(keyword all value) sortby field/sort.unknown");
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, FIELD_NAME)).thenReturn(of(keywordField(SORT)));
    assertThatThrownBy(() -> cqlSortProvider.getSort(cqlSortNode, UNKNOWN))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Failed to parse CQL query. [error: 'CQL: Unsupported modifier sort.unknown']");
  }

  private static CQLSortNode sortNode(String query) throws Exception {
    return (CQLSortNode) new CQLParser().parse(query);
  }

  private static PlainFieldDescription sortField(SortDescription sortDescription) {
    var plainFieldDescription = keywordField(SORT);
    plainFieldDescription.setSortDescription(sortDescription);
    return plainFieldDescription;
  }

  private static SortDescription sortDescription(String fieldName, SortFieldType type, String... secondaryFields) {
    var sortDescription = new SortDescription();
    sortDescription.setFieldName(fieldName);
    sortDescription.setSortType(type);
    sortDescription.setSecondarySort(asList(secondaryFields));
    return sortDescription;
  }
}
