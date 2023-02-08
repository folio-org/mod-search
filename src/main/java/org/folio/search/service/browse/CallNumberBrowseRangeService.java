package org.folio.search.service.browse;

import static java.lang.String.valueOf;
import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Stream.concat;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.folio.search.service.browse.CallNumberBrowseQueryProvider.CALL_NUMBER_RANGE_FIELD;
import static org.folio.search.utils.CollectionUtils.toLinkedHashMap;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.opensearch.index.query.QueryBuilders.existsQuery;
import static org.opensearch.search.aggregations.AggregationBuilders.range;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;

import com.github.benmanes.caffeine.cache.Cache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.SimpleResourceRequest;
import org.folio.search.model.service.CallNumberBrowseRangeValue;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.setter.item.ItemCallNumberProcessor;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.aggregations.bucket.range.ParsedRange;
import org.opensearch.search.aggregations.bucket.range.Range.Bucket;
import org.opensearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.opensearch.search.aggregations.bucket.range.RangeAggregator.Range;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class CallNumberBrowseRangeService {

  private static final String AGGREGATION_NAME = "cnRanges";
  private final SearchRepository searchRepository;
  private final ItemCallNumberProcessor callNumberProcessor;
  private final Cache<String, List<CallNumberBrowseRangeValue>> cache;

  /**
   * Get range boundary to optimize call-number browsing.
   *
   * @param tenant            - tenant id to retrieve range facet if it's not loaded yet
   * @param anchor            - call-number browsing anchor as {@link String}
   * @param size              - requested page size for Elasticsearch query
   * @param isBrowsingForward - browsing direction
   * @return {@link Optional} of {@link Long} value as range boundary
   */
  public Optional<Long> getRangeBoundaryForBrowsing(String tenant, String anchor, int size, boolean isBrowsingForward) {
    log.debug("getRangeBoundaryForBrowsing:: by [tenant: {}, anchor: {}, size: {}, isBrowsingForward: {}]",
      tenant, anchor, size, isBrowsingForward);

    var ranges = getBrowseRanges(tenant);
    return isNotEmpty(ranges) && isRangeBoundaryCanBeProvided(anchor, isBrowsingForward, ranges)
      ? Optional.ofNullable(getRangeBoundaryFromCachedValue(ranges, anchor, size, isBrowsingForward))
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
    log.debug("getCallNumberRanges:: by [tenant: {}]", tenantId);

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
      .toList();
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

  private static Long getRangeBoundaryFromCachedValue(List<CallNumberBrowseRangeValue> ranges,
                                                      String anchor, int expectedPageSize, boolean isBrowsingForward) {
    var foundPosition = getClosestPosition(ranges, CallNumberBrowseRangeValue.of(anchor, 0, 0), isBrowsingForward);
    return isBrowsingForward
      ? getTopBoundaryForSucceedingQuery(ranges, expectedPageSize, foundPosition)
      : getBottomBoundaryForPrecedingQuery(ranges, expectedPageSize, foundPosition);
  }

  private static Long getTopBoundaryForSucceedingQuery(List<CallNumberBrowseRangeValue> ranges, int size, int pos) {
    var element = ranges.get(pos);
    var sum = element.getCount();

    for (int i = pos + 1; i < ranges.size(); i++) {
      var current = ranges.get(i);
      if (sum >= size) {
        return current.getKeyAsLong();
      }
      sum += current.getCount();
    }

    return null;
  }

  private static Long getBottomBoundaryForPrecedingQuery(List<CallNumberBrowseRangeValue> ranges, int size, int pos) {
    var sum = 0L;

    for (int i = pos - 1; i >= 0; i--) {
      var current = ranges.get(i);
      sum += current.getCount();
      if (sum >= size) {
        return current.getKeyAsLong();
      }
    }

    return null;
  }

  private static <T extends Comparable<T>> int getClosestPosition(List<T> list, T value, boolean isBrowsingForward) {
    var foundPosition = Collections.binarySearch(list, value);
    if (foundPosition >= 0) {
      return foundPosition;
    }

    foundPosition = -foundPosition - (isBrowsingForward ? 1 : 2);
    return Math.min(Math.max(0, foundPosition), list.size() - 1);
  }
}
