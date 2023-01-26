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
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class HoldingsTagsProcessorTest {
  private final HoldingsTagsProcessor holdingsTagsProcessor = new HoldingsTagsProcessor();

  @MethodSource("holdingsTagsDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance instance, List<String> expected) {
    var actual = holdingsTagsProcessor.getFieldValue(instance);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  private static Stream<Arguments> holdingsTagsDataProvider() {
    var twoTags = tags("tag1", "tag2");
    return Stream.of(
      arguments("all empty fields", new Instance(), emptyList()),
      arguments("empty holding", new Instance().addHoldingsItem(new Holding()), emptyList()),
      arguments("holdings with null tag", instanceWithHoldingsTags(tags((String) null)), emptyList()),
      arguments("holdings with null tagList ", instanceWithHoldingsTags(tags((String[]) null)), emptyList()),
      arguments("holdings with empty tagList", instanceWithHoldingsTags(tags()), emptyList()),
      arguments("holdings with 2 tags", instanceWithHoldingsTags(twoTags), List.of("tag1", "tag2")),
      arguments("2 holdings tags", instanceWithHoldingsTags(twoTags, twoTags), List.of("tag1", "tag2")),
      arguments("holdings tag(trailing spaces)", instanceWithHoldingsTags(tags("tag  ")), List.of("tag")),
      arguments("holdings tag(leading spaces)", instanceWithHoldingsTags(tags("  tag")), List.of("tag")),
      arguments("holdings tag(leading and trailing)", instanceWithHoldingsTags(tags("  tag  ")), List.of("tag")),
      arguments("holdings tag(empty)", instanceWithHoldingsTags(tags("")), emptyList()),
      arguments("holdings tag(blank)", instanceWithHoldingsTags(tags(" ")), emptyList())
    );
  }

  private static Instance instanceWithHoldingsTags(Tags... tags) {
    var instance = new Instance();
    Arrays.stream(tags).forEach(tag -> instance.addHoldingsItem(new Holding().tags(tag)));
    return instance;
  }
}
