package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.utils.TestUtils.tags;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Tags;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class InstanceAbstractTagsProcessorTest {
  private final TagsProcessor tagsProcessor = new TagsProcessor();

  @MethodSource("tagsDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance eventBody, List<String> expected) {
    var actual = tagsProcessor.getFieldValue(eventBody);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  private static Stream<Arguments> tagsDataProvider() {
    return Stream.of(
      arguments("all empty fields", new Instance(), emptyList()),
      arguments("null tags", instanceWithTags(null), emptyList()),
      arguments("empty tags", instanceWithTags(tags()), emptyList()),
      arguments("null in tagList", instanceWithTags(tags((String) null)), emptyList()),
      arguments("null tagList", instanceWithTags(tags((String[]) null)), emptyList()),
      arguments("2 tag values", instanceWithTags(tags("tag1", "tag2")), List.of("tag1", "tag2")),
      arguments("spaces in tag (leading)", instanceWithTags(tags("  tag")), List.of("tag")),
      arguments("spaces in tag (trailing)", instanceWithTags(tags("tag  ")), List.of("tag")),
      arguments("spaces in tag (leading and trailing)", instanceWithTags(tags("  tag  ")), List.of("tag")),
      arguments("empty tag value", instanceWithTags(tags("")), emptyList()),
      arguments("blank tag value", instanceWithTags(tags("  ")), emptyList())
    );
  }

  private static Instance instanceWithTags(Tags tags) {
    return new Instance().tags(tags);
  }
}
