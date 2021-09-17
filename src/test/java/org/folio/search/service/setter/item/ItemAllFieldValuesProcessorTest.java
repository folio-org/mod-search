package org.folio.search.service.setter.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Sets.newLinkedHashSet;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.toMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.domain.dto.Note;
import org.folio.search.domain.dto.Tags;
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
class ItemAllFieldValuesProcessorTest {

  private static final String ITEM_ID_1 = randomId();
  private static final String ITEM_ID_2 = randomId();
  private static final Set<String> MULTILANG_VALUE_PATHS = Set.of("items.notes.note", "items.tags.tagList");

  @InjectMocks private ItemAllFieldValuesProcessor processor;
  @Mock private SearchFieldProvider searchFieldProvider;

  @BeforeEach
  void setUp() {
    processor.setSearchFieldProvider(searchFieldProvider);
  }

  @Test
  void getFieldValue_positive() {
    when(searchFieldProvider.isMultilangField(eq(INSTANCE_RESOURCE), anyString())).thenAnswer(inv ->
      MULTILANG_VALUE_PATHS.contains(inv.<String>getArgument(1)));

    var actual = processor.getFieldValue(toMap(
      new Instance().id(randomId()).items(List.of(item1(), item2()))));

    assertThat(actual).isEqualTo(MultilangValue.of(
      newLinkedHashSet(ITEM_ID_1, "i001", "DA 3900 C89", ITEM_ID_2, "i002", "CE 16 B6724 41993"),
      newLinkedHashSet("tag1", "tag2", "privateNote", "publicNote", "tag3")));
  }

  @Test
  void getFieldValue_holdingFieldsFromSearchGeneratedValues() {
    when(searchFieldProvider.isMultilangField(INSTANCE_RESOURCE, "itemPublicNotes")).thenReturn(true);
    when(searchFieldProvider.isMultilangField(INSTANCE_RESOURCE, "itemsFullCallNumbers")).thenReturn(false);

    var actual = processor.getFieldValue(mapOf(
      "itemPublicNotes", List.of("note1", "note2"),
      "itemsFullCallNumbers", List.of("callNumber1", "callNumber2")));

    assertThat(actual).isEqualTo(MultilangValue.of(
      newLinkedHashSet("callNumber1", "callNumber2"), newLinkedHashSet("note1", "note2")));
  }

  private static Item item1() {
    return new Item()
      .id(ITEM_ID_1)
      .hrid("i001")
      .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents().callNumber("DA 3900 C89"))
      .discoverySuppress(false)
      .effectiveLocationId(randomId())
      .tags(new Tags().tagList(List.of("tag1", "tag2")))
      .notes(List.of(
        new Note().note("publicNote").staffOnly(false),
        new Note().note("privateNote").staffOnly(true)));
  }

  private static Item item2() {
    return new Item()
      .id(ITEM_ID_2)
      .hrid("i002")
      .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents().callNumber("CE 16 B6724 41993"))
      .discoverySuppress(true)
      .tags(new Tags().tagList(List.of("tag1", "tag3")))
      .notes(List.of(
        new Note().note("publicNote").staffOnly(false),
        new Note().note("privateNote").staffOnly(true)));
  }
}
