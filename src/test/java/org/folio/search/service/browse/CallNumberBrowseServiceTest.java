package org.folio.search.service.browse;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.stream;
import static java.util.Collections.singletonList;
import static org.apache.lucene.search.TotalHits.Relation.EQUAL_TO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_BROWSING_FIELD;
import static org.folio.search.utils.SearchUtils.SHELVING_ORDER_BROWSING_FIELD;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.cnBrowseItem;
import static org.folio.search.utils.TestUtils.getShelfKeyFromCallNumber;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;

import java.util.List;
import org.apache.lucene.search.TotalHits;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.cql.EffectiveShelvingOrderTermProcessor;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.integration.ReferenceDataService;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.model.types.CallNumberType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.SearchRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
  private EffectiveShelvingOrderTermProcessor shelvingOrderProcessor;

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
  @Mock
  private ReferenceDataService referenceDataService;
  @Mock
  private SearchConfigurationProperties searchConfig;

  @BeforeEach
  void setUp() {
    callNumberBrowseService.setBrowseContextProvider(browseContextProvider);
    lenient().when(cqlSearchQueryConverter.convertToTermNode(anyString(), any()))
      .thenReturn(new CQLTermNode(null, null, "B"));
    lenient().when(shelvingOrderProcessor.getSearchTerms(ANCHOR)).thenReturn(newArrayList(ANCHOR));
  }

  @Test
  void browse_positive_around() {
    var request = request("callNumber >= B or callNumber < B", true);
    prepareMockForBrowsingAround(request,
      contextAroundIncluding(),
      BrowseResult.of(4, browseItems("A1", "A2", "A3", "A4")),
      BrowseResult.of(7, browseItems("C1", "C2", "C3", "C4", "C5", "C6", "C7")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(11, "A3", "C2", List.of(
      cnBrowseItem(instance("A3"), "A3"),
      cnBrowseItem(instance("A4"), "A4"),
      cnBrowseItem(0, "B", true),
      cnBrowseItem(instance("C1"), "C1"),
      cnBrowseItem(instance("C2"), "C2"))));
  }

  @Test
  void browse_positive_aroundWithFoundAnchor() {
    var request = request("callNumber >= B or callNumber < B", true);

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

    prepareMockForBrowsingAround(request, contextAroundIncluding(), precedingResult, succeedingResult);
    prepareMockForAdditionalRequest(request, contextAroundIncluding(), additionalPrecedingResult);
    when(searchConfig.getMaxBrowseRequestOffset()).thenReturn(500L);

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(2, List.of(
      cnBrowseItem(instance("A"), "A"),
      cnBrowseItem(instance("B"), "B", true)
    )));
  }

  @Test
  void browse_positive_around_emptySucceedingResults() {
    var request = request("callNumber >= B or callNumber < B", true);
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

  @ValueSource(strings = {"B", "B 2005"})
  @ParameterizedTest
  void browse_positive_around_highlightMatchWithSuffix(String callNumber) {
    var request = request(String.format("callNumber >= %s or callNumber < %s", callNumber, callNumber), true);

    when(cqlSearchQueryConverter.convertToTermNode(anyString(), any()))
      .thenReturn(new CQLTermNode(null, null, callNumber));
    lenient().when(shelvingOrderProcessor.getSearchTerms(callNumber)).thenReturn(newArrayList(callNumber));

    prepareMockForBrowsingAround(request,
      contextAroundIncluding(callNumber, callNumber),
      BrowseResult.empty(),
      BrowseResult.of(1, List.of(browseItemWithSuffix("B", "2005"))));

    var actual = callNumberBrowseService.browse(request).getRecords().get(0);

    assertThat(actual.getInstance()).isNotNull();
    assertThat(actual.getIsAnchor()).isTrue();
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

    when(shelvingOrderProcessor.getSearchTerm(any(), any())).thenReturn(context.getAnchor());
    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, true)).thenReturn(succeedingQuery);
    when(searchRepository.search(request, succeedingQuery)).thenReturn(succeedingResponse);
    when(browseResultConverter.convert(succeedingResponse, context, true)).thenReturn(
      BrowseResult.of(2, browseItems("C1", "C2")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(2, "C1", null, List.of(
      cnBrowseItem(instance("C1"), "C1"), cnBrowseItem(instance("C2"), "C2"))));
  }

  @Test
  void browse_positive_forwardMultipleAnchors() {
    var request = request("callNumber >= B", false);
    var query = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte(ANCHOR);
    var multiAnchorContext = BrowseContext.builder().succeedingQuery(query).succeedingLimit(5).anchor(ANCHOR)
      .build();
    var context = BrowseContext.builder().succeedingQuery(query).succeedingLimit(5).anchor(ANCHOR).build();

    when(shelvingOrderProcessor.getSearchTerm(any(), any())).thenReturn(multiAnchorContext.getAnchor());
    when(browseContextProvider.get(request)).thenReturn(multiAnchorContext);
    when(browseQueryProvider.get(request, context, true)).thenReturn(succeedingQuery);
    when(searchRepository.search(request, succeedingQuery)).thenReturn(succeedingResponse);
    when(browseResultConverter.convert(succeedingResponse, context, true)).thenReturn(
      BrowseResult.of(1, browseItems("B")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(1, "B", null, List.of(
      cnBrowseItem(instance("B"), "B"))));
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
  void browse_positive_multipleAnchors() {
    var request = request("callNumber >= B or callNumber < B", true);

    when(cqlSearchQueryConverter.convertToTermNode(anyString(), any()))
      .thenReturn(new CQLTermNode(null, null, "B"));
    lenient().when(shelvingOrderProcessor.getSearchTerms(ANCHOR)).thenReturn(newArrayList("A", "B"));
    when(shelvingOrderProcessor.getSearchTerm(any(), any())).thenReturn("A");

    var precedingResult = BrowseResult.of(1, browseItems("A1", "A2"));
    var succeedingResult = BrowseResult.of(1, browseItems("B"));
    var contextForNoAnchorInResponse = contextAroundIncluding("A", ANCHOR);

    //mocks for request without anchor in response
    when(browseContextProvider.get(request)).thenReturn(contextForNoAnchorInResponse);
    when(browseQueryProvider.get(request, contextForNoAnchorInResponse, false)).thenReturn(precedingQuery);
    when(browseQueryProvider.get(request, contextForNoAnchorInResponse, true)).thenReturn(succeedingQuery);
    var msearchResponse = msearchResponse(precedingResponse, succeedingResponse);
    when(searchRepository.msearch(request, List.of(precedingQuery, succeedingQuery))).thenReturn(msearchResponse);
    when(browseResultConverter.convert(succeedingResponse, contextForNoAnchorInResponse, true))
      .thenReturn(succeedingResult);

    var contextForAnchorInResponse = contextAroundIncluding(ANCHOR, ANCHOR);

    //mock for request with anchor in response
    when(browseQueryProvider.get(request, contextForAnchorInResponse, false)).thenReturn(precedingQuery);
    when(browseQueryProvider.get(request, contextForAnchorInResponse, true)).thenReturn(succeedingQuery);
    when(searchRepository.msearch(request, List.of(precedingQuery, succeedingQuery))).thenReturn(msearchResponse);
    when(browseResultConverter.convert(precedingResponse, contextForAnchorInResponse, false))
      .thenReturn(precedingResult);
    when(browseResultConverter.convert(succeedingResponse, contextForAnchorInResponse, false))
      .thenReturn(BrowseResult.empty());
    when(browseResultConverter.convert(precedingResponse, contextForAnchorInResponse, true))
      .thenReturn(BrowseResult.empty());
    when(browseResultConverter.convert(succeedingResponse, contextForAnchorInResponse, true))
      .thenReturn(succeedingResult);

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(2, List.of(
      cnBrowseItem(instance("A1"), "A1"),
      cnBrowseItem(instance("A2"), "A2"),
      cnBrowseItem(instance("B"), "B", true)
    )));
  }

  @Test
  void browse_positive_forwardWithNextValue() {
    var request = request("callNumber >= B", false);
    var query = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte(ANCHOR);
    var context = BrowseContext.builder().succeedingQuery(query).succeedingLimit(2).anchor(ANCHOR).build();

    when(shelvingOrderProcessor.getSearchTerm(any(), any())).thenReturn(context.getAnchor());
    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, true)).thenReturn(succeedingQuery);
    when(searchRepository.search(request, succeedingQuery)).thenReturn(succeedingResponse);
    when(browseResultConverter.convert(succeedingResponse, context, true)).thenReturn(
      BrowseResult.of(5, browseItems("C1", "C2", "C3", "C4", "C5")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(5, "C1", "C2", List.of(
      cnBrowseItem(instance("C1"), "C1"), cnBrowseItem(instance("C2"), "C2"))));
  }

  @Test
  void browse_positive_forwardZeroResults() {
    var request = request("callNumber >= B", false);
    var query = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte(ANCHOR);
    var context = BrowseContext.builder().succeedingQuery(query).succeedingLimit(5).anchor(ANCHOR).build();

    when(shelvingOrderProcessor.getSearchTerm(any(), any())).thenReturn(context.getAnchor());
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

    when(shelvingOrderProcessor.getSearchTerm(any(), any())).thenReturn(context.getAnchor());
    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, false)).thenReturn(precedingQuery);
    when(searchRepository.search(request, precedingQuery)).thenReturn(precedingResponse);
    when(browseResultConverter.convert(precedingResponse, context, false)).thenReturn(
      BrowseResult.of(2, browseItems("A1", "A2")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(2, null, "A2", List.of(
      cnBrowseItem(instance("A1"), "A1"), cnBrowseItem(instance("A2"), "A2"))));
  }

  @Test
  void browse_positive_backwardShelvingOrderTyped() {
    var request = request("itemEffectiveShelvingOrder < B", SHELVING_ORDER_BROWSING_FIELD, false, "lc");
    var query = rangeQuery(SHELVING_ORDER_BROWSING_FIELD).lt(ANCHOR);
    var context = BrowseContext.builder().precedingQuery(query).precedingLimit(5).anchor(ANCHOR).build();
    var browseItems = browseItems("A1", "A2");
    browseItems.forEach(browseItem -> browseItem.getInstance().getItems().get(0).getEffectiveCallNumberComponents()
      .setTypeId(CallNumberType.LC.getId()));
    var browseResult = BrowseResult.of(2, browseItems).next("A2");

    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, false)).thenReturn(precedingQuery);
    when(searchRepository.search(request, precedingQuery)).thenReturn(precedingResponse);
    when(browseResultConverter.convert(precedingResponse, context, false)).thenReturn(browseResult);

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(browseResult);
    verifyNoInteractions(shelvingOrderProcessor);
  }

  @Test
  void browse_positive_backwardWithPrevValue() {
    var request = request("callNumber < B", false);
    var query = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(ANCHOR);
    var context = BrowseContext.builder().precedingQuery(query).precedingLimit(2).anchor(ANCHOR).build();

    when(shelvingOrderProcessor.getSearchTerm(any(), any())).thenReturn(context.getAnchor());
    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, false)).thenReturn(precedingQuery);
    when(searchRepository.search(request, precedingQuery)).thenReturn(precedingResponse);
    when(browseResultConverter.convert(precedingResponse, context, false)).thenReturn(
      BrowseResult.of(5, browseItems("A1", "A2", "A3", "A4", "A5")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(5, "A4", "A5", List.of(
      cnBrowseItem(instance("A4"), "A4"), cnBrowseItem(instance("A5"), "A5"))));
  }

  @Test
  void browse_positive_backwardZeroResults() {
    var request = request("callNumber < B", false);
    var query = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(ANCHOR);
    var context = BrowseContext.builder().precedingQuery(query).precedingLimit(5).anchor(ANCHOR).build();

    when(shelvingOrderProcessor.getSearchTerm(any(), any())).thenReturn(context.getAnchor());
    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, false)).thenReturn(precedingQuery);
    when(searchRepository.search(request, precedingQuery)).thenReturn(precedingResponse);
    when(browseResultConverter.convert(precedingResponse, context, false)).thenReturn(BrowseResult.empty());

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.empty());
  }

  @Test
  void browse_positive_backwardWithIrrelevantCallNumberTypes() {
    var request = request("typedCallNumber < B", false, "lc");
    var query = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(ANCHOR);
    var context = BrowseContext.builder().precedingQuery(query).precedingLimit(5).anchor(ANCHOR).build();
    var browseItems = browseItems("A1", "A2");
    browseItems.get(0).getInstance().getItems().get(0).getEffectiveCallNumberComponents()
      .setTypeId(CallNumberType.NLM.getId());
    browseItems.get(1).getInstance().getItems().get(0).getEffectiveCallNumberComponents()
      .setTypeId(CallNumberType.LC.getId());
    var browseResult = BrowseResult.of(2, browseItems);
    var expected = BrowseResult.of(2, singletonList(browseItems.get(1))).next("A2");

    when(shelvingOrderProcessor.getSearchTerm(any(), any())).thenReturn(context.getAnchor());
    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, false)).thenReturn(precedingQuery);
    when(searchRepository.search(request, precedingQuery)).thenReturn(precedingResponse);
    when(browseResultConverter.convert(precedingResponse, context, false)).thenReturn(browseResult);

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void browse_positive_emptySucceedingResults() {
    var request = request("callNumber >= B or callNumber < B", true, 2, 5);

    when(cqlSearchQueryConverter.convertToTermNode(anyString(), any()))
      .thenReturn(new CQLTermNode(null, null, "B"));
    when(shelvingOrderProcessor.getSearchTerm(any(), any())).thenReturn(ANCHOR);
    lenient().when(shelvingOrderProcessor.getSearchTerms(ANCHOR)).thenReturn(newArrayList("B"));

    var precedingResult = BrowseResult.of(2, browseItems("A", "A1"));
    var succeedingResult = BrowseResult.of(1, browseItems("D"));
    var forwardPrecedingResult = BrowseResult.of(2, browseItems("B", "C"));
    var context = contextAroundIncluding();

    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, false)).thenReturn(precedingQuery);
    when(browseQueryProvider.get(request, context, true)).thenReturn(succeedingQuery);
    var msearchResponse = msearchResponse(precedingResponse, succeedingResponse);
    when(searchRepository.msearch(request, List.of(precedingQuery, succeedingQuery))).thenReturn(msearchResponse);
    when(browseResultConverter.convert(precedingResponse, context, false))
      .thenReturn(precedingResult);
    when(browseResultConverter.convert(succeedingResponse, context, false))
      .thenReturn(BrowseResult.empty());
    when(browseResultConverter.convert(precedingResponse, context, true))
      .thenReturn(forwardPrecedingResult);
    when(browseResultConverter.convert(succeedingResponse, context, true))
      .thenReturn(succeedingResult);

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(BrowseResult.of(3, List.of(
      cnBrowseItem(instance("A"), "A"),
      cnBrowseItem(instance("A1"), "A1"),
      cnBrowseItem(instance("B"), "B", true),
      cnBrowseItem(instance("C"), "C"),
      cnBrowseItem(instance("D"), "D")
    )));
  }

  private void prepareMockForBrowsingAround(BrowseRequest request, BrowseContext context,
                                            BrowseResult<CallNumberBrowseItem> precedingResult,
                                            BrowseResult<CallNumberBrowseItem> succeedingResult) {
    when(browseContextProvider.get(request)).thenReturn(context);
    when(shelvingOrderProcessor.getSearchTerm(any(), any())).thenReturn(context.getAnchor());
    when(browseQueryProvider.get(request, context, false)).thenReturn(precedingQuery);
    when(browseQueryProvider.get(request, context, true)).thenReturn(succeedingQuery);

    var msearchResponse = msearchResponse(precedingResponse, succeedingResponse);
    when(searchRepository.msearch(request, List.of(precedingQuery, succeedingQuery))).thenReturn(msearchResponse);
    when(browseResultConverter.convert(precedingResponse, context, false)).thenReturn(precedingResult);
    when(browseResultConverter.convert(succeedingResponse, context, false)).thenReturn(BrowseResult.empty());
    when(browseResultConverter.convert(precedingResponse, context, true)).thenReturn(BrowseResult.empty());
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
    return contextAroundIncluding(ANCHOR, ANCHOR);
  }

  private static BrowseContext contextAroundIncluding(String anchor, String searchTerm) {
    return BrowseContext.builder()
      .precedingQuery(rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(searchTerm))
      .succeedingQuery(rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte(searchTerm))
      .precedingLimit(2)
      .succeedingLimit(3)
      .anchor(anchor)
      .build();
  }

  private static BrowseRequest request(String query, boolean highlightMatch) {
    return request(query, CALL_NUMBER_BROWSING_FIELD, highlightMatch, null, 1, 2);
  }

  private static BrowseRequest request(String query, boolean highlightMatch, String callNumberType) {
    return request(query, CALL_NUMBER_BROWSING_FIELD, highlightMatch, callNumberType, 1, 2);
  }

  private static BrowseRequest request(String query, String browsingField, boolean highlightMatch,
                                       String callNumberType) {
    return request(query, browsingField, highlightMatch, callNumberType, 1, 2);
  }

  private static BrowseRequest request(String query, boolean highlightMatch, int precedingCount, int limit) {
    return request(query, CALL_NUMBER_BROWSING_FIELD, highlightMatch, null, precedingCount, limit);
  }

  private static BrowseRequest request(String query, String browsingField, boolean highlightMatch,
                                       String callNumberType, int precedingCount, int limit) {
    return BrowseRequest.builder().tenantId(TENANT_ID).resource(ResourceType.INSTANCE)
      .query(query)
      .highlightMatch(highlightMatch)
      .expandAll(false)
      .targetField(browsingField)
      .precedingRecordsCount(precedingCount)
      .limit(limit)
      .refinedCondition(callNumberType)
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
      .fullCallNumber(callNumber + " " + suffix)
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
