package org.folio.search.service.converter;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.common.xcontent.DeprecationHandler.IGNORE_DEPRECATIONS;
import static org.folio.search.utils.JsonUtils.jsonArray;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.TestUtils.facet;
import static org.folio.search.utils.TestUtils.facetResult;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.analytics.ParsedStringStats;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ContextParser;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetItem;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ElasticsearchFacetConverterTest {

  private static final NamedXContentRegistry NAMED_XCONTENT_REGISTRY =
    new NamedXContentRegistry(getNamedContentRegistryEntries());
  private static final String AGG_NAME = "item";

  @InjectMocks private ElasticsearchFacetConverter facetConverter;

  @MethodSource("aggregationsDataProvider")
  @DisplayName("convert_positive_parameterized")
  @ParameterizedTest(name = "[{index}] agg = {0}")
  void convert_positive_parameterized(JsonNode aggregations, Map<String, Facet> expected) throws Exception {
    var actual = facetConverter.convert(aggregationsFromJson(aggregations));
    assertThat(actual).isEqualTo(facetResult(expected));
  }

  @Test
  void convert_negative_nullAggregationName() {
    var actual = facetConverter.convert(new Aggregations(List.of(new ParsedStringTerms())));
    assertThat(actual).isEqualTo(facetResult(emptyMap()));
  }

  private static Stream<Arguments> aggregationsDataProvider() {
    return Stream.of(
      arguments(null, emptyMap()),
      arguments(jsonObject(), emptyMap()),
      arguments(jsonObject("sterms#item", null), emptyMap()),
      arguments(jsonObject("sterms#item", jsonObject("buckets", jsonArray())), mapOf(AGG_NAME, facet(emptyList()))),
      arguments(stringStatsAggregation(), mapOf(AGG_NAME, facet(emptyList()))),
      arguments(filterFacetAggregation(), mapOf(AGG_NAME, facet(List.of(facetItem("v1", 200), facetItem("v2", 100))))),
      arguments(termsFacetAggregation(), mapOf(AGG_NAME, facet(List.of(facetItem("marc", 5), facetItem("folio", 2))))),
      arguments(termsFacetAggregationWithSelectedTerms(), mapOf(AGG_NAME, facet(List.of(
        facetItem("custom", 10), facetItem("marc", 199), facetItem("folio", 25))))),
      arguments(termsFacetAggregationWithOnlySelectedTerms(), mapOf(AGG_NAME, facet(List.of(facetItem("custom", 10))))),
      arguments(filterFacetAggregationWithSelectedTerms(), mapOf(AGG_NAME, facet(List.of(
        facetItem("v3", 300), facetItem("v4", 10), facetItem("v1", 200), facetItem("v2", 100))))),
      arguments(filterFacetAggregationWithOnlySelectedTerms(), mapOf(AGG_NAME, facet(List.of(
        facetItem("v3", 300), facetItem("v4", 10)))))
    );
  }

  public static Aggregations aggregationsFromJson(JsonNode aggregationNode) throws Exception {
    XContentParser parser = JsonXContent.jsonXContent.createParser(
      NAMED_XCONTENT_REGISTRY, IGNORE_DEPRECATIONS, searchResponse(aggregationNode).toString());
    return SearchResponse.fromXContent(parser).getAggregations();
  }

  public static List<NamedXContentRegistry.Entry> getNamedContentRegistryEntries() {
    Map<String, ContextParser<Object, ? extends Aggregation>> map = new HashMap<>();
    map.put("sterms", (p, c) -> ParsedStringTerms.fromXContent(p, (String) c));
    map.put("filter", (p, c) -> ParsedFilter.fromXContent(p, (String) c));
    map.put("string_stats", (p, c) -> ParsedStringStats.PARSER.parse(p, (String) c));
    return map.entrySet().stream()
      .map(v -> new NamedXContentRegistry.Entry(Aggregation.class, new ParseField(v.getKey()), v.getValue()))
      .collect(toList());
  }

  private static JsonNode searchResponse(JsonNode aggregationValue) {
    return jsonObject(
      "took", 0,
      "timed_out", false,
      "_shards", jsonObject("total", 1, "successful", 1, "skipped", 0, "failed", 0),
      "hits", jsonObject("total", jsonObject("value", 0, "relation", "eq"), "max_score", null, "hits", jsonArray()),
      "aggregations", aggregationValue);
  }

  private static ObjectNode filterFacetAggregation() {
    return jsonObject("filter#item", jsonObject(
      "sterms#values", jsonObject("buckets", jsonArray(
        jsonObject("key", "v1", "doc_count", 200),
        jsonObject("key", "v2", "doc_count", 100)))));
  }

  private static ObjectNode filterFacetAggregationWithSelectedTerms() {
    return jsonObject("filter#item", jsonObject(
      "sterms#values", jsonObject("buckets", jsonArray(
        jsonObject("key", "v1", "doc_count", 200),
        jsonObject("key", "v2", "doc_count", 100))),
      "sterms#selected_values", jsonObject("buckets", jsonArray(
        jsonObject("key", "v3", "doc_count", 300),
        jsonObject("key", "v4", "doc_count", 10)))));
  }

  private static ObjectNode filterFacetAggregationWithOnlySelectedTerms() {
    return jsonObject("filter#item", jsonObject(
      "sterms#selected_values", jsonObject("buckets", jsonArray(
        jsonObject("key", "v3", "doc_count", 300),
        jsonObject("key", "v4", "doc_count", 10)))));
  }

  private static ObjectNode termsFacetAggregation() {
    return jsonObject("sterms#item", jsonObject("buckets", jsonArray(
      jsonObject("key", "marc", "doc_count", 5),
      jsonObject("key", "folio", "doc_count", 2))));
  }

  private static ObjectNode termsFacetAggregationWithSelectedTerms() {
    return jsonObject(
      "sterms#item", jsonObject("buckets", jsonArray(
        jsonObject("key", "marc", "doc_count", 199),
        jsonObject("key", "folio", "doc_count", 25))),
      "sterms#selected_item", jsonObject("buckets", jsonArray(
        jsonObject("key", "custom", "doc_count", 10))));
  }

  private static ObjectNode termsFacetAggregationWithOnlySelectedTerms() {
    return jsonObject(
      "sterms#selected_item", jsonObject("buckets", jsonArray(
        jsonObject("key", "custom", "doc_count", 10))));
  }

  private static ObjectNode stringStatsAggregation() {
    return jsonObject("string_stats#item", jsonObject(
      "count", 20, "min_length", 1, "max_length", 20, "avg_length", 10, "entropy", 2.5f));
  }

  private static FacetItem facetItem(String id, int value) {
    return new FacetItem().id(id).totalRecords(BigDecimal.valueOf(value));
  }
}
