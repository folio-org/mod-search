package org.folio.search.service.browse;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CN_INTERMEDIATE_VALUES;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_BROWSING_FIELD;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.cnBrowseItem;
import static org.folio.search.utils.TestUtils.getShelfKeyFromCallNumber;
import static org.folio.search.utils.TestUtils.toMap;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;

import java.util.List;
import java.util.stream.Stream;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.TotalHits.Relation;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.service.consortium.FeatureConfigServiceDecorator;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.folio.spring.test.type.UnitTest;
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

@UnitTest
@ExtendWith(MockitoExtension.class)
class CallNumberBrowseResultConverterTest {

  @InjectMocks
  private CallNumberBrowseResultConverter resultConverter;
  @Spy
  private ElasticsearchDocumentConverter documentConverter = new ElasticsearchDocumentConverter(OBJECT_MAPPER);

  @Mock
  private SearchHits searchHits;
  @Mock
  private SearchResponse searchResponse;
  @Mock
  private FeatureConfigServiceDecorator featureConfigService;

  @MethodSource("testDataProvider")
  @DisplayName("convert_positive_parameterized")
  @ParameterizedTest(name = "[{index}] {0}")
  void convert_positive_parameterized(@SuppressWarnings("unused") String name,
                                      List<SearchHit> hits, BrowseContext ctx, boolean isBrowsingForward,
                                      List<CallNumberBrowseItem> expected) {
    when(searchResponse.getHits()).thenReturn(searchHits);
    when(searchHits.getHits()).thenReturn(hits.toArray(SearchHit[]::new));
    when(searchHits.getTotalHits()).thenReturn(new TotalHits(100, Relation.EQUAL_TO));
    when(featureConfigService.isEnabled(BROWSE_CN_INTERMEDIATE_VALUES)).thenReturn(true);

    var actual = resultConverter.convert(searchResponse, ctx, isBrowsingForward);

    assertThat(actual).isEqualTo(BrowseResult.of(100, expected));
    verify(documentConverter)
      .convertToSearchResult(any(SearchResponse.class), eq(Instance.class), any());
  }

  @Test
  void convert_positive_forwardZeroResults() {
    when(searchResponse.getHits()).thenReturn(searchHits);
    when(searchHits.getHits()).thenReturn(new SearchHit[0]);
    when(searchHits.getTotalHits()).thenReturn(new TotalHits(0, Relation.EQUAL_TO));

    var actual = resultConverter.convert(searchResponse, forwardContext(), true);

    assertThat(actual).isEqualTo(BrowseResult.empty());
    verify(documentConverter)
      .convertToSearchResult(any(SearchResponse.class), eq(Instance.class), any());
  }

  @Test
  void convert_positive_backwardZeroResults() {
    when(searchResponse.getHits()).thenReturn(searchHits);
    when(searchHits.getHits()).thenReturn(new SearchHit[0]);
    when(searchHits.getTotalHits()).thenReturn(new TotalHits(0, Relation.EQUAL_TO));

    var actual = resultConverter.convert(searchResponse, backwardContext(), false);

    assertThat(actual).isEqualTo(BrowseResult.empty());
    verify(documentConverter)
      .convertToSearchResult(any(SearchResponse.class), eq(Instance.class), any());
  }

  @Test
  void convert_positive_intermediateResultsNotPopulatedForBrowsingForward() {
    var hits = new SearchHit[] {
      searchHit("A", instance("A", "A1", "A2")),
      searchHit("B1", instance("B1", "B2", "C2")),
      searchHit("C1", instance("C1", "C2"))};
    when(searchResponse.getHits()).thenReturn(searchHits);
    when(searchHits.getHits()).thenReturn(hits);
    when(searchHits.getTotalHits()).thenReturn(new TotalHits(10, Relation.EQUAL_TO));
    when(featureConfigService.isEnabled(BROWSE_CN_INTERMEDIATE_VALUES)).thenReturn(false);

    var actual = resultConverter.convert(searchResponse, forwardContext(), true);

    assertThat(actual).isEqualTo(BrowseResult.of(10, List.of(
      cnBrowseItem(instance("B1", "B2", "C2"), "B1"),
      cnBrowseItem(instance("C1", "C2"), "C1"))));
    verify(documentConverter)
      .convertToSearchResult(any(SearchResponse.class), eq(Instance.class), any());
  }

