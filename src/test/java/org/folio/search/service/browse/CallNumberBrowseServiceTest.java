package org.folio.search.service.browse;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_BROWSING_FIELD;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.cnBrowseItem;
import static org.folio.search.utils.TestUtils.searchResult;
import static org.mockito.Mockito.when;

import java.util.List;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CallNumberBrowseServiceTest {

  private static final String ANCHOR = "B";
  @InjectMocks private CallNumberBrowseService callNumberBrowseService;

  @Mock private SearchRepository searchRepository;
  @Mock private BrowseContextProvider browseContextProvider;
  @Mock private CallNumberBrowseQueryProvider browseQueryProvider;
  @Mock private CallNumberBrowseResultConverter resultConverter;

  @Mock private SearchResponse precedingResponse;
  @Mock private SearchResponse succeedingResponse;
  @Mock private SearchSourceBuilder precedingQuery;
  @Mock private SearchSourceBuilder succeedingQuery;

  @BeforeEach
  void setUp() {
    callNumberBrowseService.setBrowseContextProvider(browseContextProvider);
  }

  @Test
  void browse_positive_around() {
    var request = request("callNumber >= B or callNumber < B", true);
    prepareMockForBrowsingAround(request,
      contextAroundIncluding(),
      searchResult(browseItems("A1", "A2", "A3", "A4")),
      searchResult(browseItems("C1", "C2", "C3", "C4", "C5", "C6", "C7")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(SearchResult.of(11, List.of(
      cnBrowseItem(instance("A3"), "A3"),
      cnBrowseItem(instance("A4"), "A4"),
      cnBrowseItem(0, "B", null, true),
      cnBrowseItem(instance("C1"), "C1"),
      cnBrowseItem(instance("C2"), "C2"))));
  }

  @Test
  void browse_positive_aroundWithFoundAnchor() {
    var request = request("callNumber >= B or callNumber < B", true);

    prepareMockForBrowsingAround(request,
      contextAroundIncluding(),
      searchResult(browseItem("A 11")),
      searchResult(browseItem("B"), browseItem("C 11")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(SearchResult.of(3, List.of(
      cnBrowseItem(instance("A 11"), "A 11"),
      cnBrowseItem(instance("B"), "B", "B", true),
      cnBrowseItem(instance("C 11"), "C 11"))));
  }

  @Test
  void browse_positive_around_emptySucceedingResults() {
    var request = request("callNumber >= B or callNumber < B", true);
    prepareMockForBrowsingAround(request, contextAroundIncluding(), searchResult(browseItem("A 11")), searchResult());

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(SearchResult.of(1, List.of(
      cnBrowseItem(instance("A 11"), "A 11"),
      cnBrowseItem(0, "B", null, true))));
  }

  @Test
  void browse_positive_around_noHighlightMatch() {
    var request = request("callNumber >= B or callNumber < B", false);
    var context = contextAroundIncluding();

    prepareMockForBrowsingAround(request, context, searchResult(browseItem("A 11")), searchResult(browseItem("C 11")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(SearchResult.of(2, List.of(
      cnBrowseItem(instance("A 11"), "A 11"),
      cnBrowseItem(instance("C 11"), "C 11"))));
  }

  @Test
  void browse_positive_forward() {
    var request = request("callNumber >= B", false);
    var query = rangeQuery(CALL_NUMBER_BROWSING_FIELD).gte(ANCHOR);
    var context = BrowseContext.builder().succeedingQuery(query).succeedingLimit(5).anchor(ANCHOR).build();

    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, true)).thenReturn(succeedingQuery);
    when(searchRepository.search(request, succeedingQuery)).thenReturn(succeedingResponse);
    when(resultConverter.convert(succeedingResponse, context, true)).thenReturn(searchResult(browseItems("C1", "C2")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(SearchResult.of(2, List.of(
      cnBrowseItem(instance("C1"), "C1"), cnBrowseItem(instance("C2"), "C2"))));
  }

  @Test
  void browse_positive_backward() {
    var request = request("callNumber < B", false);
    var query = rangeQuery(CALL_NUMBER_BROWSING_FIELD).lt(ANCHOR);
    var context = BrowseContext.builder().precedingQuery(query).precedingLimit(5).anchor(ANCHOR).build();

    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, false)).thenReturn(precedingQuery);
    when(searchRepository.search(request, precedingQuery)).thenReturn(precedingResponse);
    when(resultConverter.convert(precedingResponse, context, false)).thenReturn(searchResult(browseItems("A1", "A2")));

    var actual = callNumberBrowseService.browse(request);

    assertThat(actual).isEqualTo(SearchResult.of(2, List.of(
      cnBrowseItem(instance("A1"), "A1"), cnBrowseItem(instance("A2"), "A2"))));
  }

  private void prepareMockForBrowsingAround(BrowseRequest request, BrowseContext context,
    SearchResult<CallNumberBrowseItem> precedingResult, SearchResult<CallNumberBrowseItem> succeedingResult) {
    when(browseContextProvider.get(request)).thenReturn(context);
    when(browseQueryProvider.get(request, context, false)).thenReturn(precedingQuery);
    when(browseQueryProvider.get(request, context, true)).thenReturn(succeedingQuery);

    var msearchResponse = msearchResponse(precedingResponse, succeedingResponse);
    when(searchRepository.msearch(request, List.of(precedingQuery, succeedingQuery))).thenReturn(msearchResponse);
    when(resultConverter.convert(precedingResponse, context, false)).thenReturn(precedingResult);
    when(resultConverter.convert(succeedingResponse, context, true)).thenReturn(succeedingResult);
  }

  private static MultiSearchResponse msearchResponse(SearchResponse... responses) {
    var msearchItems = stream(responses)
      .map(response -> new MultiSearchResponse.Item(response, null))
      .toArray(MultiSearchResponse.Item[]::new);
    return new MultiSearchResponse(msearchItems, 0);
  }

  private static Instance instance(String... shelfKeys) {
    var items = stream(shelfKeys)
      .map(shelfKey -> new Item()
        .effectiveShelvingOrder(shelfKey)
        .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents().callNumber(shelfKey)))
      .collect(toList());
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

  private static CallNumberBrowseItem browseItem(String shelfKey) {
    return new CallNumberBrowseItem()
      .fullCallNumber(shelfKey)
      .shelfKey(shelfKey)
      .instance(instance(shelfKey))
      .totalRecords(1);
  }

  private static CallNumberBrowseItem[] browseItems(String... shelfKeys) {
    return stream(shelfKeys)
      .map(CallNumberBrowseServiceTest::browseItem)
      .toArray(CallNumberBrowseItem[]::new);
  }
}
