package org.folio.search.service.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.elasticsearch.common.collect.List;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceAlternativeTitles;
import org.folio.search.domain.dto.InstanceIdentifiers;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ElasticsearchHitConverterTest {

  @InjectMocks private ElasticsearchHitConverter elasticsearchHitConverter;
  @Spy private final ObjectMapper objectMapper = OBJECT_MAPPER;

  @DisplayName("should convert incoming document to instance")
  @MethodSource("positiveConvertDataProvider")
  @ParameterizedTest
  void convert_positive_noMultiLangFields(
    Map<String, Object> given, Instance expected) {
    var actual = elasticsearchHitConverter.convert(given, Instance.class);

    assertThat(actual).isEqualTo(expected);
    verify(objectMapper).convertValue(anyMap(), eq(Instance.class));
  }

  private static Stream<Arguments> positiveConvertDataProvider() {
    return Stream.of(
      arguments(
        mapOf("title", mapOf("src", "title value", "eng", "title value")),
        instance(instance -> instance.setTitle("title value"))),

      arguments(
        mapOf("identifiers", List.of(mapOf("value", "isbn1"), mapOf("value", "isbn2"))),
        instance(instance -> instance.setIdentifiers(List.of(identifier("isbn1"), identifier("isbn2"))))),

      arguments(
        mapOf("alternativeTitles", List.of(
          mapOf("alternativeTitle", mapOf("ara", "value1", "src", "value1")),
          mapOf("alternativeTitle", mapOf("ara", "value2", "src", "value2")))),
        instance(instance -> instance.setAlternativeTitles(List.of(
          alternativeTitle("value1"), alternativeTitle("value2"))))),

      arguments(
        mapOf("series", List.of(mapOf("src", "series1"), mapOf("src", null))),
        instance(instance -> instance.setSeries(List.of("series1"))))
    );
  }

  private static Instance instance(Consumer<Instance> setters) {
    var instance = new Instance();
    setters.accept(instance);
    return instance;
  }

  private static InstanceIdentifiers identifier(String value) {
    var identifier = new InstanceIdentifiers();
    identifier.setValue(value);
    return identifier;
  }

  private static InstanceAlternativeTitles alternativeTitle(String value) {
    var title = new InstanceAlternativeTitles();
    title.setAlternativeTitle(value);
    return title;
  }
}
