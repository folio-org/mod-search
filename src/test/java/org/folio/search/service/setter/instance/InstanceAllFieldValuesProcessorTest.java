package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Sets.newLinkedHashSet;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.toMap;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Identifiers;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.Note;
import org.folio.search.model.service.MultilangValue;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class InstanceAllFieldValuesProcessorTest {

  @InjectMocks
  private InstanceAllFieldValuesProcessor processor;
  @Mock
  private SearchFieldProvider searchFieldProvider;

  @BeforeEach
  void setUp() {
    processor.setSearchFieldProvider(searchFieldProvider);
  }

  @Test
  void getFieldValue_positive_emptyValue() {
    var actual = processor.getFieldValue(emptyMap());
    assertThat(actual).isEqualTo(MultilangValue.empty());
  }

  @Test
  void getFieldValue_positive_multilangTitle() {
    var instanceId = randomId();
    when(searchFieldProvider.isMultilangField(INSTANCE_RESOURCE, "id")).thenReturn(false);
    when(searchFieldProvider.isMultilangField(INSTANCE_RESOURCE, "title")).thenReturn(true);
    var actual = processor.getFieldValue(toMap(new Instance().id(instanceId).title("my resource")));
    assertThat(actual).isEqualTo(MultilangValue.of(singleton(instanceId), newLinkedHashSet("my resource")));
  }

  @Test
  void getFieldValue_positive_multilangSubjects() {
    when(searchFieldProvider.isMultilangField(INSTANCE_RESOURCE, "subjects")).thenReturn(true);
    var actual = processor.getFieldValue(toMap(new Instance().subjects(List.of("subject1", "subject2"))));
    assertThat(actual).isEqualTo(MultilangValue.of(emptySet(), newLinkedHashSet("subject1", "subject2")));
  }

  @Test
  void getFieldValue_positive_identifiers() {
    var actual = processor.getFieldValue(toMap(new Instance().identifiers(List.of(
      new Identifiers().identifierTypeId(randomId()).value("978-1-56619-909-4"),
      new Identifiers().identifierTypeId(randomId()).value("1-56619-909-3")))));
    assertThat(actual).isEqualTo(MultilangValue.of(
      newLinkedHashSet("978-1-56619-909-4", "1-56619-909-3"), emptySet()));
    verify(searchFieldProvider, times(2)).isMultilangField(INSTANCE_RESOURCE, "identifiers.value");
  }

  @Test
  void getFieldValue_positive_instanceNotes() {
    var actual = processor.getFieldValue(toMap(new Instance().notes(List.of(
      new Note().note("public note").staffOnly(false),
      new Note().note("private note").staffOnly(true)))));
    assertThat(actual).isEqualTo(MultilangValue.of(newLinkedHashSet("public note", "private note"), emptySet()));
    verify(searchFieldProvider, times(2)).isMultilangField(INSTANCE_RESOURCE, "notes.note");
  }

  @Test
  void getFieldValue_positive_classification() {
    var actual = processor.getFieldValue(mapOf("matchKey", "123456"));
    assertThat(actual).isEqualTo(MultilangValue.of(newLinkedHashSet("123456"), emptySet()));
    verify(searchFieldProvider).isMultilangField(INSTANCE_RESOURCE, "matchKey");
  }

  @Test
  void getFieldValue_positive_searchFieldProcessedIsbn() {
    var isbnValues = newLinkedHashSet("1-56619-909-3", "1566199093", "9781566199093");
    var actual = processor.getFieldValue(mapOf("isbn", isbnValues));
    assertThat(actual).isEqualTo(MultilangValue.of(isbnValues, emptySet()));
    verify(searchFieldProvider, times(3)).isMultilangField(INSTANCE_RESOURCE, "isbn");
  }

  @Test
  void getFieldValue_positive_multilangTitleValue() {
    when(searchFieldProvider.isMultilangField(INSTANCE_RESOURCE, "title")).thenReturn(true);

    var value = "titleValue";
    var actual = processor.getFieldValue(mapOf("plain_title", value, "title", mapOf("src", value, "eng", value)));

    assertThat(actual).isEqualTo(MultilangValue.of(emptySet(), singleton(value)));
  }

  @Test
  void getFieldValue_positive_holdingsAndItemSearchFieldValue() {
    var actual = processor.getFieldValue(mapOf(
      "itemPublicNotes", List.of("note1", "note2"),
      "holdingsFullCallNumbers", List.of("callNumber1", "callNumber2")));

    assertThat(actual).isEqualTo(MultilangValue.empty());
  }

  @Test
  void getFieldValue_positive_publication() {
    var actual = processor.getFieldValue(mapOf(
      "publication", List.of(mapOf(
        "publisher", "MIT Press",
        "place", "Cambridge, Mass. ",
        "dateOfPublication", "c2004",
        "role", "Publisher"
      ))));

    assertThat(actual).isEqualTo(MultilangValue.of(
      List.of("MIT Press", "Cambridge, Mass.", "c2004", "Publisher"), emptyList()));
  }

  @Test
  void getFieldValue_positive_fieldWithEmptyMapValue() {
    var actual = processor.getFieldValue(mapOf("identifiers", emptyMap()));
    assertThat(actual).isEqualTo(MultilangValue.empty());
  }

  @Test
  void getFieldValue_positive_fieldWithEmptyListValue() {
    var actual = processor.getFieldValue(mapOf("subjects", emptyList()));
    assertThat(actual).isEqualTo(MultilangValue.empty());
  }

  @Test
  void getFieldValue_positive_itemsAndHoldings() {
    var instanceId = randomId();
    var actual = processor.getFieldValue(toMap(new Instance().id(instanceId)
      .items(List.of(new Item().id(randomId()).barcode("000333"), new Item().id(randomId()).hrid("i1")))
      .holdings(List.of(new Holding().id(randomId()).hrid("h1")))));
    assertThat(actual).isEqualTo(MultilangValue.of(singleton(instanceId), emptySet()));
    verify(searchFieldProvider).isMultilangField(INSTANCE_RESOURCE, "id");
  }
}
