package org.folio.search.service.converter;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.utils.JsonTestUtils.jsonArray;
import static org.folio.support.utils.JsonTestUtils.jsonObject;
import static org.folio.support.utils.TestUtils.aggregationsFromJson;
import static org.folio.support.utils.TestUtils.facet;
import static org.folio.support.utils.TestUtils.facetResult;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetItem;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.search.aggregations.Aggregations;
import org.opensearch.search.aggregations.bucket.terms.ParsedStringTerms;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ElasticsearchFacetConverterTest {

  private static final String AGG_NAME = "item";

  @InjectMocks
  private ElasticsearchFacetConverter facetConverter;

  @MethodSource("aggregationsDataProvider")
  @DisplayName("convert_positive_parameterized")
  @ParameterizedTest(name = "[{index}] agg = {0}")
  void convert_positive_parameterized(JsonNode aggregations, Map<String, Facet> expected) {
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
