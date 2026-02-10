package org.folio.search.service.converter;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.folio.support.TestConstants.RESOURCE_ID;
import static org.folio.support.utils.JsonTestUtils.OBJECT_MAPPER;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.searchResult;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.TotalHits.Relation;
import org.folio.search.domain.dto.AlternativeTitle;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.Identifier;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Metadata;
import org.folio.search.domain.dto.SeriesItem;
import org.folio.search.model.SearchResult;
import org.folio.spring.testing.type.UnitTest;
import org.folio.support.utils.TestUtils.TestResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import tools.jackson.databind.ObjectMapper;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ElasticsearchDocumentConverterTest {

  @Spy
  private final ObjectMapper objectMapper = OBJECT_MAPPER;
  @InjectMocks
  private ElasticsearchDocumentConverter elasticsearchDocumentConverter;
  @Mock
  private SearchResponse searchResponse;
  @Mock
  private SearchHits searchHits;
  @Mock
  private SearchHit searchHit;

  @ParameterizedTest
  @MethodSource("positiveConvertDataProvider")
  @DisplayName("should convert incoming document to instance")
  void convert_positive_noMultiLangFields(Map<String, Object> given, Instance expected) {
    assumeThat(given).isNotEmpty();
    var actual = elasticsearchDocumentConverter.convert(given, Instance.class);
    assertThat(actual).isEqualTo(expected);
    assertThat(given).isEmpty();
    verify(objectMapper).convertValue(anyMap(), eq(Instance.class));
  }

  @Test
  void convertToSearchResult_positive() {
    when(searchResponse.getHits()).thenReturn(searchHits);
    when(searchHits.getTotalHits()).thenReturn(new TotalHits(1, Relation.EQUAL_TO));
    when(searchHits.getHits()).thenReturn(array(searchHit));
    when(searchHit.getSourceAsMap()).thenReturn(mapOf("id", RESOURCE_ID));

    var actual = elasticsearchDocumentConverter.convertToSearchResult(searchResponse, TestResource.class);

    assertThat(actual).isEqualTo(searchResult(TestResource.of(RESOURCE_ID)));
  }

  @Test
  void convertToSearchResult_negative_searchHitsIsNull() {
    when(searchResponse.getHits()).thenReturn(null);
    var actual = elasticsearchDocumentConverter.convertToSearchResult(searchResponse, TestResource.class);
    assertThat(actual).isEqualTo(SearchResult.empty());
  }

  @Test
  void convertToSearchResult_negative_totalHitsIsNull() {
    when(searchResponse.getHits()).thenReturn(searchHits);
    when(searchHits.getTotalHits()).thenReturn(null);
    when(searchHits.getHits()).thenReturn(array(searchHit));
    when(searchHit.getSourceAsMap()).thenReturn(mapOf("id", RESOURCE_ID));

    var actual = elasticsearchDocumentConverter.convertToSearchResult(searchResponse, TestResource.class);

    assertThat(actual).isEqualTo(SearchResult.of(0, List.of(TestResource.of(RESOURCE_ID))));
  }

  @Test
  void convertToSearchResult_negative_searchHitsArrayIsNull() {
    when(searchResponse.getHits()).thenReturn(searchHits);
    when(searchHits.getTotalHits()).thenReturn(new TotalHits(1, Relation.EQUAL_TO));
    when(searchHits.getHits()).thenReturn(null);

    var actual = elasticsearchDocumentConverter.convertToSearchResult(searchResponse, TestResource.class);

    assertThat(actual).isEqualTo(SearchResult.of(1, emptyList()));
  }

  @Test
  void convertToSearchResult_negative_responseIsNull() {
    var actual = elasticsearchDocumentConverter.convertToSearchResult(null, TestResource.class);
    assertThat(actual).isEqualTo(SearchResult.empty());
  }

  private static Stream<Arguments> positiveConvertDataProvider() {
    return Stream.of(
      arguments(emptyMap(), new Instance()),
      arguments(mapOf("plain_title", "title value", "title", mapOf("eng", "title value", "rus", "title value")),
        instance(instance -> instance.setTitle("title value"))),
      arguments(mapOf("identifiers", List.of(mapOf("value", "isbn1"), mapOf("value", "isbn2"))),
        instance(instance -> instance.setIdentifiers(List.of(identifier("isbn1"), identifier("isbn2"))))),
      arguments(mapOf("metadata", mapOf("updatedByUserId", "userId")),
        instance(instance -> instance.setMetadata(metadata()))),
      arguments(mapOf("contributors", List.of(mapOf("plain_name", "John"))),
        instance(instance -> instance.addContributorsItem(new Contributor().name("John")))),
      arguments(mapOf("alternativeTitles", asList(
          mapOf("plain_alternativeTitle", "value1"),
          mapOf("plain_alternativeTitle", "value2"))),
        instance(instance -> instance.setAlternativeTitles(List.of(
          alternativeTitle("value1"), alternativeTitle("value2"))))),
      arguments(mapOf("series", List.of(mapOf("plain_value", "series1"))),
        instance(instance -> instance.addSeriesItem(seriesItem("series1"))))
    );
  }

  private static Instance instance(Consumer<Instance> setters) {
    var instance = new Instance();
    setters.accept(instance);
    return instance;
  }

  private static Identifier identifier(String value) {
    var identifier = new Identifier();
    identifier.setValue(value);
    return identifier;
  }

  private static AlternativeTitle alternativeTitle(String value) {
    var title = new AlternativeTitle();
    title.setAlternativeTitle(value);
    return title;
  }

  private static SeriesItem seriesItem(String value) {
    var seriesItem = new SeriesItem();
    seriesItem.setValue(value);
    return seriesItem;
  }

  private static Metadata metadata() {
    var metadata = new Metadata();
    metadata.setUpdatedByUserId("userId");
    return metadata;
  }
}
