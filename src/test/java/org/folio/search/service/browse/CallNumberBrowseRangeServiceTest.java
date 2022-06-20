package org.folio.search.service.browse;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.JsonUtils.jsonArray;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.aggregationsFromJson;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.folio.search.model.SimpleResourceRequest;
import org.folio.search.model.service.CallNumberBrowseRangeValue;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.setter.item.ItemCallNumberProcessor;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.lang.NonNull;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CallNumberBrowseRangeServiceTest {

  @InjectMocks
  private CallNumberBrowseRangeService callNumberBrowseRangeService;

  @Spy
  private final Cache<String, List<CallNumberBrowseRangeValue>> cache = Caffeine.from("maximumSize=50").build();
  @Spy
  private final ItemCallNumberProcessor itemCallNumberProcessor = new ItemCallNumberProcessor();
  @Captor
  private ArgumentCaptor<SearchSourceBuilder> searchSourceCaptor;

  @Mock
  private SearchRepository searchRepository;
  @Mock
  private SearchResponse searchResponse;

  @BeforeEach
  void setUp() {
    cache.cleanUp();
  }

  @Test
  void getBrowseRanges_positive() {
    var request = SimpleResourceRequest.of(INSTANCE_RESOURCE, TENANT_ID);
    when(searchRepository.search(eq(request), searchSourceCaptor.capture())).thenReturn(searchResponse);
    when(itemCallNumberProcessor.getCallNumberAsLong(anyString())).thenReturn(1L);
    when(searchResponse.getAggregations()).thenReturn(aggregationsFromJson(jsonObject(
      "range#cnRanges", jsonObject("buckets", jsonArray(bucket("A", 10), bucket("B", 20))))));

    var firstAttempt = callNumberBrowseRangeService.getBrowseRanges(TENANT_ID);

    var expectedRanges = List.of(rangeValue("A", 10), rangeValue("B", 20));
    assertThat(firstAttempt).isEqualTo(expectedRanges);

    var secondAttempt = callNumberBrowseRangeService.getBrowseRanges(TENANT_ID);
    assertThat(secondAttempt).isEqualTo(expectedRanges);

    verify(itemCallNumberProcessor, times(36)).getCallNumberAsLong(anyString());

    var searchSource = searchSourceCaptor.getValue();
    var aggregations = searchSource.aggregations();
    var rangeAggregation = (RangeAggregationBuilder) aggregations.getAggregatorFactories().iterator().next();
    assertThat(aggregations.count()).isEqualTo(1);
    assertThat(rangeAggregation.ranges()).hasSize(36);
  }

  @Test
  void evictCache_positive() {
    var cachedValue = singletonList(rangeValue("A", 10, 10));
    cache.put(TENANT_ID, cachedValue);
    var actual = callNumberBrowseRangeService.getBrowseRanges(TENANT_ID);
    assertThat(actual).isEqualTo(cachedValue);
    callNumberBrowseRangeService.evictRangeCache(TENANT_ID);

    assertThat(cache.asMap()).isEmpty();
  }

  @Test
  void getRangeBoundaryForBrowsing_positive_emptyRanges() {
    cache.put(TENANT_ID, emptyList());
    var actual = callNumberBrowseRangeService.getRangeBoundaryForBrowsing(TENANT_ID, "A", 30, true);
    assertThat(actual).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("getRangeBoundaryDataSource")
  void getRangeBoundaryForBrowsing_parameterized(String anchor, int size, boolean isBrowsingForward, Long expected) {
    cache.put(TENANT_ID, getCallNumberBrowseRangeValues());
    var actual = callNumberBrowseRangeService.getRangeBoundaryForBrowsing(TENANT_ID, anchor, size, isBrowsingForward);
    assertThat(actual).isEqualTo(Optional.ofNullable(expected));
  }

  public static Stream<Arguments> getRangeBoundaryDataSource() {
    return Stream.of(
      // browse forward
      arguments(".", 10, true, 2L),
      arguments("1", 10, true, 2L),

      arguments("A", 10, true, 2L),
      arguments("A", 20, true, 2L),
      arguments("A", 25, true, 3L),
      arguments("A", 50, true, 5L),
      arguments("A", 100, true, 6L),

      arguments("AM", 10, true, 3L),
      arguments("AM", 20, true, 4L),
      arguments("AM", 25, true, 4L),
      arguments("AM", 50, true, 5L),
      arguments("AM", 100, true, null),

      arguments("F", 20, true, null),
      arguments("Z", 20, true, null),

      arguments("D", 20, true, 5L),
      arguments("D", 50, true, 6L),
      arguments("D", 100, true, null),

      arguments("DM", 20, true, 6L),
      arguments("DM", 50, true, null),
      arguments("DM", 100, true, null),

      //browse backward
      arguments("A", 50, false, null),
      arguments("1", 50, false, null),
      arguments("F", 40, false, 4L),

      arguments("C", 5, false, 2L),
      arguments("C", 12, false, 2L),
      arguments("C", 20, false, 1L),
      arguments("C", 50, false, null),

      arguments("CM", 5, false, 2L),
      arguments("CM", 12, false, 2L),
      arguments("CM", 20, false, 1L),
      arguments("CM", 50, false, null),

      arguments("E", 10, false, 4L),
      arguments("E", 30, false, 3L),
      arguments("E", 45, false, 2L),
      arguments("E", 60, false, 1L),

      arguments("EM", 10, false, 4L),
      arguments("EM", 30, false, 3L),
      arguments("EM", 45, false, 2L),
      arguments("EM", 60, false, 1L)
    );
  }

  @NonNull
  private static List<CallNumberBrowseRangeValue> getCallNumberBrowseRangeValues() {
    return List.of(
      rangeValue("A", 1, 20), rangeValue("B", 2, 12),
      rangeValue("C", 3, 15), rangeValue("D", 4, 25),
      rangeValue("E", 5, 30), rangeValue("F", 6, 9));
  }

  private static CallNumberBrowseRangeValue rangeValue(String key, long count) {
    return rangeValue(key, 1L, count);
  }

  private static CallNumberBrowseRangeValue rangeValue(String key, long keyAsLong, long count) {
    return CallNumberBrowseRangeValue.of(key, keyAsLong, count);
  }

  private static JsonNode bucket(String key, int count) {
    return jsonObject("key", key, "doc_count", count);
  }
}