  @Test
  void convert_positive_intermediateResultsNotPopulatedForBrowsingBackward() {
    var hits = new SearchHit[] {
      searchHit("F", instance("E1", "E2", "F")),
      searchHit("C4", instance("C1", "C2", "C4")),
      searchHit("B2", instance("B1", "B2"))};
    when(searchResponse.getHits()).thenReturn(searchHits);
    when(searchHits.getHits()).thenReturn(hits);
    when(searchHits.getTotalHits()).thenReturn(new TotalHits(10, Relation.EQUAL_TO));
    when(featureConfigService.isEnabled(BROWSE_CN_INTERMEDIATE_VALUES)).thenReturn(false);

    var actual = resultConverter.convert(searchResponse, backwardContext(), false);

    assertThat(actual).isEqualTo(BrowseResult.of(10, List.of(
      cnBrowseItem(instance("B1", "B2"), "B2"),
      cnBrowseItem(instance("C1", "C2", "C4"), "C4"))));
    verify(documentConverter)
      .convertToSearchResult(any(SearchResponse.class), eq(Instance.class), any());
  }

  private static Stream<Arguments> testDataProvider() {
    return Stream.of(
      arguments("forward: 1 resource", searchHits("B1"), forwardContext(), true, List.of(browseItem("B1"))),
      arguments("forward: 1 resource(included anchor)", searchHits("A"), forwardContext(), true, emptyList()),
      arguments("forward: 1 resource(excluded anchor)",
        searchHits("A"), forwardIncludingContext(), true, browseItems("A")),

      arguments("forward: 2 resources",
        searchHits("B1", "B2"), forwardContext(), true, browseItems("B1", "B2")),
      arguments("forward: 2 resources (anchor included)",
        searchHits("A", "A1"), forwardIncludingContext(), true, browseItems("A", "A1")),
      arguments("forward: 2 resources (anchor excluded)",
        searchHits("A", "A1"), forwardContext(), true, browseItems("A1")),

      arguments("forward: 2 resources (intermediate call numbers are populated)",
        List.of(searchHit("B1", instance("B1", "B2", "C2")), searchHit("C1", instance("C1", "C2"))),
        forwardContext(), true, List.of(
          cnBrowseItem(instance("B1", "B2", "C2"), "B1"),
          cnBrowseItem(instance("B1", "B2", "C2"), "B2"),
          cnBrowseItem(instance("C1", "C2"), "C1"),
          cnBrowseItem(2, "C2"))),

      arguments("forward: n resources, results collapsed",
        searchHits("A1", "A1", "A2", "A2", "A2", "A3", "A4", "A4", "A5", "A6", "A6"),
        forwardContext(), true, List.of(
          cnBrowseItem(2, "A1"), cnBrowseItem(3, "A2"), browseItem("A3"),
          cnBrowseItem(2, "A4"), browseItem("A5"), cnBrowseItem(2, "A6"))),

      arguments("forward: n resources, invalid resources are ignored (anchor included)",
        searchHits("0", "123", "928", "A", "A1", "A2"), forwardIncludingContext(), true, browseItems("A", "A1", "A2")),

      arguments("forward: n resources, invalid resources are ignored (anchor excluded)",
        searchHits("0", "123", "928", "A", "A1", "A2"), forwardContext(), true, browseItems("A1", "A2")),

      arguments("backward: 1 resource", searchHits("C"), backwardContext(), false, browseItems("C")),
      arguments("backward: 1 resource (excluding anchor)", searchHits("F"), backwardContext(), false, emptyList()),
      arguments("backward: 1 resource (including anchor)",
        searchHits("F"), backwardIncludingContext(), false, browseItems("F")),

      arguments("backward: 2 resources",
        searchHits("E2", "E1"), backwardContext(), false, browseItems("E1", "E2")),
      arguments("forward: 2 resources (anchor included)",
        searchHits("F", "E1"), backwardIncludingContext(), false, browseItems("E1", "F")),
      arguments("forward: 2 resources (anchor excluded)",
        searchHits("F", "E1"), backwardContext(), false, browseItems("E1")),

      arguments("backward: 2 resources (intermediate call numbers are populated)",
        List.of(searchHit("C4", instance("C1", "C2", "C4")), searchHit("B2", instance("B1", "B2"))),
        backwardContext(), false, List.of(
          cnBrowseItem(instance("B1", "B2"), "B1"),
          cnBrowseItem(instance("B1", "B2"), "B2"),
          cnBrowseItem(instance("C1", "C2", "C4"), "C1"),
          cnBrowseItem(instance("C1", "C2", "C4"), "C2"),
          cnBrowseItem(instance("C1", "C2", "C4"), "C4"))),

      arguments("backward: n resources, results collapsed",
        searchHits("E6", "E6", "E6", "E5", "E4", "E4", "E3", "E2", "E2", "E2", "E1"),
        backwardContext(), false, List.of(
          browseItem("E1"), cnBrowseItem(3, "E2"), browseItem("E3"),
          cnBrowseItem(2, "E4"), browseItem("E5"), cnBrowseItem(3, "E6"))),

      arguments("forward: n resources, invalid resources are ignored (anchor included)",
        searchHits("G2", "G1", "F", "E2", "E1"), backwardIncludingContext(), false, browseItems("E1", "E2", "F")),

      arguments("forward: n resources, invalid resources are ignored (anchor excluded)",
        searchHits("G2", "G1", "F", "E2", "E1"), backwardContext(), false, browseItems("E1", "E2"))
    );
  }

