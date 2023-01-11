package org.folio.search.service.setter.item;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestUtils.tags;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.Tags;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class ItemTagsProcessorTest {
  private final ItemTagsProcessor itemTagsProcessor = new ItemTagsProcessor();

  @MethodSource("tagsDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] initial={0}, expected={1}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance eventBody, List<String> expected) {
    var actual = itemTagsProcessor.getFieldValue(eventBody);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  private static Stream<Arguments> tagsDataProvider() {
    return Stream.of(
      arguments("all empty fields", new Instance(), emptyList()),
      arguments("empty holding", new Instance().addItemsItem(new Item()), emptyList()),
      arguments("item with null tag", instanceWithItemTags(tags((String) null)), emptyList()),
      arguments("item with null tagList ", instanceWithItemTags(tags((String[]) null)), emptyList()),
      arguments("item with empty value in tagList", instanceWithItemTags(tags()), emptyList()),
      arguments("item with 2 tags", instanceWithItemTags(tags("tag1", "tag2")), List.of("tag1", "tag2")),
      arguments("2 items with the same tags",
        instanceWithItemTags(tags("tag1", "tag2"), tags("tag1", "tag2")), List.of("tag1", "tag2")),
      arguments("item with tag (trailing spaces)", instanceWithItemTags(tags("tag  ")), List.of("tag")),
      arguments("item with tag(leading spaces)", instanceWithItemTags(tags("  tag")), List.of("tag")),
      arguments("item with tag(trailing and leading spaces)",
        instanceWithItemTags(tags("  tag  ")), List.of("tag")),
      arguments("item with empty tag", instanceWithItemTags(tags("")), emptyList()),
      arguments("item with blank tag", instanceWithItemTags(tags(" ")), emptyList())
    );
  }

  private static Instance instanceWithItemTags(Tags... tags) {
    var instance = new Instance();
    Arrays.stream(tags).forEach(tag -> instance.addItemsItem(new Item().tags(tag)));
    return instance;
  }
}
