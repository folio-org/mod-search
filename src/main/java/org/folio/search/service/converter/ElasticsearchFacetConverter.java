package org.folio.search.service.converter;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableMap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.ParsedSingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
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
    var facetsMap = aggregations.asList().stream()
      .filter(agg -> agg.getName() != null)
      .collect(toUnmodifiableMap(Aggregation::getName, agg -> facet(getFacetItems(agg))));
    return facetResult(facetsMap);
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
    for (var nestedAggregation : agg.getAggregations()) {
      facetItems.addAll(getFacetItems(nestedAggregation));
    }
    return facetItems;
  }

  private static List<FacetItem> getFacetItemsFromParsedTerms(ParsedTerms parsedTerms) {
    var buckets = parsedTerms.getBuckets();
    if (CollectionUtils.isEmpty(buckets)) {
      return emptyList();
    }
    return buckets.stream().map(ElasticsearchFacetConverter::facetItem).collect(toList());
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
}
