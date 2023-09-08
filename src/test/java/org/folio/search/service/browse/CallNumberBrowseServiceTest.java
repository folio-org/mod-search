package org.folio.search.service.browse;

import static java.util.Arrays.stream;
import static org.apache.lucene.search.TotalHits.Relation.EQUAL_TO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_BROWSING_FIELD;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.cnBrowseItem;
import static org.folio.search.utils.TestUtils.getShelfKeyFromCallNumber;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;

import java.util.List;
import org.apache.lucene.search.TotalHits;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.search.MultiSearchResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.z3950.zing.cql.CQLTermNode;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CallNumberBrowseServiceTest {

  private static final String ANCHOR = "B";
  @InjectMocks
  private CallNumberBrowseService callNumberBrowseService;

  @Mock
  private SearchRepository searchRepository;
  @Mock
  private BrowseContextProvider browseContextProvider;
  @Mock
  private CallNumberBrowseQueryProvider browseQueryProvider;
  @Mock
  private CallNumberBrowseResultConverter browseResultConverter;

  @Mock
  private SearchResponse additionalResponse;
  @Mock
  private SearchResponse precedingResponse;
  @Mock
  private SearchResponse succeedingResponse;
  @Mock
  private SearchSourceBuilder precedingQuery;
  @Mock
  private SearchSourceBuilder succeedingQuery;
  @Mock
  private CqlSearchQueryConverter cqlSearchQueryConverter;

  @BeforeEach
  void setUp() {
    callNumberBrowseService.setBrowseContextProvider(browseContextProvider);
  }

  @Test
  void browse_positive_around() {
    var request = request("callNumber >= B or callNumber < B", true);
    when(cqlSearchQueryConverter.convertToTermNode(anyString(), anyString()))
      .thenReturn(new CQLTermNode(null, null, "B"));
    prepareMockForBrowsingAround(request,
      contextAroundIncluding(),
      BrowseResult.of(4, browseItems("A1", "A2", "A3", "A4")),
      BrowseResult.of(7, browseItems("C1", "C2", "C3", "C4", "C5", "C6", "C7")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(11, "A 13", "C 12", List.of(
      cnBrowseItem(instance("A3"), "A3"),
      cnBrowseItem(instance("A4"), "A4"),
      cnBrowseItem(0, "B", true),
      cnBrowseItem(instance("C1"), "C1"),
      cnBrowseItem(instance("C2"), "C2"))));
  }

  @Test
  void browse_positive_aroundWithFoundAnchor() {
    var request = request("callNumber >= B or callNumber < B", true);

    when(cqlSearchQueryConverter.convertToTermNode(anyString(), anyString()))
      .thenReturn(new CQLTermNode(null, null, "B"));
    prepareMockForBrowsingAround(request,
      contextAroundIncluding(),
      BrowseResult.of(1, browseItems("A 11", "A 12")),
      BrowseResult.of(2, browseItems("B", "C 11")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(3, List.of(
      cnBrowseItem(instance("A 11"), "A 11"),
      cnBrowseItem(instance("A 12"), "A 12"),
      cnBrowseItem(instance("B"), "B", true),
      cnBrowseItem(instance("C 11"), "C 11"))));
  }

  @Test
  void browse_positive_around_additionalRequest_when_emptyPrecedingResults() {
    var request = request("callNumber >= B or callNumber < B", true);
    var precedingResult = BrowseResult.of(1, browseItems());
    var additionalPrecedingResult = BrowseResult.of(1, browseItems("A"));
    var succeedingResult = BrowseResult.of(1, browseItems("B"));

    when(cqlSearchQueryConverter.convertToTermNode(anyString(), anyString()))
      .thenReturn(new CQLTermNode(null, null, "B"));

    prepareMockForBrowsingAround(request, contextAroundIncluding(), precedingResult, succeedingResult);
    prepareMockForAdditionalRequest(request, contextAroundIncluding(), additionalPrecedingResult);

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(2, List.of(
      cnBrowseItem(instance("A"), "A"),
      cnBrowseItem(instance("B"), "B", true)
    )));
  }

  @Test
  void browse_positive_around_emptySucceedingResults() {
    var request = request("callNumber >= B or callNumber < B", true);
    when(cqlSearchQueryConverter.convertToTermNode(anyString(), anyString()))
      .thenReturn(new CQLTermNode(null, null, "B"));
    prepareMockForBrowsingAround(request,
      contextAroundIncluding(), BrowseResult.of(1, browseItems("A 11", "A 12")), BrowseResult.empty());

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(1, List.of(
      cnBrowseItem(instance("A 11"), "A 11"),
      cnBrowseItem(instance("A 12"), "A 12"),
      cnBrowseItem(0, "B", true))));
  }

  @Test
  void browse_positive_around_noHighlightMatch() {
    var request = request("callNumber >= B or callNumber < B", false);

    prepareMockForBrowsingAround(request,
      contextAroundIncluding(),
      BrowseResult.of(1, browseItems("A 11", "A 12")),
      BrowseResult.of(1, browseItems("C 11")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(2, List.of(
      cnBrowseItem(instance("A 11"), "A 11"),
      cnBrowseItem(instance("A 12"), "A 12"),
      cnBrowseItem(instance("C 11"), "C 11"))));
  }

  @Test
  void browse_positive_around_highlightMatchWithSuffix() {
    var request = request("callNumber >= B or callNumber < B", true);

    when(cqlSearchQueryConverter.convertToTermNode(anyString(), anyString()))
      .thenReturn(new CQLTermNode(null, null, "B"));

    prepareMockForBrowsingAround(request,
      contextAroundIncluding(),
      BrowseResult.empty(),
      BrowseResult.of(1, List.of(browseItemWithSuffix("B", "2005"))));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual.getRecords().get(0).getIsAnchor()).isTrue();
  }

  @Test
  void browse_positive_around_noResults() {
    var request = request("callNumber >= B or callNumber < B", false);
    prepareMockForBrowsingAround(request, contextAroundIncluding(), BrowseResult.empty(), BrowseResult.empty());
    var actual = callNumberBrowseService.browse(request);
    assertThat(actual).isEqualTo(BrowseResult.empty());
  }

  @Test
  void browse_positive_forward() {
    var request = request("callNumber >= B", false);
    var query = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte(ANCHOR);
    var context = BrowseContext.builder().succeedingQuery(query).succeedingLimit(5).anchor(ANCHOR).build();

    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, true)).thenReturn(succeedingQuery);
    when(searchRepository.search(request, succeedingQuery)).thenReturn(succeedingResponse);
    when(browseResultConverter.convert(succeedingResponse, context, true)).thenReturn(
      BrowseResult.of(2, browseItems("C1", "C2")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(2, "C 11", null, List.of(
      cnBrowseItem(instance("C1"), "C1"), cnBrowseItem(instance("C2"), "C2"))));
  }

  @Test
  void browse_positive_emptyAnchor() {
    var request = request("callNumber >= []", false);
    var query = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte(ANCHOR);
    var context = BrowseContext.builder().succeedingQuery(query).succeedingLimit(5).anchor("").build();
    when(browseContextProvider.get(request)).thenReturn(context);

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.empty());
  }

  @Test
  void browse_positive_forwardWithNextValue() {
    var request = request("callNumber >= B", false);
    var query = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte(ANCHOR);
    var context = BrowseContext.builder().succeedingQuery(query).succeedingLimit(2).anchor(ANCHOR).build();

    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, true)).thenReturn(succeedingQuery);
    when(searchRepository.search(request, succeedingQuery)).thenReturn(succeedingResponse);
    when(browseResultConverter.convert(succeedingResponse, context, true)).thenReturn(
      BrowseResult.of(5, browseItems("C1", "C2", "C3", "C4", "C5")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(5, "C 11", "C 12", List.of(
      cnBrowseItem(instance("C1"), "C1"), cnBrowseItem(instance("C2"), "C2"))));
  }

  @Test
  void browse_positive_forwardZeroResults() {
    var request = request("callNumber >= B", false);
    var query = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte(ANCHOR);
    var context = BrowseContext.builder().succeedingQuery(query).succeedingLimit(5).anchor(ANCHOR).build();

    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, true)).thenReturn(succeedingQuery);
    when(searchRepository.search(request, succeedingQuery)).thenReturn(succeedingResponse);
    when(browseResultConverter.convert(succeedingResponse, context, true)).thenReturn(BrowseResult.empty());

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.empty());
  }

  @Test
  void browse_positive_backward() {
    var request = request("callNumber < B", false);
    var query = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(ANCHOR);
    var context = BrowseContext.builder().precedingQuery(query).precedingLimit(5).anchor(ANCHOR).build();

    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, false)).thenReturn(precedingQuery);
    when(searchRepository.search(request, precedingQuery)).thenReturn(precedingResponse);
    when(browseResultConverter.convert(precedingResponse, context, false)).thenReturn(
      BrowseResult.of(2, browseItems("A1", "A2")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(2, null, "A 12", List.of(
      cnBrowseItem(instance("A1"), "A1"), cnBrowseItem(instance("A2"), "A2"))));
  }

  @Test
  void browse_positive_backwardWithPrevValue() {
    var request = request("callNumber < B", false);
    var query = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(ANCHOR);
    var context = BrowseContext.builder().precedingQuery(query).precedingLimit(2).anchor(ANCHOR).build();

    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, false)).thenReturn(precedingQuery);
    when(searchRepository.search(request, precedingQuery)).thenReturn(precedingResponse);
    when(browseResultConverter.convert(precedingResponse, context, false)).thenReturn(
      BrowseResult.of(5, browseItems("A1", "A2", "A3", "A4", "A5")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(5, "A 14", "A 15", List.of(
      cnBrowseItem(instance("A4"), "A4"), cnBrowseItem(instance("A5"), "A5"))));
  }

  @Test
  void browse_positive_backwardZeroResults() {
    var request = request("callNumber < B", false);
    var query = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(ANCHOR);
    var context = BrowseContext.builder().precedingQuery(query).precedingLimit(5).anchor(ANCHOR).build();

    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, false)).thenReturn(precedingQuery);
    when(searchRepository.search(request, precedingQuery)).thenReturn(precedingResponse);
    when(browseResultConverter.convert(precedingResponse, context, false)).thenReturn(BrowseResult.empty());

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.empty());
  }

  private void prepareMockForBrowsingAround(BrowseRequest request, BrowseContext context,
                                            BrowseResult<CallNumberBrowseItem> precedingResult,
                                            BrowseResult<CallNumberBrowseItem> succeedingResult) {
    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, false)).thenReturn(precedingQuery);
    when(browseQueryProvider.get(request, context, true)).thenReturn(succeedingQuery);

    var msearchResponse = msearchResponse(precedingResponse, succeedingResponse);
    when(searchRepository.msearch(request, List.of(precedingQuery, succeedingQuery))).thenReturn(msearchResponse);
    when(browseResultConverter.convert(precedingResponse, context, false)).thenReturn(precedingResult);
    when(browseResultConverter.convert(succeedingResponse, context, false)).thenReturn(BrowseResult.empty());
    when(browseResultConverter.convert(succeedingResponse, context, true)).thenReturn(succeedingResult);
  }

  private void prepareMockForAdditionalRequest(BrowseRequest request, BrowseContext context,
                                               BrowseResult<CallNumberBrowseItem> additionalResult) {
    var mockHits = mock(SearchHits.class);
    when(precedingQuery.from()).thenReturn(1);
    when(precedingQuery.from(anyInt())).thenReturn(precedingQuery);
    when(additionalResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(new TotalHits(additionalResult.getTotalRecords(), EQUAL_TO));

    when(searchRepository.search(request, precedingQuery)).thenReturn(additionalResponse);
    when(browseResultConverter.convert(additionalResponse, context, false)).thenReturn(additionalResult);
  }

  private static MultiSearchResponse msearchResponse(SearchResponse... responses) {
    var msearchItems = stream(responses)
      .map(response -> new MultiSearchResponse.Item(response, null))
      .toArray(MultiSearchResponse.Item[]::new);
    return new MultiSearchResponse(msearchItems, 0);
  }

  private static Instance instance(String... callNumbers) {
    var items = stream(callNumbers)
      .map(callNumber -> new Item()
        .effectiveShelvingOrder(getShelfKeyFromCallNumber(callNumber))
        .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents().callNumber(callNumber)))
      .toList();
    return new Instance().items(items);
  }

  private static BrowseContext contextAroundIncluding() {
    return BrowseContext.builder()
      .precedingQuery(rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(ANCHOR))
      .succeedingQuery(rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte(ANCHOR))
      .precedingLimit(2)
      .succeedingLimit(3)
      .anchor(ANCHOR)
      .build();
  }

  private static BrowseRequest request(String query, boolean highlightMatch) {
    return BrowseRequest.builder().tenantId(TENANT_ID).resource(RESOURCE_NAME)
      .query(query)
      .highlightMatch(highlightMatch)
      .expandAll(false)
      .targetField(CALL_NUMBER_BROWSING_FIELD)
      .limit(5)
      .precedingRecordsCount(2)
      .build();
  }

  private static CallNumberBrowseItem browseItem(String callNumber) {
    return new CallNumberBrowseItem()
      .fullCallNumber(callNumber)
      .shelfKey(getShelfKeyFromCallNumber(callNumber))
      .instance(instance(callNumber))
      .totalRecords(1);
  }

  private static CallNumberBrowseItem browseItemWithSuffix(String callNumber, String suffix) {
    var instance = instance(callNumber);
    instance.getItems().get(0).getEffectiveCallNumberComponents().setSuffix(suffix);
    return new CallNumberBrowseItem()
      .fullCallNumber(callNumber)
      .shelfKey(getShelfKeyFromCallNumber(callNumber) + " " + suffix)
      .instance(instance)
      .totalRecords(1);
  }

  private static List<CallNumberBrowseItem> browseItems(String... shelfKeys) {
    return stream(shelfKeys)
      .map(CallNumberBrowseServiceTest::browseItem)
      .toList();
  }
}