  private static BrowseContext forwardIncludingContext() {
    return BrowseContext.builder().anchor("A").succeedingQuery(rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte("A")).build();
  }

  private static BrowseContext forwardContext() {
    return BrowseContext.builder().anchor("A").succeedingQuery(rangeQuery(CALL_NUMBER_BROWSING_FIELD).gt("A")).build();
  }

  private static BrowseContext backwardContext() {
    return BrowseContext.builder().anchor("F").precedingQuery(rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt("F")).build();
  }

  private static BrowseContext backwardIncludingContext() {
    return BrowseContext.builder().anchor("F").precedingQuery(rangeQuery(CALL_NUMBER_BROWSING_FIELD).lte("F")).build();
  }

  private static List<SearchHit> searchHits(String... sortShelfKey) {
    return stream(sortShelfKey).map(CallNumberBrowseResultConverterTest::searchHit).toList();
  }

  private static SearchHit searchHit(String sortShelfKey) {
    return searchHit(sortShelfKey, instance(sortShelfKey));
  }

  private static SearchHit searchHit(String sortCallnumber, Instance instance) {
    var searchHit = mock(SearchHit.class);
    when(searchHit.getSortValues()).thenReturn(new Object[] {getShelfKeyFromCallNumber(sortCallnumber)});
    when(searchHit.getSourceAsMap()).thenReturn(toMap(instance));
    return searchHit;
  }

  private static CallNumberBrowseItem browseItem(String shelfKey) {
    return cnBrowseItem(instance(shelfKey), shelfKey);
  }

  private static List<CallNumberBrowseItem> browseItems(String... shelfKeys) {
    return stream(shelfKeys).map(CallNumberBrowseResultConverterTest::browseItem).toList();
  }

  private static Instance instance(String... callNumbers) {
    var items = stream(callNumbers)
      .map(callNumber -> new Item()
        .effectiveShelvingOrder(getShelfKeyFromCallNumber(callNumber))
        .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents().callNumber(callNumber)))
      .toList();
    return new Instance().items(items);
  }
}
