package org.folio.search.service.setter.holding;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestUtils.tags;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Tags;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class HoldingTagsProcessorTest {
  private final HoldingTagsProcessor holdingTagsProcessor = new HoldingTagsProcessor();

  @MethodSource("holdingTagsDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance instance, List<String> expected) {
    var actual = holdingTagsProcessor.getFieldValue(instance);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  private static Stream<Arguments> holdingTagsDataProvider() {
    var twoTags = tags("tag1", "tag2");
    return Stream.of(
      arguments("all empty fields", new Instance(), emptyList()),
      arguments("empty holding", new Instance().addHoldingsItem(new Holding()), emptyList()),
      arguments("holding with null tag", instanceWithHoldingTags(tags((String) null)), emptyList()),
      arguments("holding with null tagList ", instanceWithHoldingTags(tags((String[]) null)), emptyList()),
      arguments("holding with empty tagList", instanceWithHoldingTags(tags()), emptyList()),
      arguments("holding with 2 tags", instanceWithHoldingTags(twoTags), List.of("tag1", "tag2")),
      arguments("2 holdings tags", instanceWithHoldingTags(twoTags, twoTags), List.of("tag1", "tag2")),
      arguments("holding tag(trailing spaces)", instanceWithHoldingTags(tags("tag  ")), List.of("tag")),
      arguments("holding tag(leading spaces)", instanceWithHoldingTags(tags("  tag")), List.of("tag")),
      arguments("holding tag(leading and trailing)", instanceWithHoldingTags(tags("  tag  ")), List.of("tag")),
      arguments("holding tag(empty)", instanceWithHoldingTags(tags("")), emptyList()),
      arguments("holding tag(blank)", instanceWithHoldingTags(tags(" ")), emptyList())
    );
  }

  private static Instance instanceWithHoldingTags(Tags... tags) {
    var instance = new Instance();
    Arrays.stream(tags).forEach(tag -> instance.addHoldingsItem(new Holding().tags(tag)));
    return instance;
  }
}
