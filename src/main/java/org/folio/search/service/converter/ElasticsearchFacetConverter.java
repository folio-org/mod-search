package org.folio.search.service.converter;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static org.folio.search.utils.CollectionUtils.addToList;
import static org.folio.search.utils.SearchUtils.SELECTED_AGG_PREFIX;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.Aggregations;
import org.opensearch.search.aggregations.bucket.ParsedSingleBucketAggregation;
import org.opensearch.search.aggregations.bucket.terms.ParsedTerms;
import org.opensearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetItem;
import org.folio.search.domain.dto.FacetResult;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchFacetConverter {

  /**
   * Converts elasticsearch {@link Aggregations} object into {@link FacetResult} object.
   *
   * @param aggregations elasticsearch {@link Aggregations} object to analyze and process.
   * @return facet result.
   */
  public FacetResult convert(Aggregations aggregations) {
    if (aggregations == null) {
      return facetResult(emptyMap());
    }

    var facetsMap = new LinkedHashMap<String, Facet>();
    for (var agg : aggregations.asList()) {
      var aggregationName = agg.getName();
      if (aggregationName != null) {
        facetsMap.put(aggregationName, facet(getFacetItems(agg)));
      }
    }

    return facetResult(mergeSelectedAndNormalFacets(facetsMap));
  }

  private static List<FacetItem> getFacetItems(Aggregation aggregation) {
    if (aggregation instanceof ParsedSingleBucketAggregation) {
      return getFacetItemsFromSingleBucketAggregation((ParsedSingleBucketAggregation) aggregation);
    }
    if (aggregation instanceof ParsedTerms) {
      return getFacetItemsFromParsedTerms((ParsedTerms) aggregation);
    }
    return emptyList();
  }

  private static List<FacetItem> getFacetItemsFromSingleBucketAggregation(ParsedSingleBucketAggregation agg) {
    var facetItems = new ArrayList<FacetItem>();
    agg.getAggregations().forEach(nestedAgg ->
      addToList(facetItems, getFacetItems(nestedAgg), isSelectedTermsAggregation(nestedAgg.getName())));
    return facetItems;
  }

  private static List<FacetItem> getFacetItemsFromParsedTerms(ParsedTerms parsedTerms) {
    var buckets = parsedTerms.getBuckets();
    return CollectionUtils.isNotEmpty(buckets)
      ? buckets.stream().map(ElasticsearchFacetConverter::facetItem).collect(toList())
      : emptyList();
  }

  private static Facet facet(List<FacetItem> items) {
    return new Facet().values(items).totalRecords(items.size());
  }

  private static FacetItem facetItem(Bucket bucket) {
    return new FacetItem().id(bucket.getKeyAsString()).totalRecords(BigDecimal.valueOf(bucket.getDocCount()));
  }

  private static FacetResult facetResult(Map<String, Facet> facets) {
    return new FacetResult().facets(facets).totalRecords(facets.size());
  }

  /**
   * Merges selected and not-selected facets in the single key.
   *
   * <p><i>Implementation details:</i></p>
   * <p>Selected facets are prefixed with 'selected_' value. This method should merge facets with prefix and without it
   * into single {@link Facet} object. A 'totalRecords' value in {@link Facet} should be recalculated too after
   * merge.</p>
   *
   * @param facets map with elastic
   * @return {@link Map} object with key as the facet name, value as the {@link Facet} object
   */
  private static Map<String, Facet> mergeSelectedAndNormalFacets(Map<String, Facet> facets) {
    var result = new LinkedHashMap<String, Facet>();

    facets.forEach((key, value) -> {
      var isSelectedAggregation = isSelectedTermsAggregation(key);
      var finalKey = isSelectedAggregation ? key.substring(SELECTED_AGG_PREFIX.length()) : key;
      var facet = result.computeIfAbsent(finalKey, v -> facet(new ArrayList<>()));
      var facetValues = value.getValues();
      addToList(facet.getValues(), facetValues, isSelectedAggregation);
      facet.setTotalRecords(facet.getTotalRecords() + facetValues.size());
    });

    return result;
  }

  private static boolean isSelectedTermsAggregation(String name) {
    return name.startsWith(SELECTED_AGG_PREFIX);
  }
}
