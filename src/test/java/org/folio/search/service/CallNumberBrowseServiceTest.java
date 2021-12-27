package org.folio.search.service;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.script.Script.DEFAULT_SCRIPT_LANG;
import static org.elasticsearch.script.ScriptType.INLINE;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.elasticsearch.search.sort.ScriptSortBuilder.ScriptSortType.NUMBER;
import static org.elasticsearch.search.sort.SortBuilders.scriptSort;
import static org.folio.search.service.CallNumberBrowseService.CALL_NUMBER_BROWSING_FIELD;
import static org.folio.search.service.CallNumberBrowseService.SORT_SCRIPT_FOR_PRECEDING_QUERY;
import static org.folio.search.service.CallNumberBrowseService.SORT_SCRIPT_FOR_SUCCEEDING_QUERY;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.array;
import static org.folio.search.utils.TestUtils.cnBrowseItem;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.searchResult;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.configuration.properties.SearchQueryConfigurationProperties;
import org.folio.search.cql.CqlQueryParser;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.CallNumberBrowseRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.service.setter.instance.CallNumberProcessor;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.marc4j.callnum.LCCallNumber;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CallNumberBrowseServiceTest {

  private static final long ANCHOR = (long) 1e17;
  private static final long OFFSET = (long) 0.5e17;

  @Spy private final CqlQueryParser cqlQueryParser = new CqlQueryParser();
  @Spy private final SearchQueryConfigurationProperties queryConfiguration =
    SearchQueryConfigurationProperties.of(OFFSET, 2);

  @InjectMocks private CallNumberBrowseService callNumberBrowseService;

  @Mock private SearchRepository searchRepository;
  @Mock private CallNumberProcessor callNumberProcessor;
  @Mock private SearchFieldProvider searchFieldProvider;
  @Mock private CqlSearchQueryConverter queryConverter;
  @Mock private ElasticsearchDocumentConverter documentConverter;

  @Test
  void browseByCallNumber_positive_aroundWhenCallNumberIsNotMatched() {
    var query = "callNumber >= B or callNumber < B";
    var request = CallNumberBrowseRequest.of(RESOURCE_NAME, TENANT_ID, query, 100, true, true, 50);

    prepareMocksForBrowsingAround(request, aroundQuery(),
      List.of(precedingSearchSource(100), succeedingSearchSource(100)),
      searchResult(instance("A 11"), instance("A 12")),
      searchResult(instance("C 11"), instance("C 12"), instance("C 13")));

    var actual = callNumberBrowseService.browseByCallNumber(request);

    assertThat(actual).isEqualTo(SearchResult.of(5, List.of(
      cnBrowseItem(instance("A 12"), "A 12"),
      cnBrowseItem(instance("A 11"), "A 11"),
      cnBrowseItem(0, "B", null),
      cnBrowseItem(instance("C 11"), "C 11"),
      cnBrowseItem(instance("C 12"), "C 12")
    )));

    verify(cqlQueryParser).parseCqlQuery(query, RESOURCE_NAME);
    verify(queryConfiguration, times(2)).getRangeQueryLimitMultiplier();
  }

  @Test
  void browseByCallNumber_positive_aroundWithFilter() {
    var query = "(callNumber >= B or callNumber <B) and effectiveLocation = test-location";
    var request = CallNumberBrowseRequest.of(RESOURCE_NAME, TENANT_ID, query, 5, true, false, 2);

    var filterQuery = termQuery("effectiveLocation", "test-location");
    var esQuery = boolQuery().must(aroundQuery()).filter(filterQuery);
    var searchSources = List.of(
      withFilters(precedingSearchSource(4), filterQuery),
      withFilters(succeedingSearchSource(6), filterQuery));

    prepareMocksForBrowsingAround(request, esQuery, searchSources,
      searchResult(instance("A 11")), searchResult(instance("B"), instance("C 11")));

    var actual = callNumberBrowseService.browseByCallNumber(request);

    assertThat(actual).isEqualTo(SearchResult.of(3, List.of(
      cnBrowseItem(instance("A 11"), "A 11"),
      cnBrowseItem(instance("B"), "B", "B"),
      cnBrowseItem(instance("C 11"), "C 11")
    )));

    verify(queryConfiguration, times(2)).getRangeQueryLimitMultiplier();
  }

  @Test
  void browseByCallNumber_positive_aroundCallNumberMatched() {
    var query = "callNumber >= B or callNumber < B";
    var request = CallNumberBrowseRequest.of(RESOURCE_NAME, TENANT_ID, query, 5, true, true, 2);

    prepareMocksForBrowsingAround(request, aroundQuery(),
      List.of(precedingSearchSource(4), succeedingSearchSource(6)),
      searchResult(instance("A 11")), searchResult(instance("B"), instance("C 11")));

    when(callNumberProcessor.getCallNumberAsLong("B")).thenReturn(ANCHOR);
    var actual = callNumberBrowseService.browseByCallNumber(request);

    assertThat(actual).isEqualTo(SearchResult.of(3, List.of(
      cnBrowseItem(instance("A 11"), "A 11"),
      cnBrowseItem(instance("B"), "<mark>B</mark>", "B"),
      cnBrowseItem(instance("C 11"), "C 11")
    )));

    verify(cqlQueryParser).parseCqlQuery(query, RESOURCE_NAME);
    verify(queryConfiguration, times(2)).getRangeQueryLimitMultiplier();
  }

  @Test
  void browseByCallNumber_positive_aroundEmptySucceedingSearchResult() {
    var query = "callNumber >= B or callNumber < B";
    var request = CallNumberBrowseRequest.of(RESOURCE_NAME, TENANT_ID, query, 5, true, true, 2);

    prepareMocksForBrowsingAround(request, aroundQuery(),
      List.of(precedingSearchSource(4), succeedingSearchSource(6)),
      searchResult(instance("A")), searchResult());

    var actual = callNumberBrowseService.browseByCallNumber(request);

    assertThat(actual).isEqualTo(SearchResult.of(1, List.of(cnBrowseItem(
      instance("A"), "A"), cnBrowseItem(0, "B", null))));

    verify(cqlQueryParser).parseCqlQuery(query, RESOURCE_NAME);
    verify(queryConfiguration, times(2)).getRangeQueryLimitMultiplier();
  }

  @Test
  void browseByCallNumber_positive_browsingForward() {
    var query = "callNumber >= B";
    var esQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte(ANCHOR);
    var request = CallNumberBrowseRequest.of(RESOURCE_NAME, TENANT_ID, query, 10, false, false, 5);
    var instances = searchResult(instance("B 11"), instance("B 12"), instance("B 13"));

    prepareMocksForBrowsing(request, esQuery, succeedingSearchSource(20), instances);

    var actual = callNumberBrowseService.browseByCallNumber(request);

    assertThat(actual).isEqualTo(SearchResult.of(3, List.of(
      cnBrowseItem(instance("B 11"), "B 11"),
      cnBrowseItem(instance("B 12"), "B 12"),
      cnBrowseItem(instance("B 13"), "B 13")
    )));
  }

  @Test
  void browseByCallNumber_positive_browsingForward_collapseDuplicates() {
    var query = "callNumber >= B";
    var esQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte(ANCHOR);
    var request = CallNumberBrowseRequest.of(RESOURCE_NAME, TENANT_ID, query, 10, false, false, 5);
    var instances = searchResult(instance("B 11"), instance("B 11"), instance("B 11"));

    prepareMocksForBrowsing(request, esQuery, succeedingSearchSource(20), instances);

    var actual = callNumberBrowseService.browseByCallNumber(request);

    assertThat(actual).isEqualTo(SearchResult.of(3, List.of(cnBrowseItem(3, "B 11"))));
  }

  @Test
  void browseByCallNumber_positive_browsingForward_multipleCallNumberPerInstance() {
    var query = "callNumber >= B";

    var esQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte(ANCHOR);
    var request = CallNumberBrowseRequest.of(RESOURCE_NAME, TENANT_ID, query, 10, false, false, 5);
    var instances = searchResult(instance("A 11", "B"), instance("A 11", "B 11"),
      instance("A 11", "B 12"), instance("B 14", "B 13"));

    mockCallNumberProcessor();
    prepareMocksForBrowsing(request, esQuery, succeedingSearchSource(20), instances);

    var actual = callNumberBrowseService.browseByCallNumber(request);

    assertThat(actual).isEqualTo(SearchResult.of(4, List.of(
      cnBrowseItem(instance("A 11", "B"), "B"),
      cnBrowseItem(instance("A 11", "B 11"), "B 11"),
      cnBrowseItem(instance("A 11", "B 12"), "B 12"),
      cnBrowseItem(instance("B 14", "B 13"), "B 13")
    )));
  }

  @Test
  void browseByCallNumber_positive_browsingBackward_multipleCallNumberPerInstance() {
    var query = "callNumber < B";
    var esQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(ANCHOR);
    var request = CallNumberBrowseRequest.of(RESOURCE_NAME, TENANT_ID, query, 10, false, false, 5);
    var instances = searchResult(instance("A 12", "B"), instance("A 11", "B 11"), instance("A 11", "B 12"));

    mockCallNumberProcessor();
    prepareMocksForBrowsing(request, esQuery, precedingSearchSource(20), instances);

    var actual = callNumberBrowseService.browseByCallNumber(request);

    assertThat(actual).isEqualTo(SearchResult.of(3, List.of(
      cnBrowseItem(2, "A 11"),
      cnBrowseItem(instance("A 12", "B"), "A 12")
    )));
  }

  @Test
  void browseByCallNumber_positive_browsingBackwardIncluding_multipleCallNumberPerInstance() {
    var query = "callNumber <= B";
    var esQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lte(ANCHOR);
    var request = CallNumberBrowseRequest.of(RESOURCE_NAME, TENANT_ID, query, 10, false, false, 5);
    var instances = searchResult(instance("A 12", "B"), instance("A 11", "B 11"), instance("A 11", "B 12"));

    mockCallNumberProcessor();
    var expectedRangeQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lte(ANCHOR).gte(ANCHOR - OFFSET);
    prepareMocksForBrowsing(request, esQuery, precedingSearchSource(20, expectedRangeQuery, ANCHOR), instances);

    var actual = callNumberBrowseService.browseByCallNumber(request);

    assertThat(actual).isEqualTo(SearchResult.of(3, List.of(
      cnBrowseItem(2, "A 11"),
      cnBrowseItem(instance("A 12", "B"), "B")
    )));
  }

  @Test
  void browseByCallNumber_positive_browsingBackward() {
    var query = "callNumber < B";
    var esQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(ANCHOR);
    var request = CallNumberBrowseRequest.of(RESOURCE_NAME, TENANT_ID, query, 10, false, false, 5);
    var instances = searchResult(instance("A 13"), instance("A 12"), instance("A 11"));

    prepareMocksForBrowsing(request, esQuery, precedingSearchSource(20), instances);

    var actual = callNumberBrowseService.browseByCallNumber(request);

    assertThat(actual).isEqualTo(SearchResult.of(3, List.of(
      cnBrowseItem(instance("A 11"), "A 11"),
      cnBrowseItem(instance("A 12"), "A 12"),
      cnBrowseItem(instance("A 13"), "A 13")
    )));
  }

  @Test
  void browseByCallNumber_positive_browsingBackwardWithFilter() {
    var query = "callNumber < B or effectiveLocation == location";
    var filterQuery = termQuery("effectiveLocation", "location");
    var esQuery = boolQuery().must(rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(ANCHOR)).filter(filterQuery);
    var request = CallNumberBrowseRequest.of(RESOURCE_NAME, TENANT_ID, query, 10, false, false, 5);
    var instances = searchResult(instance("A 13"), instance("A 12"), instance("A 11"));

    prepareMocksForBrowsing(request, esQuery, withFilters(precedingSearchSource(20), filterQuery), instances);

    var actual = callNumberBrowseService.browseByCallNumber(request);

    assertThat(actual).isEqualTo(SearchResult.of(3, List.of(
      cnBrowseItem(instance("A 11"), "A 11"),
      cnBrowseItem(instance("A 12"), "A 12"),
      cnBrowseItem(instance("A 13"), "A 13")
    )));
  }

  @Test
  void browseByCallNumber_positive_browsingForwardLargeAnchor() {
    var query = "callNumber > Z";
    var anchor = Long.MAX_VALUE - 10;
    var esQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gt(anchor);
    var request = CallNumberBrowseRequest.of(RESOURCE_NAME, TENANT_ID, query, 10, false, false, 5);
    var instances = searchResult(instance("Z 99"));

    var expectedRangeQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gt(anchor).lte(Long.MAX_VALUE);
    prepareMocksForBrowsing(request, esQuery, succeedingSearchSource(20, expectedRangeQuery, anchor), instances);

    var actual = callNumberBrowseService.browseByCallNumber(request);

    assertThat(actual).isEqualTo(SearchResult.of(1, List.of(cnBrowseItem(instance("Z 99"), "Z 99"))));
  }

  @Test
  void browseByCallNumber_positive_browsingBackwardSmallAnchor() {
    var query = "callNumber > A 0";
    var esQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(10L);
    var request = CallNumberBrowseRequest.of(RESOURCE_NAME, TENANT_ID, query, 10, false, false, 5);
    var instances = searchResult(instance("A 0"));

    var expectedRangeQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(10L).gte(0L);
    prepareMocksForBrowsing(request, esQuery, precedingSearchSource(20, expectedRangeQuery, 10L), instances);

    var actual = callNumberBrowseService.browseByCallNumber(request);

    assertThat(actual).isEqualTo(SearchResult.of(1, List.of(cnBrowseItem(instance("A 0"), "A 0"))));
  }

  @Test
  void browseByCallNumber_negative_queryWithSorting() {
    var query = "callNumber > A sortby title";
    var esQuery = termQuery("field", 10);
    var request = CallNumberBrowseRequest.of(RESOURCE_NAME, TENANT_ID, query, 10, false, false, 5);
    when(queryConverter.convert(query, RESOURCE_NAME)).thenReturn(searchSource().query(esQuery).sort("sort_title"));
    assertThatThrownBy(() -> callNumberBrowseService.browseByCallNumber(request))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid CQL query for call-number browsing, 'sortBy' is not supported");
  }

  @MethodSource("invalidQueriesDataProvider")
  @ParameterizedTest(name = "[{index}] query = {0}")
  @DisplayName("browseByCallNumber_negative_parameterized")
  void browseByCallNumber_negative_parameterized(String query, String errorMessage, QueryBuilder esQuery) {
    var request = CallNumberBrowseRequest.of(RESOURCE_NAME, TENANT_ID, query, 10, false, false, 5);
    when(queryConverter.convert(query, RESOURCE_NAME)).thenReturn(searchSource().query(esQuery));
    assertThatThrownBy(() -> callNumberBrowseService.browseByCallNumber(request))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage(errorMessage);
  }

  private static Stream<Arguments> invalidQueriesDataProvider() {
    var invalidQueryMessage = "Invalid CQL query for call-number browsing.";
    var invalidAnchorMessage = invalidQueryMessage + " Anchors must be the same in range conditions.";
    var locationQuery = termQuery("location", "test");
    return Stream.of(
      arguments("field < 10", invalidQueryMessage, rangeQuery("field").lt(10)),
      arguments("field = 10 sortBy title", invalidQueryMessage, termQuery("field", 10)),

      arguments("language = eng and location = test", invalidQueryMessage, boolQuery()
        .must(termQuery("language", "eng")).must(locationQuery)),

      arguments("language = eng not location = test", invalidQueryMessage, boolQuery()
        .must(termQuery("language", "eng")).mustNot(locationQuery)),

      arguments("callNumber >= A or field < 10", invalidQueryMessage, boolQuery()
        .should(rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte((long) 1e16))
        .should(rangeQuery("field").lt(10))),

      arguments("field = (1 or 2 or 3) and location == test", invalidQueryMessage, boolQuery()
        .must(boolQuery().should(termQuery("field", 1)).should(termQuery("field", 2)).should(termQuery("field", 3))
          .filter(locationQuery))),

      arguments("field = 3 and location == test", invalidQueryMessage, boolQuery()
        .must(termQuery("field", 1)).filter(locationQuery)),

      arguments("(callNumber >= A or field < 10) and location == test", invalidQueryMessage, boolQuery()
        .must(boolQuery()
          .should(rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte((long) 1e16))
          .should(rangeQuery("field").lt(10)))
        .filter(locationQuery)),

      arguments("callNumber >= A or callNumber < B", invalidAnchorMessage, boolQuery()
        .should(rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte((long) 1e16))
        .should(rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(ANCHOR))),

      arguments("(callNumber >= A or callNumber < B) and location == test", invalidAnchorMessage, boolQuery()
        .filter(locationQuery).must(boolQuery()
          .should(rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte((long) 1e16))
          .should(rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(ANCHOR))))
    );
  }

  private void prepareMocksForBrowsing(CallNumberBrowseRequest request, QueryBuilder query,
    SearchSourceBuilder source, SearchResult<Instance> result) {
    var searchResponse = mock(SearchResponse.class);

    if (isFalse(request.getExpandAll())) {
      when(searchFieldProvider.getSourceFields(request.getResource())).thenReturn(List.of("id", "title"));
      source.fetchSource(new String[] {"id", "title"}, null);
    }

    when(queryConverter.convert(request.getQuery(), RESOURCE_NAME)).thenReturn(searchSource().query(query));
    when(searchRepository.search(request, source)).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, Instance.class)).thenReturn(result);
  }

  private void prepareMocksForBrowsingAround(CallNumberBrowseRequest request, QueryBuilder query,
    List<SearchSourceBuilder> sources, SearchResult<Instance> precedingRs, SearchResult<Instance> succeedingRs) {

    var multiSearchResponse = mock(MultiSearchResponse.class);
    var precedingSearchItem = mock(MultiSearchResponse.Item.class);
    var succeedingSearchItem = mock(MultiSearchResponse.Item.class);
    var precedingResponse = mock(SearchResponse.class);
    var succeedingResponse = mock(SearchResponse.class);

    when(queryConverter.convert(request.getQuery(), RESOURCE_NAME)).thenReturn(searchSource().query(query));
    when(searchRepository.msearch(request, sources)).thenReturn(multiSearchResponse);
    when(multiSearchResponse.getResponses()).thenReturn(array(precedingSearchItem, succeedingSearchItem));
    when(precedingSearchItem.getResponse()).thenReturn(precedingResponse);
    when(succeedingSearchItem.getResponse()).thenReturn(succeedingResponse);
    when(documentConverter.convertToSearchResult(precedingResponse, Instance.class)).thenReturn(precedingRs);
    when(documentConverter.convertToSearchResult(succeedingResponse, Instance.class)).thenReturn(succeedingRs);
  }

  private static QueryBuilder aroundQuery() {
    return boolQuery()
      .should(rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(ANCHOR))
      .should(rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte(ANCHOR));
  }

  private static SearchSourceBuilder precedingSearchSource(int size) {
    var rangeQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(ANCHOR).gte(ANCHOR - OFFSET);
    return precedingSearchSource(size, rangeQuery, ANCHOR);
  }

  private static SearchSourceBuilder precedingSearchSource(int size, QueryBuilder query, long anchor) {
    var script = new Script(INLINE, DEFAULT_SCRIPT_LANG, SORT_SCRIPT_FOR_PRECEDING_QUERY, Map.of("anchor", anchor));
    return searchSource().from(0).size(size).trackTotalHits(true).query(query).sort(scriptSort(script, NUMBER));
  }

  private static SearchSourceBuilder succeedingSearchSource(int size) {
    var rangeQuery = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte(ANCHOR).lte(ANCHOR + OFFSET);
    return succeedingSearchSource(size, rangeQuery, ANCHOR);
  }

  private static SearchSourceBuilder succeedingSearchSource(int size, QueryBuilder query, long anchor) {
    var script = new Script(INLINE, DEFAULT_SCRIPT_LANG, SORT_SCRIPT_FOR_SUCCEEDING_QUERY, Map.of("anchor", anchor));
    return searchSource().from(0).size(size).trackTotalHits(true).query(query).sort(scriptSort(script, NUMBER));
  }

  private static Instance instance(String... shelfKeys) {
    var items = stream(shelfKeys)
      .map(shelfKey -> new Item()
        .effectiveShelvingOrder(shelfKey)
        .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents().callNumber(shelfKey)))
      .collect(toList());
    return new Instance().items(items);
  }

  private static SearchSourceBuilder withFilters(SearchSourceBuilder searchSource, QueryBuilder... filterQueries) {
    var query = searchSource.query();
    var boolQuery = boolQuery().must(query);
    for (var filterQuery : filterQueries) {
      boolQuery.filter(filterQuery);
    }

    return searchSource.query(boolQuery);
  }

  private void mockCallNumberProcessor() {
    var map = mapOf("A 11", (long) 5e16, "B", ANCHOR, "B 11", (long) 11e16,
      "B 12", (long) 12e16, "B 13", (long) 13e16, "B 14", (long) 14e16);
    when(callNumberProcessor.getCallNumberAsLong(any())).thenAnswer(inv -> map.get(inv.<String>getArgument(0)));
  }

  @Test
  void name() {
    System.out.println(new LCCallNumber("TK5105.88815 . A58 2004 FT MEADE").getShelfKey());
  }
}
