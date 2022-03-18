package org.folio.search.service.browse;

import static java.lang.String.valueOf;
import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.range;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.folio.search.service.browse.CallNumberBrowseQueryProvider.CALL_NUMBER_RANGE_FIELD;
import static org.folio.search.utils.CollectionUtils.toLinkedHashMap;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import com.github.benmanes.caffeine.cache.Cache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.bucket.range.ParsedRange;
import org.elasticsearch.search.aggregations.bucket.range.Range.Bucket;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregator.Range;
import org.folio.search.model.SimpleResourceRequest;
import org.folio.search.model.service.CallNumberBrowseRangeValue;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.setter.instance.CallNumberProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CallNumberBrowseRangeService {

  public static final String AGGREGATION_NAME = "cnRanges";
  private final SearchRepository searchRepository;
  private final CallNumberProcessor callNumberProcessor;
  private final Cache<String, List<CallNumberBrowseRangeValue>> cache;

  /**
   * Get range boundary to optimize call-number browsing.
   *
   * @param tenant - tenant id to retrieve range facet if it's not loaded yet
   * @param anchor - call-number browsing anchor as {@link String}
   * @param size - requested page size for Elasticsearch query
   * @param isBrowsingForward - browsing direction
   * @return {@link Optional} of {@link Long} value as range boundary
   */
  public Optional<Long> getRangeBoundaryForBrowsing(String tenant, String anchor, int size, boolean isBrowsingForward) {
    var ranges = getBrowseRanges(tenant);
    return isNotEmpty(ranges) && isRangeBoundaryCanBeProvided(anchor, isBrowsingForward, ranges)
      ? getUpperRangeForBrowsingForward(ranges, anchor, size, isBrowsingForward)
      : Optional.empty();
  }

  /**
   * Provides call-number ranges as {@link Map} object.
   *
   * @param tenantId - tenant id for call-number ranges retrieval
   * @return {@link Map} with call-number ranges, where key is the lower boundary and the value is the amount of
   *   resources after it
   */
  public List<CallNumberBrowseRangeValue> getBrowseRanges(String tenantId) {
    return cache.get(tenantId, this::getCallNumberRanges);
  }

  /**
   * Evicts cache for the given {@link String} tenant id.
   *
   * @param tenantId - tenant id as {@link String} object
   */
  public void evictRangeCache(String tenantId) {
    cache.invalidate(tenantId);
  }

  private static boolean isRangeBoundaryCanBeProvided(
    String anchor, boolean isBrowsingForward, List<CallNumberBrowseRangeValue> ranges) {
    return anchor.compareTo(ranges.get(0).getKey()) > 0 && !isBrowsingForward
      || anchor.compareTo(ranges.get(ranges.size() - 1).getKey()) < 0 && isBrowsingForward;
  }

  private List<CallNumberBrowseRangeValue> getCallNumberRanges(String tenantId) {
    var callNumbersMap = concat(getCallNumbersRange('0', '9'), getCallNumbersRange('A', 'Z'))
      .collect(toLinkedHashMap(identity(), callNumberProcessor::getCallNumberAsLong));
    var searchSource = searchSource().from(0).size(0)
      .query(existsQuery(CALL_NUMBER_RANGE_FIELD))
      .aggregation(prepareRangeAggregation(callNumbersMap));
    var searchResponse = searchRepository.search(SimpleResourceRequest.of(INSTANCE_RESOURCE, tenantId), searchSource);

    return Optional.ofNullable(searchResponse)
      .map(SearchResponse::getAggregations)
      .map(aggs -> aggs.get(AGGREGATION_NAME))
      .filter(ParsedRange.class::isInstance)
      .map(ParsedRange.class::cast)
      .map(ParsedRange::getBuckets)
      .map(buckets -> mapBucketsToCacheValuesList(buckets, callNumbersMap))
      .orElse(emptyList());
  }

  private static List<CallNumberBrowseRangeValue> mapBucketsToCacheValuesList(
    List<? extends Bucket> buckets, Map<String, Long> callNumbersMap) {
    return buckets.stream()
      .map(bucket -> mapBucketToCacheValue(bucket, callNumbersMap))
      .sorted()
      .collect(toList());
  }

  private static CallNumberBrowseRangeValue mapBucketToCacheValue(Bucket bucket, Map<String, Long> callNumbers) {
    var key = bucket.getKeyAsString();
    var keyAsLong = callNumbers.get(key);
    return CallNumberBrowseRangeValue.of(key, keyAsLong, bucket.getDocCount());
  }

  private static Stream<String> getCallNumbersRange(char lower, char upper) {
    return IntStream.range(lower, upper + 1).mapToObj(character -> valueOf((char) character));
  }

  private static RangeAggregationBuilder prepareRangeAggregation(Map<String, Long> callNumbersMap) {
    var rangeAggregation = range(AGGREGATION_NAME).field(CALL_NUMBER_RANGE_FIELD);
    var callNumbers = new ArrayList<>(callNumbersMap.keySet());

    for (int i = 0; i < callNumbers.size() - 1; i++) {
      var current = callNumbers.get(i);
      var next = callNumbers.get(i + 1);
      rangeAggregation.addRange(new Range(current,
        callNumbersMap.get(current).doubleValue(),
        callNumbersMap.get(next).doubleValue()));
    }

    var lastCharacter = callNumbers.get(callNumbers.size() - 1);
    rangeAggregation.addRange(new Range(lastCharacter, callNumbersMap.get(lastCharacter).doubleValue(), null));
    return rangeAggregation;
  }

  private static Optional<Long> getUpperRangeForBrowsingForward(List<CallNumberBrowseRangeValue> ranges,
    String anchor, int expectedPageSize, boolean isBrowsingForward) {
    var foundPosition = getClosestPosition(ranges, CallNumberBrowseRangeValue.of(anchor, 0, 0), isBrowsingForward);
    var loopCondition = (Predicate<Integer>) i -> isBrowsingForward ? i < ranges.size() : i >= 0;
    var element = ranges.get(foundPosition);
    var startPosition = foundPosition + getPositionOffset(element.getKey(), anchor, isBrowsingForward);
    long sum = isBrowsingForward && Objects.equals(element.getKey(), anchor) ? element.getCount() : 0L;
    for (int i = startPosition; loopCondition.test(i); i += isBrowsingForward ? 1 : -1) {
      var current = ranges.get(i);
      sum += current.getCount();
      if (sum >= expectedPageSize) {
        return Optional.of(current.getKeyAsLong());
      }
    }

    return Optional.empty();
  }

  private static int getPositionOffset(String foundValue, String anchor, boolean isBrowsingForward) {
    if (isBrowsingForward) {
      return 1;
    }
    return Objects.equals(foundValue, anchor) ? -1 : -2;
  }

  private static <T extends Comparable<T>> int getClosestPosition(List<T> list, T value, boolean isBrowsingForward) {
    var foundPosition = Collections.binarySearch(list, value);
    if (foundPosition >= 0) {
      return foundPosition;
    }
    foundPosition = -foundPosition - (isBrowsingForward ? 1 : 2);
    return Math.min(foundPosition, list.size() - 1);
  }
}
