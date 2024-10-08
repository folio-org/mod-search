package org.folio.search.service.setter.holding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Sets.newLinkedHashSet;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.toMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Note;
import org.folio.search.domain.dto.Tags;
import org.folio.search.model.service.MultilangValue;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class HoldingAllFieldValuesProcessorTest {

  private static final String HOLDING_ID_1 = randomId();
  private static final String HOLDING_ID_2 = randomId();
  private static final Set<String> MULTILANG_VALUE_PATHS = Set.of("holdings.notes.note", "holdings.tags.tagList");

  @InjectMocks
  private HoldingAllFieldValuesProcessor processor;
  @Mock
  private SearchFieldProvider searchFieldProvider;

  @BeforeEach
  void setUp() {
    processor.setSearchFieldProvider(searchFieldProvider);
  }

  @Test
  void getFieldValue_positive() {
    when(searchFieldProvider.isFullTextField(eq(INSTANCE), anyString())).thenAnswer(inv ->
      MULTILANG_VALUE_PATHS.contains(inv.<String>getArgument(1)));

    var actual = processor.getFieldValue(toMap(
      new Instance().id(randomId()).holdings(List.of(holding1(), holding2()))));

    assertThat(actual).isEqualTo(MultilangValue.of(
      newLinkedHashSet(HOLDING_ID_1, "h001", "DA 3900 C89", HOLDING_ID_2, "h002", "CE 16 B6724 41993"),
      newLinkedHashSet("tag1", "tag2", "privateNote", "publicNote", "tag3")));
  }

  @Test
  void getFieldValue_holdingFieldsFromSearchGeneratedValues() {
    when(searchFieldProvider.isFullTextField(INSTANCE, "holdingsPublicNotes")).thenReturn(true);
    when(searchFieldProvider.isFullTextField(INSTANCE, "holdingsFullCallNumbers")).thenReturn(false);

    var actual = processor.getFieldValue(mapOf(
      "holdingsPublicNotes", List.of("note1", "note2"),
      "holdingsFullCallNumbers", List.of("callNumber1", "callNumber2")));

    assertThat(actual).isEqualTo(MultilangValue.of(
      newLinkedHashSet("callNumber1", "callNumber2"), newLinkedHashSet("note1", "note2")));
  }

  private static Holding holding1() {
    return new Holding()
      .id(HOLDING_ID_1)
      .hrid("h001")
      .permanentLocationId(randomId())
      .callNumber("DA 3900 C89")
      .discoverySuppress(false)
      .tags(new Tags().tagList(List.of("tag1", "tag2")))
      .notes(List.of(
        new Note().note("publicNote").staffOnly(false),
        new Note().note("privateNote").staffOnly(true)));
  }

  private static Holding holding2() {
    return new Holding()
      .id(HOLDING_ID_2)
      .hrid("h002")
      .permanentLocationId(randomId())
      .formerIds(List.of(randomId(), randomId()))
      .callNumber("CE 16 B6724 41993")
      .discoverySuppress(true)
      .tags(new Tags().tagList(List.of("tag1", "tag3")))
      .notes(List.of(
        new Note().note("publicNote").staffOnly(false),
        new Note().note("privateNote").staffOnly(true)));
  }
}
